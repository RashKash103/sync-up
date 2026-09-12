package app.morphe.extension.syncforreddit.ui.comments;

import java.util.Locale;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.syncforreddit.settings.SyncUpSettings;

/**
 * Whether a link in a comment is drawn as the picture it points at, rather than as a chip naming
 * where it goes.
 *
 * <p>Sync already draws some of them inline, but only where the address ends in one of five
 * extensions or belongs to one of a handful of named services. Anything else — a Reddit gallery
 * link, an Imgur page, a RedGifs or Giphy link, an address that names no extension at all —
 * falls through to the chip, which is what makes the behaviour look arbitrary from a thread
 * where one comment draws and the next does not.
 *
 * <p>This widens that test to everything Sync can actually draw, on a setting of its own.
 *
 * @noinspection unused
 */
public final class InlineCommentMediaPatch {
    private static final String INLINE_EVERYTHING = "sync_up_inline_comment_media";

    /** Hosts that serve a picture or a video whatever the address on them looks like. */
    private static final String[] MEDIA_HOSTS = {
            "i.redd.it", "preview.redd.it", "v.redd.it", "i.imgur.com", "imgur.com",
            "redgifs.com", "gfycat.com", "giphy.com", "tenor.com", "media.giphy.com",
            "i.redditmedia.com", "redditmedia.com", "imgflip.com", "i.imgflip.com",
            "streamable.com", "media.tumblr.com", "youtube.com", "youtu.be",
    };

    /** What an address ends with when it is media, whatever host it is on. */
    private static final String[] MEDIA_ENDINGS = {
            ".jpg", ".jpeg", ".png", ".gif", ".gifv", ".webp", ".bmp", ".apng",
            ".mp4", ".webm", ".mov", ".m4v",
    };

    private static boolean said;

    /** How many calls are worth writing down before the point is made. */


    private InlineCommentMediaPatch() {}



    /** Set while a picture has just been put in, so the address is not written out after it. */
    private static final ThreadLocal<Boolean> justDrew = new ThreadLocal<>();

    /**
     * Draws the picture where the link stood, rather than letting Sync make a card of it.
     *
     * <p>Sync has two ways of putting a picture into text. Its giphy handling puts the picture
     * in by itself. Everything else goes into a list of cards, and a card is drawn as a small
     * picture with the address beside it — which is the rectangle, and is not what was asked
     * for. This puts it in the first way.
     *
     * @param card What Sync was about to do, which is make a card of it.
     * @param into The text being built.
     * @param link The address of the picture.
     * @return Whether Sync should still make its card.
     */
    /** What text stands for a picture that is drawn rather than named. */
    private static final String THE_PICTURE_ITSELF = "\uFFFC";

    /**
     * @return The text to write where the link stood.
     *
     * <p>Sync is about to add the link to the cards it draws under the text — a small picture
     * with the address beside it. Where the picture itself was asked for, no card is added and
     * the text written is the one character a picture stands in, which the spans below fill.
     *
     * <p>Adding the card as well is what drew each picture twice.
     */
    public static String cardOrPicture(nc.b cards, nc.b.a card, String link) {
        if (!wanted(link)) {
            return cards.c(card);
        }
        justDrew.set(Boolean.TRUE);
        Logger.printDebug(() -> "inline comments: no card for " + link
                + "; the picture stands where the address did");
        // Nothing at all: the picture has already been drawn over the address itself, and a
        // card would be the same picture again, small, underneath it.
        return "";
    }

    /**
     * @return What to draw that text with: the picture by itself, which is what Sync's own
     *         giphy handling draws one with, rather than the card's label.
     */
    /**
     * How big each picture is, once it has been asked.
     *
     * <p>The span that draws a picture is told a width and a height when it is made and never
     * changes them, so a guess at the shape is a picture drawn wrong for as long as it is on
     * screen. Sync draws one inline only where the address itself carries both numbers and
     * falls back to a card otherwise, which is why a card is what most of them were.
     */
    private static final java.util.Map<String, int[]> shapes =
            java.util.Collections.synchronizedMap(new java.util.HashMap<String, int[]>());

    /** Pictures already being measured, so a list redrawing does not ask again. */
    private static final java.util.Set<String> measuring =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    private static final java.util.concurrent.ExecutorService MEASURING =
            java.util.concurrent.Executors.newFixedThreadPool(6);

