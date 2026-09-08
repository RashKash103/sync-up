package app.morphe.extension.syncforreddit.http.posts;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.requests.PatchedditInterceptor;
import app.morphe.extension.syncforreddit.http.ArchiveRequests;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

/**
 * The picture beside a link in a comment.
 *
 * <p>Sync shows a preview of a linked page, and asks its own proxy for the picture to put beside
 * it. That proxy answers 401 to everything now, so every preview but a video's — those are asked
 * of the site that hosts them — comes out as a broken image.
 *
 * <p>The picture the proxy would have found is the one the page names for exactly this purpose:
 * pages have carried a preview picture in their head for years, for anything that quotes a link.
 * So the page is read and asked, and the request goes to what it names instead.
 *
 * @noinspection unused
 */
public class WebsitePreviewImagePatch extends PatchedditInterceptor {
    /** Sync's own proxy, which is still there and refuses everything. */
    private static final String THEIR_PROXY = "ap.syncforreddit.com";

    private static final String THE_PICTURE_PATH = "/image";

    private static final String THE_PAGE = "url";

    /** What a page calls the picture it wants shown when it is quoted somewhere else. */
    private static final Pattern NAMES_A_PICTURE = Pattern.compile(
            "<meta[^>]+(?:property|name)\\s*=\\s*[\"'](?:og:image(?::url)?|twitter:image"
                    + "(?::src)?)[\"'][^>]+content\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    /** The same, where the page writes the two attributes the other way round. */
    private static final Pattern NAMES_IT_BACKWARDS = Pattern.compile(
            "<meta[^>]+content\\s*=\\s*[\"']([^\"']+)[\"'][^>]+(?:property|name)\\s*=\\s*[\"']"
                    + "(?:og:image(?::url)?|twitter:image(?::src)?)[\"']",
            Pattern.CASE_INSENSITIVE);

    /** Only the head is worth reading, and some pages are very large. */
    private static final int ENOUGH_OF_THE_PAGE = 200_000;

    /** What each page named, so a thread full of links to one place asks it once. */
    private static final int REMEMBERED = 128;

    private static final Map<String, String> named = Collections.synchronizedMap(
            new LinkedHashMap<String, String>(REMEMBERED, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > REMEMBERED;
                }
            });

    /** Kept apart from the pictures, so a page that names none is not read again either. */
    private static final String NAMED_NONE = "";

    @Override
    public boolean isPatchIncluded() {
        // Overridden by patch.
        return false;
    }

    @NonNull
    @Override
    protected Response doIntercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        HttpUrl asked = request.url();

        if (!isPatchIncluded()
                || !THEIR_PROXY.equals(asked.host())
                || !asked.encodedPath().startsWith(THE_PICTURE_PATH)) {
            return chain.proceed(request);
        }

        String page = asked.queryParameter(THE_PAGE);
        if (page == null || page.isEmpty()) {
            return chain.proceed(request);
        }

        String picture = pictureFor(page);
        if (picture == null) {
            // Nothing to put there. What the proxy says is what it has always said.
            return chain.proceed(request);
        }

        HttpUrl instead = HttpUrl.parse(picture);
        if (instead == null) {
            return chain.proceed(request);
        }

        Logger.printInfo(() -> "Showing " + page + " with the picture it names");
        return chain.proceed(request.newBuilder().url(instead).build());
    }

    /** @return The picture the page names for being quoted, or null where it names none. */
    private static String pictureFor(String page) {
        String already = named.get(page);
        if (already != null) {
            return already.isEmpty() ? null : already;
        }

        String found = null;
        try {
            String html = ArchiveRequests.get(page, "text/html");
            if (html != null) {
                String head = html.length() <= ENOUGH_OF_THE_PAGE
                        ? html : html.substring(0, ENOUGH_OF_THE_PAGE);

                Matcher names = NAMES_A_PICTURE.matcher(head);
                if (names.find()) {
                    found = names.group(1);
                } else {
                    Matcher backwards = NAMES_IT_BACKWARDS.matcher(head);
                    if (backwards.find()) {
                        found = backwards.group(1);
                    }
                }
            }
        } catch (Exception ex) {
            Logger.printInfo(() -> "Could not read " + page + " for a picture: " + ex);
        }

        if (found != null) {
            found = found.trim();
            if (found.startsWith("//")) {
                found = "https:" + found;
            }
            if (!found.startsWith("http")) {
                found = null;
            }
        }

        named.put(page, found == null ? NAMED_NONE : found);
        return found;
    }
}
