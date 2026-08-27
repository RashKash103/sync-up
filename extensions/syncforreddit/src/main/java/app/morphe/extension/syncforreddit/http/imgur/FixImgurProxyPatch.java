package app.morphe.extension.syncforreddit.http.imgur;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.requests.PatchedditInterceptor;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Sync resolves Imgur links through proxies of its own that no longer resolve, so those links
 * fail outright.
 *
 * <p>There are two. images.syncforreddit.com answered with Imgur's API shape and is what Sync
 * asks to resolve a link, and ap.syncforreddit.com served images themselves and is what a feed
 * tile loads through. The second is why an album post showed a blank thumbnail even once the
 * album itself opened: the tile never asked Imgur for anything.
 *
 * <p>The proxy answered with Imgur's own API shape, and the only field Sync needs from it is
 * the link. Since the id is right there in the request path, the response can be answered
 * locally with the ordinary Imgur URL for that id. Sync then loads that URL directly, and if
 * the image has since been removed the undelete patch recovers it from the Wayback Machine.
 *
 * @noinspection unused
 */
public class FixImgurProxyPatch extends PatchedditInterceptor {
    private static final String PROXY_HOST = "images.syncforreddit.com";
    private static final String IMAGE_PROXY_HOST = "ap.syncforreddit.com";
    private static final String IMAGE_PROXY_PATH = "/image";
    private static final String ALBUM_TOKEN = "imgur-album-";
    private static final String IMAGE_PATH = "image";
    private static final String ALBUM_PATH = "a";

    @Override
    public boolean isPatchIncluded() {
        // Overridden by patch.
        return false;
    }

    @NonNull
    @Override
    protected Response doIntercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        HttpUrl url = request.url();

        if (url.host().equals(IMAGE_PROXY_HOST) && url.encodedPath().equals(IMAGE_PROXY_PATH)) {
            return image(chain, request, url);
        }

        if (!url.host().equals(PROXY_HOST)) {
            return chain.proceed(request);
        }

        // The proxy took /image/<signature>/<id> for one image and /a/<signature>/<id> for an
        // album.
        List<String> segments = url.pathSegments();
        if (segments.size() < 3) {
            Logger.printDebug(() -> "No local answer for the Imgur proxy path " + url.encodedPath());
            return gone(request);
        }

        String kind = segments.get(0);
        String id = segments.get(segments.size() - 1);
        if (id.isEmpty()) {
            return gone(request);
        }

        try {
            if (IMAGE_PATH.equals(kind)) {
                return respond(request, singleImage(id));
            }
            if (ALBUM_PATH.equals(kind)) {
                List<JSONObject> images = ImgurAlbum.imagesOf(id);
                if (images.isEmpty()) {
                    Logger.printInfo(() -> "Could not recover the contents of album " + id);
                    return gone(request);
                }
                return respond(request, album(images));
            }

            Logger.printDebug(() -> "No local answer for the Imgur proxy path " + url.encodedPath());
            return gone(request);
        } catch (JSONException ex) {
            Logger.printException(() -> "Could not build the Imgur response for " + id, ex);
            return gone(request);
        }
    }

    /**
     * The image proxy took the image to serve as a url parameter, either an ordinary address or
     * a token naming something for it to resolve first. An address can simply be fetched
     * directly now that nothing is proxying it, and an album token is resolved to the album's
     * first image, which is the cover Sync means to show.
     */
    private Response image(Chain chain, Request request, HttpUrl url) throws IOException {
        String target = url.queryParameter("url");
        if (target == null || target.isEmpty()) {
            Logger.printDebug(() -> "No local answer for the image proxy request " + url);
            return gone(request);
        }

        if (target.startsWith(ALBUM_TOKEN)) {
            String id = target.substring(ALBUM_TOKEN.length());
            try {
                return albumThumbnail(chain, request, id);
            } catch (JSONException ex) {
                Logger.printException(() -> "Could not recover a thumbnail for album " + id, ex);
                return gone(request);
            }
        }

        HttpUrl direct = HttpUrl.parse(target);
        if (direct == null) {
            Logger.printDebug(() -> "The image proxy was asked for " + target
                    + ", which is not an address this can fetch");
            return gone(request);
        }
        return chain.proceed(request.newBuilder().url(direct).build());
    }

    /**
     * Reissues the request against the album's cover, falling back to an archived copy of it
     * when Imgur no longer holds it. The undelete interceptor cannot do that here, since it
     * sits ahead of this one and so never sees the address this substitutes.
     */
    private Response albumThumbnail(Chain chain, Request request, String id)
            throws IOException, JSONException {
        List<JSONObject> images = ImgurAlbum.imagesOf(id);
        if (images.isEmpty()) {
            Logger.printInfo(() -> "Could not recover a cover for album " + id);
            return gone(request);
        }

        String cover = images.get(0).optString("link", "");
        if (cover.isEmpty() || HttpUrl.parse(cover) == null) {
            return gone(request);
        }

        Response response = chain.proceed(request.newBuilder().url(cover).build());
        if (response.isSuccessful()) {
            Logger.printInfo(() -> "Serving the thumbnail for album " + id + " from " + cover);
            return response;
        }

        String snapshot = WaybackMachine.findSnapshot(cover);
        if (snapshot == null) {
            Logger.printInfo(() -> "No archived copy of the cover of album " + id);
            return response;
        }

        // Closed only once a replacement is certain, so the original is still returned intact
        // on the paths above.
        response.close();
        Logger.printInfo(() -> "Serving the thumbnail for album " + id + " from the archive");
        return chain.proceed(request.newBuilder().url(snapshot).build());
    }

    /**
     * Sync reads only the link, and rebuilds Imgur addresses from the id to choose a size, so
     * the ordinary address is what belongs here. If the image is gone, the undelete patch
     * recovers it when Sync goes on to load it.
     */
    private static JSONObject singleImage(String id) throws JSONException {
        JSONObject image = new JSONObject();
        image.put("link", "https://i.imgur.com/" + id + ".jpg");

        JSONObject body = new JSONObject();
        body.put("data", image);
        return body;
    }

    private static JSONObject album(List<JSONObject> images) throws JSONException {
        JSONArray entries = new JSONArray();
        for (JSONObject image : images) {
            entries.put(image);
        }

        JSONObject data = new JSONObject();
        data.put("images", entries);

        JSONObject body = new JSONObject();
        body.put("data", data);
        return body;
    }

    private static Response respond(Request request, JSONObject body) {
        return new Response.Builder()
                .message("OK")
                .code(HttpURLConnection.HTTP_OK)
                .protocol(Protocol.HTTP_1_1)
                .request(request)
                .header("Content-Type", "application/json")
                .body(ResponseBody.create(body.toString(), MediaType.get("application/json")))
                .build();
    }

    private static Response gone(Request request) {
        return new Response.Builder()
                .message("Gone")
                .code(HttpURLConnection.HTTP_GONE)
                .protocol(Protocol.HTTP_1_1)
                .request(request)
                .header("Content-Type", "application/json")
                .body(ResponseBody.create("{}", MediaType.get("application/json")))
                .build();
    }
}