    /** The pictures being asked about, so the comments can wait for the answers. */
    private static final java.util.Map<String, java.util.concurrent.Future<?>> pending =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Writes down how big a picture is, as Reddit itself said when it sent the comments.
     *
     * <p>This is where the sizes are meant to come from: Reddit sends them beside the text, so
     * they are known before anything is drawn, and the picture goes in the right shape the first
     * time. Sync draws one inline on exactly the same grounds.
     */
    public static void remember(String link, int wide, int tall) {
        if (link == null || link.isEmpty() || wide <= 0 || tall <= 0) {
            return;
        }
        String plain = link.replace("&amp;", "&");
        if (shapes.put(plain, new int[]{ wide, tall }) == null) {
            Logger.printDebug(() -> "inline comments: Reddit says " + plain
                    + " is " + wide + "x" + tall);
        }
        // Kept under both spellings: Reddit writes its addresses with the ampersands escaped
        // and asks for them unescaped.
        shapes.put(link, new int[]{ wide, tall });
    }

    /**
     * Starts measuring a picture Reddit said nothing about, as its comments arrive.
     *
     * <p>Reddit gives sizes for what it hosts itself and nothing for a giphy picture, so that
     * one has to be asked. Asked now rather than when it is drawn, since the comments arrive
     * well before any of them are on screen.
     */
    public static void measureSoon(String link) {
        if (link == null || link.isEmpty() || shapes.containsKey(link)) {
            return;
        }
        measure(link);
    }

    /**
     * @return The picture's size, or null where it is not known yet. Asking is done off to one
     *         side; until there is an answer the picture is left as Sync drew it, a card being
     *         better than a picture of the wrong shape.
     */
    private static int[] shapeOf(String link) {
        int[] known = shapes.get(link);
        if (known != null) {
            return known;
        }
        // What the address says, where it says anything. Sync reads these too.
        try {
            android.net.Uri asked = android.net.Uri.parse(link);
            String wide = asked.getQueryParameter("width");
            String tall = asked.getQueryParameter("height");
            if (wide != null && tall != null) {
                int[] shape = { Integer.parseInt(wide), Integer.parseInt(tall) };
                shapes.put(link, shape);
                return shape;
            }
        } catch (Exception ignored) {
            // Measured below instead.
        }
        measure(link);
        return null;
    }

    /** Reads the size out of the picture's own header, without decoding the picture. */
    private static void measure(String link) {
        if (!measuring.add(link)) {
            return;
        }
        pending.put(link, MEASURING.submit(() -> {
            java.net.HttpURLConnection asking = null;
            try {
                asking = (java.net.HttpURLConnection) new java.net.URL(link).openConnection();
                asking.setConnectTimeout(8000);
                asking.setReadTimeout(10000);
                asking.setInstanceFollowRedirects(true);
                android.graphics.BitmapFactory.Options only =
                        new android.graphics.BitmapFactory.Options();
                only.inJustDecodeBounds = true;
                try (java.io.InputStream reading = asking.getInputStream()) {
                    android.graphics.BitmapFactory.decodeStream(reading, null, only);
                }
                if (only.outWidth > 0 && only.outHeight > 0) {
                    shapes.put(link, new int[]{ only.outWidth, only.outHeight });
                    Logger.printDebug(() -> "inline comments: " + link + " is "
                            + only.outWidth + "x" + only.outHeight);
                } else {
                    Logger.printInfo(() -> "inline comments: could not measure " + link);
                }
            } catch (Throwable ex) {
                Logger.printInfo(() -> "inline comments: could not measure " + link + ": " + ex);
            } finally {
                if (asking != null) {
                    asking.disconnect();
                }
                measuring.remove(link);
                pending.remove(link);
            }
        }));
    }

