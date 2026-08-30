package app.morphe.extension.syncforreddit.http.posts;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.requests.PatchedditInterceptor;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Makes a plain link written in a post something that can be followed.
 *
 * <p>Sync turns certain addresses written into a body into links itself, and the ones it knows
 * are Reddit's own, Imgur's and a couple besides. Anything else is left as the words it was
 * written as, so a post whose body is a link to somewhere else is a post with nothing to tap.
 *
 * <p>Written as a link rather than as an address, it is one wherever the body is drawn. Only a
 * post's own body is touched, and only an address that was not already written as a link.
 *
 * @noinspection unused
 */
public class TappableLinksPatch extends PatchedditInterceptor {
    /**
     * An address on its own. One already written as a link sits after a bracket, and one written
     * between angle brackets is spoken for as well, so neither is taken.
     */
    private static final Pattern PLAIN_LINK = Pattern.compile(
            "(?<![\\](<])\\b(https?://[^\\s<>\\]\\[)(]+)");

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

        if (!url.host().endsWith("reddit.com") || !url.encodedPath().contains("/comments/")) {
            return chain.proceed(request);
        }

        Response response = chain.proceed(request);
        if (!response.isSuccessful() || response.body() == null) {
            return response;
        }

        MediaType contentType = response.body().contentType();
        String original = response.body().string();
        response.close();

        try {
            String rewritten = linkify(original);
            Logger.printInfo(() -> rewritten != null
                    ? "Wrote the plain links in a post as links"
                    : "No plain link to write in " + url.encodedPath());
            return rebuilt(response, request, contentType,
                    rewritten != null ? rewritten : original);
        } catch (Throwable ex) {
            // A post that cannot be tapped is a far better outcome than one that will not draw.
            Logger.printException(() -> "Could not make the links in a post tappable", ex);
            return rebuilt(response, request, contentType, original);
        }
    }

    /**
     * @return The rewritten body, or null when the post had no plain address in it.
     */
    private static String linkify(String body) throws JSONException {
        JSONArray listings = new JSONArray(body);
        if (listings.length() < 1) {
            return null;
        }

        JSONObject listing = listings.optJSONObject(0);
        JSONObject data = listing == null ? null : listing.optJSONObject("data");
        JSONArray children = data == null ? null : data.optJSONArray("children");
        if (children == null || children.length() == 0) {
            return null;
        }

        JSONObject post = children.optJSONObject(0);
        JSONObject fields = post == null ? null : post.optJSONObject("data");
        if (fields == null) {
            return null;
        }

        String text = fields.optString("selftext", "");
        if (text.isEmpty()) {
            return null;
        }

        Matcher plain = PLAIN_LINK.matcher(text);
        StringBuffer written = new StringBuffer(text.length());
        boolean found = false;
        while (plain.find()) {
            found = true;
            String link = plain.group(1);
            plain.appendReplacement(written, Matcher.quoteReplacement("[" + link + "](" + link + ")"));
        }
        if (!found) {
            return null;
        }
        plain.appendTail(written);

        fields.put("selftext", written.toString());
        return listings.toString();
    }

    private static Response rebuilt(Response response, Request request, MediaType contentType,
                                    String body) {
        return response.newBuilder()
                .request(request)
                .body(ResponseBody.create(body, contentType))
                .build();
    }
}
