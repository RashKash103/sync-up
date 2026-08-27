package app.morphe.extension.syncforreddit.http.imgur;

import androidx.annotation.Nullable;

import org.json.JSONException;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.requests.Requester;

/**
 * Recovers the contents of an Imgur album from an archived copy of its page.
 *
 * <p>There is no way to derive an album's images from its id, and Sync reached them through a
 * proxy that no longer exists, so the page itself has to be read. Imgur used to render the
 * album server side and embed the list in the markup; pages captured since then are a script
 * shell with nothing in them, so older snapshots are tried first.
 */
final class ImgurAlbum {
    private static final String ALBUM_URL = "https://imgur.com/a/";

    /** How many archived copies to try before giving up on an album. */
    private static final int SNAPSHOTS_TO_TRY = 3;

    private static final int CACHE_SIZE = 32;

    /** The embedded list, which carries the right extension for each image. */
    private static final Pattern EMBEDDED_IMAGE = Pattern.compile(
            "\"hash\"\\s*:\\s*\"([A-Za-z0-9]{5,10})\".{0,200}?\"ext\"\\s*:\\s*\"(\\.[A-Za-z0-9]+)\"",
            Pattern.DOTALL);

    /** Fallback for layouts that only ever named the images in markup. */
    private static final Pattern LINKED_IMAGE = Pattern.compile(
            "i\\.imgur\\.com/([A-Za-z0-9]{5,10})(\\.[A-Za-z0-9]+)");

    private static final Map<String, List<String>> cache =
            Collections.synchronizedMap(new LinkedHashMap<String, List<String>>(
                    CACHE_SIZE + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest) {
                    return size() > CACHE_SIZE;
                }
            });

    private ImgurAlbum() {}

    /**
     * @return The image URLs in album order, empty when nothing could be recovered.
     */
    static List<String> imagesOf(String albumId) throws IOException, JSONException {
        List<String> cached = cache.get(albumId);
        if (cached != null) {
            return cached;
        }

        List<String> images = new ArrayList<>();
        for (String snapshot : WaybackMachine.findSnapshots(ALBUM_URL + albumId, SNAPSHOTS_TO_TRY)) {
            images = parse(fetch(snapshot));
            if (!images.isEmpty()) {
                Logger.printDebug(() -> "Recovered " + albumId + " from " + snapshot);
                break;
            }
        }

        cache.put(albumId, images);
        return images;
    }

    private static String fetch(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "text/html");
        connection.setUseCaches(false);

        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            return "";
        }
        return Requester.parseStringAndDisconnect(connection);
    }

    private static List<String> parse(String html) {
        List<String> images = collect(EMBEDDED_IMAGE, html);
        return images.isEmpty() ? collect(LINKED_IMAGE, html) : images;
    }

    /**
     * Order matters, and an album page names its cover twice, so duplicates are dropped while
     * keeping the first position of each.
     */
    private static List<String> collect(Pattern pattern, String html) {
        List<String> images = new ArrayList<>();
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            String image = "https://i.imgur.com/" + matcher.group(1) + matcher.group(2);
            if (!images.contains(image)) {
                images.add(image);
            }
        }
        return images;
    }

    @Nullable
    static String albumIdFrom(List<String> pathSegments) {
        if (pathSegments.size() < 2) {
            return null;
        }
        String id = pathSegments.get(pathSegments.size() - 1);
        return id.isEmpty() ? null : id;
    }
}
