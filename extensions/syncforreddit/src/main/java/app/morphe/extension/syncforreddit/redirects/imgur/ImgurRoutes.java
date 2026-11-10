package app.morphe.extension.syncforreddit.redirects.imgur;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which Imgur addresses can be reached another way, and what that way looks like.
 *
 * <p>Nothing here touches the network, the app or Android: it is the rules themselves, so that
 * they can be read and tested on their own. {@code test/test_request_parsing.py} runs them.
 *
 * <p>The three routes do not cover the same ground, and pretending otherwise is how a picture
 * comes out broken:
 *
 * <ul>
 *   <li><b>DuckDuckGo's image proxy</b> fetches one picture, and only a picture. Asked for an
 *       album page, a video, or an address with no scheme, it answers 400 with a little SVG
 *       apology — which, handed to an image decoder, draws as a broken image rather than as
 *       nothing. So it is offered only where the address is a still picture.
 *   <li><b>Rimgo</b> is a whole front end for Imgur, so it serves pictures, videos and album
 *       pages alike, each on a path of its own.
 *   <li><b>A proxy</b> carries whatever is asked of it, Imgur's own API included, since it is
 *       the request itself that travels rather than the address.
 * </ul>
 */
public final class ImgurRoutes {
    /** What kind of thing an Imgur address asks for, which is what settles the route. */
    public enum Kind {
        /** A still picture: every route can serve one. */
        PICTURE,
        /** A video: Rimgo or a proxy, never DuckDuckGo. */
        VIDEO,
        /** A page — an album, a gallery, a post: Rimgo or a proxy. */
        PAGE,
        /** Imgur's own API: a proxy only. */
        API,
        /** Nothing we have a route for. */
        NONE,
    }

    private static final String IMGUR = "imgur.com";

    /** Imgur's own shortener, which serves the same content under another name. */
    private static final String IMGUR_SHORT = "imgur.io";

    /**
     * Stack Overflow's images, which merely happen to live on a name ending in Imgur's. They are
     * not what is being refused and Rimgo serves them on a path of its own, so they are left
     * alone rather than quietly sent somewhere else.
     */
    private static final String STACK = "i.stack.imgur.com";

    private static final String API = "api.imgur.com";

    /** DuckDuckGo's image proxy, which is what the browser extensions for this all use. */
    private static final String DUCKDUCKGO =
            "https://external-content.duckduckgo.com/iu/?u=";

    private static final String[] PICTURE_ENDINGS = {
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".apng", ".tiff", ".heic", ".avif",
    };

    private static final String[] VIDEO_ENDINGS = {".mp4", ".webm", ".mkv", ".mov"};

    /**
     * Paths that name a page rather than one piece of media, each of which Rimgo serves under
     * the same shape. {@code .gifv} is a page too: Imgur answers it with HTML holding a player.
     */
    private static final String[] PAGE_FIRST_SEGMENTS = {"a", "gallery", "t", "r", "user"};

    /**
     * What an album page links its media as. Rimgo writes each one as {@code src="/<id>.<ext>"},
     * which is its own media path, so the ids can be read straight out of the page.
     *
     * <p>Anchored on the quote and the leading slash so that nothing else on the page is taken
     * for media, and the static files it serves for itself are dropped by name below.
     */
    private static final Pattern RIMGO_MEDIA = Pattern.compile(
            "src=\"/([A-Za-z0-9]{3,12}\\.(?:jpg|jpeg|png|gif|webp|mp4|webm))\"");

    private ImgurRoutes() {}

    /**
     * @return Whether this is a host whose content Imgur refuses by region, and so one there is
     *         any point sending elsewhere.
     */
    public static boolean isImgur(String host) {
        if (host == null) {
            return false;
        }
        String named = host.toLowerCase(Locale.ROOT);
        if (named.equals(STACK)) {
            return false;
        }
        return named.equals(IMGUR) || named.endsWith("." + IMGUR)
                || named.equals(IMGUR_SHORT) || named.endsWith("." + IMGUR_SHORT);
    }

    /** @return What the address asks for, which decides which routes can answer it. */
    public static Kind kindOf(String host, String path) {
        if (!isImgur(host)) {
            return Kind.NONE;
        }
        if (host.toLowerCase(Locale.ROOT).equals(API)) {
            return Kind.API;
        }

        String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        for (String ending : VIDEO_ENDINGS) {
            if (lower.endsWith(ending)) {
                return Kind.VIDEO;
            }
        }
        for (String ending : PICTURE_ENDINGS) {
            if (lower.endsWith(ending)) {
                return Kind.PICTURE;
            }
        }

        String first = firstSegment(lower);
        for (String segment : PAGE_FIRST_SEGMENTS) {
            if (segment.equals(first)) {
                return Kind.PAGE;
            }
        }
        if (lower.endsWith(".gifv")) {
            return Kind.PAGE;
        }

        // What is left is a bare id. On the picture host that is a picture whose address simply
        // carries no extension; anywhere else it is the page for a post.
        if (!first.isEmpty()) {
            return host.toLowerCase(Locale.ROOT).startsWith("i.") ? Kind.PICTURE : Kind.PAGE;
        }
        return Kind.NONE;
    }

