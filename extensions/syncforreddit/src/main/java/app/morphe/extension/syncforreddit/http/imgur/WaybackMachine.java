/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.syncforreddit.http.imgur;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.requests.Requester;

/**
 * Looks up archived copies of URLs the origin no longer serves.
 *
 * <p>Ported from Patcheddit's Boost implementation, using org.json rather than Jackson and an
 * in memory cache rather than a Room database.
 */
public class WaybackMachine {
    private static final String TIMEMAP_URL = "https://web.archive.org/web/timemap/json/";

    /** The "if_" modifier serves the archived bytes without the Wayback page furniture. */
    private static final String CONTENT_URL = "https://web.archive.org/web/%sif_/%s";

    private static final int CACHE_SIZE = 128;

    /** Empty value means the lookup was made and the archive had nothing. */
    private static final Map<String, String> cache =
            Collections.synchronizedMap(new LinkedHashMap<String, String>(CACHE_SIZE + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > CACHE_SIZE;
                }
            });

    private WaybackMachine() {}

    /**
     * @return A URL serving the archived copy, or null if there is no usable snapshot.
     */
    @Nullable
    public static String findSnapshot(String contentUrl) throws IOException, JSONException {
        String cached = cache.get(contentUrl);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }

        String snapshot = lookUp(contentUrl);
        cache.put(contentUrl, snapshot == null ? "" : snapshot);
        return snapshot;
    }

    @Nullable
    private static String lookUp(String contentUrl) throws IOException, JSONException {
        Logger.printDebug(() -> "Wayback Machine: " + contentUrl);

        HttpURLConnection connection = (HttpURLConnection) new URL(TIMEMAP_URL + contentUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setUseCaches(false);

        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            return null;
        }

        JSONArray rows = Requester.parseJSONArrayAndDisconnect(connection);

        // Row 0 is the column header. Later rows are snapshots, oldest first, and many of them
        // are the redirects Imgur served on its way to removing something, so the status code
        // has to be checked rather than simply taking the first.
        String timestamp = null;
        for (int i = 1; i < rows.length(); i++) {
            JSONArray row = rows.optJSONArray(i);
            if (row == null || row.length() < 5) continue;

            if ("200".equals(row.optString(4, ""))) {
                timestamp = row.optString(1, null);
                break;
            }
        }

        if (timestamp == null) {
            return null;
        }
        return String.format(CONTENT_URL, timestamp, contentUrl);
    }
}
