package app.morphe.extension.syncforreddit.http.imgur;

import androidx.annotation.NonNull;

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

        // The proxy takes /image/<signature>/<id>. Albums, which it served under /a/, need the
        // list of images the album held and cannot be answered from the id alone.
        List<String> segments = url.pathSegments();
        if (segments.size() < 3 || !IMAGE_PATH.equals(segments.get(0))) {
            Logger.printDebug(() -> "No local answer for the Imgur proxy path " + url.encodedPath());
            return gone(request);
        }

        String id = segments.get(segments.size() - 1);
        if (id.isEmpty()) {
            return gone(request);
        }

        try {
            JSONObject data = new JSONObject();
            data.put("link", "https://i.imgur.com/" + id + ".jpg");

            JSONObject body = new JSONObject();
            body.put("data", data);

            Logger.printDebug(() -> "Answering the Imgur proxy locally for " + id);
            return new Response.Builder()
                    .message("OK")
                    .code(HttpURLConnection.HTTP_OK)
                    .protocol(Protocol.HTTP_1_1)
                    .request(request)
                    .header("Content-Type", "application/json")
                    .body(ResponseBody.create(body.toString(), MediaType.get("application/json")))
                    .build();
        } catch (JSONException ex) {
            Logger.printException(() -> "Could not build the Imgur response for " + id, ex);
            return gone(request);
        }
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
