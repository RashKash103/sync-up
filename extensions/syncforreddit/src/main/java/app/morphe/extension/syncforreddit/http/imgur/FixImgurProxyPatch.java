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
 * Sync resolves Imgur links through a proxy of its own at images.syncforreddit.com, which no
 * longer resolves, so those links fail outright.
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
                    Logger.printDebug(() -> "Could not recover the contents of album " + id);
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
