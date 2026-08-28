package app.morphe.extension.syncforreddit.http.profile;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.requests.PatchedditInterceptor;
import app.morphe.extension.syncforreddit.http.undelete.ArcticShift;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Fills in a profile Reddit will not list.
 *
 * <p>Reddit lets an account hide what it has written from its own profile, and answers a
 * request for that profile with a listing holding nothing at all, which Sync shows as a user
 * with no posts. Project Arctic Shift recorded the same posts and comments when they were
 * public and still serves them, so the empty listing is answered with those instead.
 *
 * <p>Only a profile that comes back empty is filled in. A profile Reddit does list is left
 * exactly as Reddit sent it, so nothing here decides what is current: it only stands in where
 * Reddit has stopped answering.
 *
 * @noinspection unused
 */
public class ArchivedProfilePatch extends PatchedditInterceptor {
    /**
     * One screenful and then some. Sync asks for a page at a time and follows "after" to get the
     * next, which the archive has no equivalent of, so what is filled in is what is served.
     */
    private static final int LIMIT = 100;

    @Override
    public boolean isPatchIncluded() {
        // Overridden by patch.
        return false;
    }

    @NonNull
    @Override
    protected Response doIntercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        Tab tab = tabOf(request.url());
        if (tab == null) {
            return chain.proceed(request);
        }

        Response response = chain.proceed(request);
        if (!response.isSuccessful() || response.body() == null) {
            return response;
        }

        // Reading the body consumes it, so the response has to be rebuilt either way.
        MediaType contentType = response.body().contentType();
        String original = response.body().string();
        response.close();

        try {
            if (!isEmptyListing(original)) {
                return rebuilt(response, request, contentType, original);
            }

            JSONArray archived = ArcticShift.searchByAuthor(tab.kind, tab.author, LIMIT);
            if (archived.length() == 0) {
                Logger.printInfo(() -> "The archive has no " + tab.kind + " by " + tab.author);
                return rebuilt(response, request, contentType, original);
            }

            if (tab.kind.equals("comments")) {
                describeParents(archived);
            }

            Logger.printInfo(() -> "Serving " + archived.length() + " archived " + tab.kind
                    + " by " + tab.author + ", whose profile Reddit lists as empty");
            return rebuilt(response, request, contentType, listing(archived));
        } catch (JSONException ex) {
            Logger.printException(() -> "Could not read the archived profile of " + tab.author, ex);
            return rebuilt(response, request, contentType, original);
        } catch (IOException ex) {
            // The archive being unreachable leaves the profile as Reddit gave it.
            Logger.printInfo(() -> "Could not reach the archive for " + tab.author + ": " + ex);
            return rebuilt(response, request, contentType, original);
        }
    }

    /**
     * A comment listing says which post each comment is on, and Reddit is asked for a title,
     * not only an id. The archive records the id alone, so the posts are looked up together and
     * what it knows of them written in. Every field is set either way: a listing that leaves one
     * out is one Sync will not read.
     */
    private static void describeParents(JSONArray comments) throws IOException, JSONException {
        Set<String> ids = new LinkedHashSet<>();
        for (int i = 0; i < comments.length(); i++) {
            JSONObject comment = comments.optJSONObject(i);
            if (comment == null) continue;
            String id = withoutPrefix(comment.optString("link_id", ""));
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }

        Map<String, JSONObject> posts = ArcticShift.getSubmissions(ids);

        for (int i = 0; i < comments.length(); i++) {
            JSONObject comment = comments.optJSONObject(i);
            if (comment == null) continue;

            JSONObject post = posts.get(withoutPrefix(comment.optString("link_id", "")));
            comment.put("link_title", post == null ? "" : post.optString("title", ""));
            comment.put("link_author", post == null ? "" : post.optString("author", ""));
            comment.put("link_url", post == null ? "" : post.optString("url", ""));
            comment.put("link_permalink", post == null ? "" : post.optString("permalink", ""));
        }
    }

    private static String withoutPrefix(String fullName) {
        int underscore = fullName.indexOf('_');
        return underscore < 0 ? fullName : fullName.substring(underscore + 1);
    }

    /**
     * Reddit answers a hidden profile with a listing carrying no children rather than saying so.
     */
    private static boolean isEmptyListing(String body) throws JSONException {
        JSONObject listing = new JSONObject(body);
        JSONObject data = listing.optJSONObject("data");
        if (data == null) {
            return false;
        }
        JSONArray children = data.optJSONArray("children");
        return children != null && children.length() == 0;
    }

    /**
     * Wraps what the archive holds in the shape Reddit would have sent. Each entry already
     * carries its full name, which is where its kind comes from, and "after" is left empty
     * because there is no page beyond the one served.
     */
    private static String listing(JSONArray archived) throws JSONException {
        JSONArray children = new JSONArray();
        for (int i = 0; i < archived.length(); i++) {
            JSONObject entry = archived.optJSONObject(i);
            if (entry == null) continue;

            String name = entry.optString("name", "");
            int underscore = name.indexOf('_');
            if (underscore <= 0) continue;

            JSONObject child = new JSONObject();
            child.put("kind", name.substring(0, underscore));
            child.put("data", entry);
            children.put(child);
        }

        JSONObject data = new JSONObject();
        data.put("children", children);
        data.put("after", JSONObject.NULL);
        data.put("before", JSONObject.NULL);
        data.put("dist", children.length());

        JSONObject listing = new JSONObject();
        listing.put("kind", "Listing");
        listing.put("data", data);
        return listing.toString();
    }

    private static Response rebuilt(Response response, Request request, MediaType contentType,
                                    String body) {
        return response.newBuilder()
                .request(request)
                .body(ResponseBody.create(body, contentType))
                .build();
    }

    /**
     * A profile listing, which is a user, then what of theirs is being asked for.
     */
    private static final class Tab {
        final String author;
        final String kind;

        Tab(String author, String kind) {
            this.author = author;
            this.kind = kind;
        }
    }

    @Nullable
    private static Tab tabOf(HttpUrl url) {
        if (!url.host().endsWith("reddit.com")) {
            return null;
        }

        List<String> segments = url.pathSegments();
        if (segments.size() < 3) {
            return null;
        }
        // Sync asks for /user/<name>/<what>, and for /u/<name>/<what> where the patch that
        // brings it onto the current endpoint has not been applied.
        String root = segments.get(0);
        if (!root.equals("user") && !root.equals("u")) {
            return null;
        }

        String author = segments.get(1);
        String what = segments.get(2).toLowerCase(Locale.ROOT);
        int suffix = what.indexOf('.');
        if (suffix >= 0) {
            what = what.substring(0, suffix);
        }

        if (author.isEmpty()) {
            return null;
        }
        // Only the two the archive keeps apart. An overview mixes them, and serving one of the
        // two in its place would look like the other had been lost.
        if (what.equals("submitted")) {
            return new Tab(author, "posts");
        }
        if (what.equals("comments")) {
            return new Tab(author, "comments");
        }
        return null;
    }
}