    /**
     * @return The address to ask DuckDuckGo's proxy for instead, or null where it would not be
     *         able to answer. The proxy reads what it is to fetch from the {@code u} parameter
     *         and insists on a whole address with a scheme; it also wants something that looks
     *         like a picture, which is why a bare id is given an ending here. Imgur serves a
     *         picture whatever ending is asked for, so saying {@code .png} of a JPEG is not a
     *         problem — the browser extensions this follows do exactly that.
     */
    public static String duckDuckGo(String url, Kind kind) {
        if (kind != Kind.PICTURE || url == null || url.isEmpty()) {
            return null;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return null;
        }

        // The ending is looked for on the path alone. An address carrying a query ends in
        // whatever the last parameter is, so asking whether the whole thing ends in .png says
        // nothing about it — and the ending, where one is added, belongs on the path rather
        // than on the end of the query.
        int query = indexOfTheQuery(url);
        String path = query < 0 ? url : url.substring(0, query);
        String rest = query < 0 ? "" : url.substring(query);

        String asked = hasAPictureEnding(path) ? url : path + ".png" + rest;
        return DUCKDUCKGO + encode(asked);
    }

    /**
     * @param instance The instance's address, with no trailing slash.
     * @return The same thing on that instance, or null where there is no path for it. Rimgo
     *         serves media at {@code /<name>.<extension>} and keeps Imgur's own shape for the
     *         pages, so most of this is the path as it stands.
     */
    public static String rimgo(String instance, String host, String path, Kind kind) {
        if (instance == null || instance.isEmpty() || kind == Kind.NONE || kind == Kind.API) {
            return null;
        }
        String base = instance.endsWith("/")
                ? instance.substring(0, instance.length() - 1) : instance;
        String onIt = path == null || path.isEmpty() ? "/" : path;
        if (!onIt.startsWith("/")) {
            onIt = "/" + onIt;
        }

        if (kind == Kind.PICTURE || kind == Kind.VIDEO) {
            // Media is named by the last segment alone, whatever it was reached through.
            String name = onIt.substring(onIt.lastIndexOf('/') + 1);
            if (name.isEmpty()) {
                return null;
            }
            if (!hasAPictureEnding(name) && !hasAVideoEnding(name)) {
                name = name + ".png";
            }
            return base + "/" + name;
        }

        return base + onIt;
    }

    /**
     * @return The id of the album or gallery this address is for, or null where it is not one.
     *         What holds an album's contents is the page, and that is what gets read.
     */
    public static String albumIdIn(String host, String path) {
        if (!isImgur(host) || path == null) {
            return null;
        }
        String[] segments = path.split("/");
        for (int at = 0; at < segments.length - 1; at++) {
            if ("a".equals(segments[at]) || "gallery".equals(segments[at])) {
                String id = segments[at + 1];
                return id.isEmpty() ? null : stripEnding(id);
            }
        }
        return null;
    }

    /**
     * Reads the media out of an album page Rimgo served.
     *
     * @return Each item's name with its extension, in the order the page lists them, with
     *         nothing repeated. Empty where the page holds none, which is what a page that is
     *         an error rather than an album looks like.
     */
    public static List<String> mediaInRimgoAlbum(String html) {
        List<String> found = new ArrayList<>();
        if (html == null || html.isEmpty()) {
            return found;
        }
        Matcher matching = RIMGO_MEDIA.matcher(html);
        while (matching.find()) {
            String name = matching.group(1);
            if (!found.contains(name)) {
                found.add(name);
            }
        }
        return found;
    }

    /** @return The ordinary Imgur address for a piece of media named in an album. */
    public static String imgurLinkFor(String name) {
        return "https://i.imgur.com/" + name;
    }

    // region Small things, kept here so the rules above read as rules.

    /** @return Where the path stops and a query or a fragment begins, or -1 where neither does. */
    private static int indexOfTheQuery(String url) {
        int question = url.indexOf('?');
        int hash = url.indexOf('#');
        if (question < 0) {
            return hash;
        }
        if (hash < 0) {
            return question;
        }
        return Math.min(question, hash);
    }

    private static String firstSegment(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        String without = path.startsWith("/") ? path.substring(1) : path;
        int slash = without.indexOf('/');
        return slash < 0 ? without : without.substring(0, slash);
    }

    private static boolean hasAPictureEnding(String what) {
        String lower = what.toLowerCase(Locale.ROOT);
        for (String ending : PICTURE_ENDINGS) {
            if (lower.endsWith(ending)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAVideoEnding(String what) {
        String lower = what.toLowerCase(Locale.ROOT);
        for (String ending : VIDEO_ENDINGS) {
            if (lower.endsWith(ending)) {
                return true;
            }
        }
        return false;
    }

    private static String stripEnding(String id) {
        int dot = id.indexOf('.');
        return dot < 0 ? id : id.substring(0, dot);
    }

    /**
     * Written out by hand rather than with {@code URLEncoder}, which turns a space into a plus
     * and is built for form fields rather than for a parameter holding an address. Everything
     * that is safe in a query is left as it is, which keeps the address readable in a log.
     */
    private static String encode(String what) {
        StringBuilder written = new StringBuilder(what.length() + 16);
        byte[] bytes = what.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (byte each : bytes) {
            int value = each & 0xFF;
            boolean plain = (value >= 'a' && value <= 'z')
                    || (value >= 'A' && value <= 'Z')
                    || (value >= '0' && value <= '9')
                    || value == '-' || value == '_' || value == '.' || value == '~'
                    || value == ':' || value == '/';
            if (plain) {
                written.append((char) value);
            } else {
                written.append('%');
                written.append(Character.toUpperCase(Character.forDigit(value >> 4, 16)));
                written.append(Character.toUpperCase(Character.forDigit(value & 0xF, 16)));
            }
        }
        return written.toString();
    }

    // endregion
}
