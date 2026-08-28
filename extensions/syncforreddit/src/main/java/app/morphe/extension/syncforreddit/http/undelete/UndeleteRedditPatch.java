/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.syncforreddit.http.undelete;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.requests.PatchedditInterceptor;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Restores the text of removed posts and comments from Project Arctic Shift.
 *
 * <p>Sync requests a thread as {@code /r/<sub>/comments/<id>/...json}, so unlike Boost the
 * path does not begin with {@code /comments/}. Sync also reads raw markdown rather than the
 * rendered {@code _html} fields, and has no field for Boost's removal reason markers, so the
 * marker is prefixed to the restored text instead.
 */
public class UndeleteRedditPatch extends PatchedditInterceptor {
    private static final String REMOVED = "[removed]";
    private static final String DELETED = "[deleted]";

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

        // Reading the body consumes it, so the response has to be rebuilt either way.
        String original = response.body().string();
        MediaType contentType = response.body().contentType();

        String restored;
        try {
            restored = restore(original, submissionIdFrom(url));
        } catch (JSONException ex) {
            Logger.printException(() -> "Could not restore removed content", ex);
            restored = null;
        } catch (IOException ex) {
            // Arctic Shift being unreachable must not take the thread down with it.
            Logger.printException(() -> "Arctic Shift request failed", ex);
            restored = null;
        }

        return response.newBuilder()
                .body(ResponseBody.create(restored != null ? restored : original, contentType))
                .build();
    }

    /**
     * Sync asks for {@code /r/<sub>/comments/<id>/_/...}, so the id follows the "comments"
     * segment.
     */
    @Nullable
    private static String submissionIdFrom(HttpUrl url) {
        java.util.List<String> segments = url.pathSegments();
        for (int i = 0; i < segments.size() - 1; i++) {
            if (segments.get(i).equals("comments")) {
                // Sync asks for a thread as <id>.json rather than putting the suffix on a
                // later segment, and the archive knows nothing about an id carrying it.
                String id = segments.get(i + 1);
                int suffix = id.indexOf('.');
                if (suffix >= 0) {
                    id = id.substring(0, suffix);
                }
                return id.isEmpty() ? null : id;
            }
        }
        return null;
    }

    /**
     * @return The rewritten body, or null when nothing needed restoring.
     */
    @Nullable
    private static String restore(String body, @Nullable String submissionId)
            throws JSONException, IOException {
        if (submissionId == null) {
            return null;
        }

        JSONArray listings = new JSONArray(body);
        if (listings.length() < 2) {
            return null;
        }

        JSONObject submission = firstChildData(listings.optJSONObject(0));
        JSONArray comments = childrenOf(listings.optJSONObject(1));

        boolean submissionRemoved = submission != null
                && (isRemoved(submission, "selftext") || isDeletedAuthor(submission));
        Set<String> removedComments = new HashSet<>();
        if (comments != null) {
            collectRemoved(comments, removedComments);
        }

        if (!submissionRemoved && removedComments.isEmpty()) {
            return null;
        }

        boolean changed = false;

        if (submissionRemoved) {
            JSONObject archived = ArcticShift.getSubmission(submissionId);
            if (archived != null) {
                if (isRemoved(submission, "selftext")) {
                    changed |= merge(submission, archived, "selftext");
                }
                changed |= restoreAuthor(submission, archived);
            }
        }

        if (!removedComments.isEmpty()) {
            int wanted = removedComments.size();
            Map<String, JSONObject> archived = ArcticShift.getCommentTree(submissionId);
            Logger.printInfo(() -> "Thread " + submissionId + ": " + wanted
                    + " comments to restore, " + archived.size() + " archived");

            if (!archived.isEmpty()) {
                removedComments.retainAll(archived.keySet());
                Logger.printInfo(() -> "Thread " + submissionId + ": the archive has "
                        + removedComments.size() + " of them");
                changed |= restoreComments(comments, archived);
            }
        }

        return changed ? listings.toString() : null;
    }

    @Nullable
    private static JSONObject firstChildData(@Nullable JSONObject listing) {
        JSONArray children = childrenOf(listing);
        if (children == null || children.length() == 0) {
            return null;
        }
        JSONObject first = children.optJSONObject(0);
        return first == null ? null : first.optJSONObject("data");
    }

    @Nullable
    private static JSONArray childrenOf(@Nullable JSONObject listing) {
        if (listing == null) return null;
        JSONObject data = listing.optJSONObject("data");
        return data == null ? null : data.optJSONArray("children");
    }

    private static boolean isRemoved(JSONObject node, String field) {
        String value = node.optString(field, "");
        return REMOVED.equals(value) || DELETED.equals(value);
    }

    private static void collectRemoved(JSONArray children, Set<String> into) {
        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.optJSONObject(i);
            if (child == null) continue;

            JSONObject data = child.optJSONObject("data");
            if (data == null) continue;

            // An account that has been deleted takes the name off comments whose text is
            // still there, so the text is not the only thing worth asking about.
            if (isRemoved(data, "body") || isDeletedAuthor(data)) {
                String id = data.optString("id", null);
                if (id != null) into.add(id);
            }

            JSONArray replies = repliesOf(data);
            if (replies != null) collectRemoved(replies, into);
        }
    }

    private static boolean restoreComments(JSONArray children, Map<String, JSONObject> archived)
            throws JSONException {
        boolean changed = false;
        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.optJSONObject(i);
            if (child == null) continue;

            JSONObject data = child.optJSONObject("data");
            if (data == null) continue;

            JSONObject source = archived.get(data.optString("id", ""));
            if (source != null) {
                if (isRemoved(data, "body")) {
                    changed |= merge(data, source, "body");
                }
                // Restored on its own as well, since a comment can keep its text and lose only
                // the name above it.
                changed |= restoreAuthor(data, source);
            }

            JSONArray replies = repliesOf(data);
            if (replies != null) changed |= restoreComments(replies, archived);
        }
        return changed;
    }

    /**
     * "replies" is an empty string when a comment has none, so only an object counts.
     */
    @Nullable
    private static JSONArray repliesOf(JSONObject comment) {
        JSONObject replies = comment.optJSONObject("replies");
        if (replies == null) return null;
        JSONObject data = replies.optJSONObject("data");
        return data == null ? null : data.optJSONArray("children");
    }

    /**
     * Copies the archived text back in, marked so it is not mistaken for content Reddit still
     * serves. Sync has no field for Boost's removal reason markers, so it goes inline.
     */
    private static boolean merge(JSONObject target, JSONObject source, String textField)
            throws JSONException {
        String text = source.optString(textField, "");
        if (text.isEmpty() || REMOVED.equals(text) || DELETED.equals(text)) {
            return false;
        }

        target.put(textField, RemovalReason.markerFor(source) + " " + text);
        return true;
    }

    private static boolean isDeletedAuthor(JSONObject node) {
        return DELETED.equals(node.optString("author", ""));
    }

    /**
     * Puts back a name Reddit no longer serves, left plain rather than marked: the text it sits
     * above is what was removed, and the name is only missing because the account went with it.
     */
    private static boolean restoreAuthor(JSONObject target, JSONObject source)
            throws JSONException {
        if (!isDeletedAuthor(target)) {
            return false;
        }
        String author = source.optString("author", "");
        if (author.isEmpty() || DELETED.equals(author) || REMOVED.equals(author)) {
            return false;
        }
        target.put("author", author);
        return true;
    }
}
