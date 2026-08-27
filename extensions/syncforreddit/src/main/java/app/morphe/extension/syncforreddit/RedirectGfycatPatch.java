package app.morphe.extension.syncforreddit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
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
 * shutdown, so answer Sync's Gfycat requests from RedGifs instead.
 *
 * <p>Sync asks for a Gfycat link twice. First it calls the API, and if that fails it falls
 * back to scraping the original link for an mp4 URL. Both are answered here: the API request
 * with the JSON Sync expects, the scrape with a body holding the media URLs. RedGifs kept
 * Gfycat's URL scheme, so the scraper's own parsing works on it unchanged.
 *
 * @noinspection unused
 */
public class RedirectGfycatPatch extends PatchedditInterceptor {
    private static final String GFYCAT_HOST = "gfycat.com";
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
        String host = url.host();

        if (!host.equals(GFYCAT_HOST) && !host.endsWith("." + GFYCAT_HOST)) {
            return chain.proceed(request);
        }

        boolean isApiRequest = host.equals(GFYCAT_API_HOST)
                && url.encodedPath().startsWith(GFYCAT_API_PATH);
        boolean isPageRequest = host.equals(GFYCAT_HOST) || host.equals("www." + GFYCAT_HOST);

        if (!isApiRequest && !isPageRequest) {
            // Gfycat's media subdomains are gone as well. Answering them without a lookup keeps
            // a feed full of dead thumbnails from spending a RedGifs request on every one.
            return gone(request);
        }

        // Assigned once: the lambdas below capture it, so it has to stay effectively final.
        String id = normalizeId(isApiRequest
                ? url.encodedPath().substring(GFYCAT_API_PATH.length())
                : lastPathSegment(url));

        if (id.isEmpty()) {
            return gone(request);
        }

        try {
            // Lets a genuine connection failure surface as one, rather than being reported as
            // content that no longer exists.
            MediaUrls media = fetchFromRedgifs(id);
            if (media == null) {
                Logger.printDebug(() -> "No RedGifs mirror for Gfycat id " + id);
                return gone(request);
            }

            Logger.printDebug(() -> "Serving Gfycat id " + id + " from RedGifs");
            return isApiRequest
                    ? respond(request, "application/json", apiBody(media))
                    : respond(request, "text/html", scrapeBody(media));
        } catch (JSONException ex) {
            Logger.printException(() -> "Could not parse RedGifs response for Gfycat id " + id, ex);
            return gone(request);
        }
    }

    private static String lastPathSegment(HttpUrl url) {
        List<String> segments = url.pathSegments();
        for (int i = segments.size() - 1; i >= 0; i--) {
            String segment = segments.get(i);
            if (!segment.isEmpty()) {
                return segment;
            }
        }
        return "";
    }

    /**
     * Strips the extension and the size suffixes Gfycat links carry, then lowercases, since
     * Gfycat ids are CamelCase and RedGifs only resolves the lowercase form.
     */
    private static String normalizeId(String id) {
        int cut = id.length();
        int dot = id.indexOf('.');
        if (dot >= 0) cut = dot;
        int dash = id.indexOf('-');
        if (dash >= 0 && dash < cut) cut = dash;
        return id.substring(0, cut).toLowerCase(Locale.ROOT);
    }

    private static final class MediaUrls {
        final String highQuality;
        final String lowQuality;

        MediaUrls(String highQuality, String lowQuality) {
            this.highQuality = highQuality;
            this.lowQuality = lowQuality;
        }
    }

    /**
     * @return The media URLs, or null if RedGifs has no such gif.
     */
    @Nullable
    private static MediaUrls fetchFromRedgifs(String id) throws IOException, JSONException {
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
        return new MediaUrls(highQuality, lowQuality);
    }

    /**
     * Sync always reads mp4Url, and prefers mobileUrl when it is present.
     */
    private static String apiBody(MediaUrls media) throws JSONException {
        JSONObject gfyItem = new JSONObject();
        gfyItem.put("mp4Url", media.highQuality);
        gfyItem.put("mobileUrl", media.lowQuality);

        JSONObject response = new JSONObject();
        response.put("gfyItem", gfyItem);
        return response.toString();
    }

    /**
     * Sync's scraper scans the body for URLs ending in .mp4 and treats a -mobile.mp4 one as the
     * lower quality option. RedGifs kept Gfycat's naming, so its URLs already fit that.
     */
    private static String scrapeBody(MediaUrls media) {
        return media.highQuality + "\n" + media.lowQuality;
    }

    @Nullable
    private static String optionalString(JSONObject object, String key) throws JSONException {
        return object.isNull(key) ? null : object.getString(key);
    }

    private static Response respond(Request request, String contentType, String body) {
        return new Response.Builder()
                .message("OK")
                .code(HttpURLConnection.HTTP_OK)
                .protocol(Protocol.HTTP_1_1)
                .request(request)
                .header("Content-Type", contentType)
                .body(ResponseBody.create(body, MediaType.get(contentType)))
                .build();
    }

    /**
     * Deliberately not a 404. Sync answers a 404 on a Gfycat link with a dialog whose only
     * action is to open the link in a browser, which can never work now that the domain is
     * gone, and it would appear for every unmirrored link. Any other status falls through to
     * Sync's ordinary error instead, which is the lesser annoyance.
     */
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
