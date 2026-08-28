/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.syncforreddit.http.undelete;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.syncforreddit.http.ArchiveRequests;

/**
 * Reads archived Reddit content from Project Arctic Shift.
 *
 * <p>Ported from Patcheddit's Boost implementation, reworked to use org.json rather than
 * Jackson, since Sync already depends on org.json and reads raw markdown rather than the
 * rendered {@code _html} fields Boost had to produce.
 */
public class ArcticShift {
    private static final String BASE_URL = "https://arctic-shift.photon-reddit.com/api/";

    /** Arctic Shift is a free community service, so repeat views of a thread are served locally. */
    private static final int CACHE_SIZE = 32;

    private static final Map<String, Map<String, JSONObject>> commentCache =
            Collections.synchronizedMap(new LinkedHashMap<String, Map<String, JSONObject>>(
                    CACHE_SIZE + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Map<String, JSONObject>> eldest) {
                    return size() > CACHE_SIZE;
                }
            });

    private ArcticShift() {}

    private static JSONObject get(String url) throws IOException, JSONException {
        Logger.printDebug(() -> "Arctic Shift: " + url);

        String body = ArchiveRequests.get(url, "application/json");
        if (body == null || body.isEmpty()) {
            return null;
        }
        return new JSONObject(body);
    }

    /**
     * @return The archived submission, or null if Arctic Shift has nothing for it.
     */
    @Nullable
    public static JSONObject getSubmission(String submissionId) throws IOException, JSONException {
        JSONObject response = get(BASE_URL + "posts/ids?ids=" + submissionId);
        if (response == null) {
            return null;
        }
        JSONArray data = response.optJSONArray("data");
        if (data == null || data.length() == 0) {
            return null;
        }
        return data.optJSONObject(0);
    }

    /**
     * Fetches the whole archived comment tree in a single call, rather than one request per
     * removed comment.
     *
     * @return Comment id to archived comment, empty when Arctic Shift has nothing.
     */
    public static Map<String, JSONObject> getCommentTree(String submissionId)
            throws IOException, JSONException {
        Map<String, JSONObject> cached = commentCache.get(submissionId);
        if (cached != null) {
            return cached;
        }

        Map<String, JSONObject> byId = new HashMap<>();
        JSONObject response = get(BASE_URL + "comments/tree?limit=25000&link_id=" + submissionId);
        if (response != null) {
            JSONArray data = response.optJSONArray("data");
            if (data != null) {
                collect(data, byId);
            }
        }

        commentCache.put(submissionId, byId);
        return byId;
    }

    /**
     * Walks the Reddit shaped tree, which nests replies as a listing under each comment.
     */
    private static void collect(JSONArray children, Map<String, JSONObject> byId) {
        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.optJSONObject(i);
            if (child == null) continue;

            JSONObject data = child.optJSONObject("data");
            if (data == null) continue;

            String id = data.optString("id", null);
            if (id != null) {
                byId.put(id, data);
            }

            // "replies" is an empty string when there are none, so only recurse into an object.
            JSONObject replies = data.optJSONObject("replies");
            if (replies != null) {
                JSONObject repliesData = replies.optJSONObject("data");
                if (repliesData != null) {
                    JSONArray grandChildren = repliesData.optJSONArray("children");
                    if (grandChildren != null) {
                        collect(grandChildren, byId);
                    }
                }
            }
        }
    }
}