    /**
     * Waits for the pictures that had to be measured, so the comments they are in are drawn
     * knowing how big they are.
     *
     * <p>Called where the comments arrive, off the screen's thread and before Sync has seen
     * them. Reddit sends its own sizes, so this is only ever the few it says nothing about;
     * waiting there costs the reply the time of one small request and saves every picture in
     * it standing as a card until it has been asked about. Bounded, since a picture drawn late
     * is better than comments that do not arrive.
     *
     * @param millis How long to wait altogether.
     */
    public static void awaitMeasures(long millis) {
        long until = android.os.SystemClock.uptimeMillis() + millis;
        for (String link : new java.util.ArrayList<>(pending.keySet())) {
            java.util.concurrent.Future<?> asking = pending.get(link);
            if (asking == null) {
                continue;
            }
            long left = until - android.os.SystemClock.uptimeMillis();
            if (left <= 0) {
                Logger.printDebug(() -> "inline comments: still measuring " + link
                        + " when the comments were wanted");
                break;
            }
            try {
                asking.get(left, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
                // Drawn as a card this once, and in shape the next time it is on screen.
            }
            pending.remove(link);
        }
    }

    /**
     * Remembers how wide the comment about to be drawn may be.
     *
     * <p>Sync works this out for every comment, taking the indent and the margins off the width
     * of the screen, and it is right: a reply gets less room than what it replies to. It is only
     * handed to the parse, though, and Reddit's own giphy pictures are drawn by a path that is
     * told the address and nothing else. That path was left guessing — the width of the last
     * comment that happened to hold a link, or nine tenths of the screen — so a giphy in a reply
     * was built wider than the room it had and cut off on the right.
     *
     * <p>Called where the width is settled, which is once per comment and before any of it is
     * parsed, so what it records is this comment's own.
     *
     * @param where What Sync is about to draw the text with.
     */
    public static void howWide(nc.a where) {
        if (!isPatchIncluded() || !inlineEverything() || where == null) {
            return;
        }
        try {
            final int room = where.e;
            if (room <= 0) {
                return;
            }
            lastWidth = room;
        } catch (Throwable ex) {
            Logger.printInfo(() -> "inline comments: could not tell how wide the text is: " + ex);
        }
    }


    public static Object[] spansFor(Object[] spans, String link, nc.a where) {
        if (!wanted(link)) {
            return spans;
        }
        try {
            int width = where == null ? 0 : where.e;
            if (width > 0) {
                // Remembered for Reddit's own giphy pictures, which are drawn by a path that is
                // told the address and nothing else.
                lastWidth = width;
            }
            if (width <= 0) {
                Logger.printInfo(() -> "inline comments: no width to draw " + link + " at");
                return spans;
            }
            int[] shape = shapeOf(link);
            if (shape == null) {
                // A picture of the wrong shape is worse than the card Sync would have drawn.
                Logger.printDebug(() -> "inline comments: the shape of " + link
                        + " is not known yet, leaving it as Sync drew it");
                return spans;
            }
            final int drawWide = width;
            final int drawTall = (int) (width * shape[1] / shape[0]);
            Logger.printDebug(() -> "inline comments: drawing " + link + " "
                    + drawWide + "x" + drawTall);
            // The span that draws the picture and nothing else, at the shape it actually is.
            // Its sibling draws a card: a small picture with the address beside it.
            return new Object[]{ new nb.c(link, drawWide, drawTall), new mb.d(link) };
        } catch (Throwable ex) {
            Logger.printInfo(() -> "inline comments: could not draw " + link + ": " + ex);
            return spans;
        }
    }

    private static boolean wanted(String link) {
        return isPatchIncluded() && link != null && !link.isEmpty()
                && inlineEverything() && isMedia(link);
    }

    /**
     * @param expanded Whether Sync writes the address out after a link.
     * @param handled  Whether the link was already drawn as something.
     * @return Whether to write the address out.
     */
    public static boolean stillShowTheLink(boolean expanded, boolean handled) {
        if (Boolean.TRUE.equals(justDrew.get())) {
            justDrew.remove();
            Logger.printDebug(() -> "inline comments: leaving the address off what was drawn");
            return false;
        }
        return expanded;
    }

    /** How wide the text was last drawn, for a path that is not told. */
    private static volatile int lastWidth;

    /**
     * The giphy picture being drawn.
     *
     * <p>Kept here rather than read again further down: Sync builds the address into a register
     * and then uses that same register for the character a picture stands in, so by the time
     * the spans are written the address is no longer there to read.
     */
    private static volatile String giphyLink;

    /** Called where Sync draws one of Reddit's own giphy pictures, which it has its own path for. */
    public static void giphy(String link) {
        giphyLink = link;
        Logger.printDebug(() -> "inline comments: a giphy picture, drawn from " + link);
    }

    /**
     * @return What to draw Reddit's own giphy pictures with.
     *
     * <p>They have a path of their own, which draws a card: a small picture with the address
     * beside it. Where the picture itself was asked for, it is drawn as the picture, at the
     * width the text was last drawn at — that path is told the address and nothing else.
     */
    public static Object[] giphySpans(Object[] spans) {
        String link = giphyLink;
        if (!isPatchIncluded() || !inlineEverything() || link == null || link.isEmpty()) {
            return spans;
        }
        int width = lastWidth;
        if (width <= 0) {
            try {
                width = (int) (app.morphe.extension.shared.Utils.getContext().getResources()
                        .getDisplayMetrics().widthPixels * 0.9f);
            } catch (Throwable ex) {
                Logger.printInfo(() -> "inline comments: no width for a giphy picture: " + ex);
                return spans;
            }
        }
        final int drawAt = width;
        try {
            int[] shape = shapeOf(link);
            if (shape == null) {
                Logger.printDebug(() -> "inline comments: the shape of " + link
                        + " is not known yet, leaving it as Sync drew it");
                return spans;
            }
            final int tall = (int) (drawAt * shape[1] / shape[0]);
            Logger.printDebug(() -> "inline comments: drawing the giphy picture " + link
                    + " " + drawAt + "x" + tall);
            return new Object[]{ new nb.c(link, drawAt, tall), new mb.d(link) };
        } catch (Throwable ex) {
            Logger.printInfo(() -> "inline comments: could not draw " + link + ": " + ex);
            return spans;
        }
    }

    /**
     * Sync asks something about where the link is before it asks anything about the link. What
     * that is, is not named in the app; it is answered here so that a capture says whether it
     * is what stops a picture being drawn where it sits.
     *
     * @param sync What Sync decided about where this link is.
     */
    public static boolean here(boolean sync) {
        return sync || inlineEverything();
    }

    public static boolean isPatchIncluded() {
        // Overridden by patch.
        return false;
    }

    /** Whether the setting is on, so Sync's own gate can be opened alongside it. */
    public static boolean inlineEverything() {
        if (!isPatchIncluded()) {
            return false;
        }
        boolean on = SyncUpSettings.flag(INLINE_EVERYTHING, false);
        if (!said) {
            said = true;
            SyncUpSettings.report("inline comments", INLINE_EVERYTHING);
            Logger.printDebug(() -> "inline comments: widening is " + (on ? "on" : "off"));
        }
        return on;
    }

    /**
     * Sync will not draw anything inline unless its own inline-previews setting is on, and that
     * setting is about its own narrow list. Opening the gate here lets the widened test below be
     * the one that decides.
     *
     * @param sync What Sync's own setting says.
     */
    public static boolean orInlineEverything(boolean sync) {
        return sync || inlineEverything();
    }

    /**
     * Whether this link is one to draw where it sits rather than to show a preview of the page
     * it is on.
     *
     * <p>Sync decides whether to preview a page before it decides anything about drawing a
     * picture, and a link it has already made a preview of is marked as dealt with and never
     * looked at again. A gif was therefore always a preview of the page it is on, whatever the
     * rest of this answered.
     */
    public static boolean claimsAsMedia(String link) {
        if (!isPatchIncluded() || link == null || link.isEmpty() || !inlineEverything()) {
            return false;
        }
        boolean media = isMedia(link);
        if (media) {
            Logger.printDebug(() -> "inline comments: claiming " + link
                    + " rather than letting it become a preview");
        }
        return media;
    }

    /**
     * @param sync     What Sync decided on its own.
     * @param link     The address in the comment.
     * @return Whether to draw the link as the media it points at.
     */
    public static boolean shouldInline(boolean sync, String link) {
        if (sync) {
            Logger.printDebug(() -> "inline comments: Sync already draws " + link);
            return true;
        }
        if (!inlineEverything()) {
            Logger.printDebug(() -> "inline comments: leaving " + link + " as a chip");
            return false;
        }
        if (link == null || link.isEmpty()) {
            return false;
        }

        boolean media = isMedia(link);
        Logger.printDebug(() -> "inline comments: " + link + " -> "
                + (media ? "drawing it inline" : "not media, left as a chip"));
        return media;
    }

    /** @return Whether the address points at something that can be drawn. */
    private static boolean isMedia(String link) {
        String lower = link.toLowerCase(Locale.ROOT);

        // Anything after the address itself is no help in telling what it points at.
        int query = lower.indexOf('?');
        String path = query < 0 ? lower : lower.substring(0, query);
        for (String ending : MEDIA_ENDINGS) {
            if (path.endsWith(ending)) {
                return true;
            }
        }

        for (String host : MEDIA_HOSTS) {
            // Matched against the whole address rather than a parsed host, since what arrives
            // here is sometimes written without a scheme.
            if (lower.contains("//" + host + "/") || lower.contains("." + host + "/")
                    || lower.contains("//" + host) && lower.endsWith(host)) {
                return true;
            }
        }
        return false;
    }
}
