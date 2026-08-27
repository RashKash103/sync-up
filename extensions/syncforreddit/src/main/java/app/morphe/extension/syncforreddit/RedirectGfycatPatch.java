package app.morphe.extension.syncforreddit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.fixes.redgifs.RedgifsTokenManager;
import app.morphe.extension.shared.requests.PatchedditInterceptor;
import app.morphe.extension.shared.requests.Requester;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Gfycat shut down and its domains no longer resolve, so every Gfycat request Sync makes
 * fails outright. Much of the content moved to RedGifs under the same slug before the
 * shutdown, so answer Sync's Gfycat API requests from RedGifs instead.
 *
 * @noinspection unused
 */
public class RedirectGfycatPatch extends PatchedditInterceptor {
    private static final String GFYCAT_API_HOST = "api.gfycat.com";
    private static final String GFYCAT_API_PATH = "/v1/gfycats/";
    private static final String REDGIFS_GIF_ENDPOINT = RedgifsTokenManager.REDGIFS_API_HOST + "/v2/gifs/";

    private static final RedirectGfycatPatch INSTANCE = new RedirectGfycatPatch();

    private static OkHttpClient instrumentedSource;
    private static OkHttpClient instrumentedClient;

    private RedirectGfycatPatch() {}

    @Override
    public boolean isPatchIncluded() {
        // The interceptor is only ever installed by this patch, so reaching here means it applies.
        return true;
    }

    /**
     * Sync's user agent. RedGifs ties a token to the user agent it was issued for and rejects
     * later requests that use a different one, so the same value must be used throughout.
     */
    public static String getUserAgent() {
        // To be filled in by patch
        return "";
    }

    /**
     * Derives a client carrying this interceptor. Called with the client Sync's bundled Volley
     * uses for every request, so the result is cached rather than rebuilt per request.
     */
    public static synchronized OkHttpClient install(OkHttpClient client) {
        if (instrumentedClient == null || instrumentedSource != client) {
            instrumentedSource = client;
            instrumentedClient = client.newBuilder().addInterceptor(INSTANCE).build();
        }
        return instrumentedClient;
    }

    @NonNull
    @Override
    protected Response doIntercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        HttpUrl url = request.url();

        if (!url.host().equals(GFYCAT_API_HOST) || !url.encodedPath().startsWith(GFYCAT_API_PATH)) {
            return chain.proceed(request);
        }

        // Gfycat ids are CamelCase, but RedGifs only resolves the lowercase form.
        String id = url.encodedPath().substring(GFYCAT_API_PATH.length()).toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            return notFound(request);
        }

        try {
            String body = lookUpOnRedgifs(id);
            if (body == null) {
                Logger.printDebug(() -> "No RedGifs mirror for Gfycat id " + id);
                return notFound(request);
            }

            Logger.printDebug(() -> "Serving Gfycat id " + id + " from RedGifs");
            return new Response.Builder()
                    .message("OK")
                    .code(HttpURLConnection.HTTP_OK)
                    .protocol(Protocol.HTTP_1_1)
                    .request(request)
                    .header("Content-Type", "application/json")
                    .body(ResponseBody.create(body, MediaType.get("application/json")))
                    .build();
        } catch (JSONException ex) {
            Logger.printException(() -> "Could not parse RedGifs response for Gfycat id " + id, ex);
            return notFound(request);
        }
    }

    /**
     * @return A Gfycat shaped response body, or null if RedGifs has no such gif.
     */
    @Nullable
    private static String lookUpOnRedgifs(String id) throws IOException, JSONException {
        String userAgent = getUserAgent();
        RedgifsTokenManager.RedgifsToken token = RedgifsTokenManager.refreshToken(userAgent);

        HttpURLConnection connection = (HttpURLConnection) new URL(REDGIFS_GIF_ENDPOINT + id).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", userAgent);
        connection.setRequestProperty("Authorization", "Bearer " + token.getAccessToken());
        connection.setRequestProperty("Accept", "application/json");
        connection.setUseCaches(false);

        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            return null;
        }

        JSONObject urls = Requester.parseJSONObjectAndDisconnect(connection)
                .getJSONObject("gif")
                .getJSONObject("urls");

        String highQuality = optionalString(urls, "hd");
        String lowQuality = optionalString(urls, "sd");
        if (highQuality == null) highQuality = lowQuality;
        if (lowQuality == null) lowQuality = highQuality;
        if (highQuality == null) {
            return null;
        }

        // Sync always reads mp4Url, and prefers mobileUrl when it is present.
        JSONObject gfyItem = new JSONObject();
        gfyItem.put("mp4Url", highQuality);
        gfyItem.put("mobileUrl", lowQuality);

        JSONObject response = new JSONObject();
        response.put("gfyItem", gfyItem);
        return response.toString();
    }

    @Nullable
    private static String optionalString(JSONObject object, String key) throws JSONException {
        return object.isNull(key) ? null : object.getString(key);
    }

    /**
     * Sync shows its own "this video does not exist" dialog for a 404, which is a better
     * outcome than a connection error.
     */
    private static Response notFound(Request request) {
        return new Response.Builder()
                .message("Not Found")
                .code(HttpURLConnection.HTTP_NOT_FOUND)
                .protocol(Protocol.HTTP_1_1)
                .request(request)
                .header("Content-Type", "application/json")
                .body(ResponseBody.create("{}", MediaType.get("application/json")))
                .build();
    }
}
