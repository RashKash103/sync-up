/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.syncforreddit.http.imgur;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;

import java.io.IOException;
import java.net.HttpURLConnection;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.requests.PatchedditInterceptor;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Serves Imgur images the site no longer has from the Wayback Machine. Imgur purged a large
 * amount of older content, so links in old Reddit posts frequently 404.
 *
 * <p>Sync loads images through Glide rather than Volley, so this only sees them because the
 * interception patch hooks Glide's client as well.
 */
public class UndeleteImgurPatch extends PatchedditInterceptor {
    /** The letters Imgur appends to an id for its sized copies. */
    private static final String SIZE_SUFFIXES = "sbtmlh";

    @Override
    public boolean isPatchIncluded() {
        // Overridden by patch.
        return false;
    }

    /**
     * Imgur names a sized copy by appending one letter to the id, and ids themselves are five
     * or seven characters, so a six or eight character one ending in a size letter is a sized
     * copy rather than an image in its own right.
     */
    @Nullable
    private static String withoutSizeSuffix(String contentUrl) {
        int slash = contentUrl.lastIndexOf('/');
        int dot = contentUrl.lastIndexOf('.');
        if (slash < 0 || dot <= slash + 1) {
            return null;
        }

        String id = contentUrl.substring(slash + 1, dot);
        if (id.length() != 6 && id.length() != 8) {
            return null;
        }
        if (SIZE_SUFFIXES.indexOf(id.charAt(id.length() - 1)) < 0) {
            return null;
        }

        return contentUrl.substring(0, slash + 1)
                + id.substring(0, id.length() - 1)
                + contentUrl.substring(dot);
    }

    /**
     * Imgur mostly does not answer a removed image with a 404. It redirects to a placeholder
     * image carrying the words "The image you are requesting does not exist or is no longer
     * available", which arrives as a perfectly ordinary 200 and gets displayed as the picture.
     */
    private static boolean isMissing(Response response) {
        if (response.code() == HttpURLConnection.HTTP_NOT_FOUND) {
            return true;
        }
        // The request the response came from, which after a redirect is the placeholder.
        String path = response.request().url().encodedPath();
        return path.startsWith("/removed.");
    }

    @NonNull
    @Override
    protected Response doIntercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        HttpUrl url = request.url();

        if (!url.host().endsWith("imgur.com")) {
            return chain.proceed(request);
        }

        Response response = chain.proceed(request);
        if (!isMissing(response)) {
            return response;
        }

        String contentUrl = url.toString();
        String snapshot;
        try {
            snapshot = WaybackMachine.findSnapshot(contentUrl);

            // Sync asks for a sized copy in places such as the feed, and the archive rarely
            // holds those: it captured what pages linked to, which is the image itself. The
            // full image stands in perfectly well, since it is only being scaled down.
            if (snapshot == null) {
                String fullSize = withoutSizeSuffix(contentUrl);
                if (fullSize != null) {
                    Logger.printDebug(() -> "No archived copy of the sized " + contentUrl
                            + ", trying the full image");
                    snapshot = WaybackMachine.findSnapshot(fullSize);
                }
            }
        } catch (JSONException ex) {
            Logger.printException(() -> "Could not read the Wayback Machine index", ex);
            return response;
        } catch (IOException ex) {
            // The Wayback Machine being unreachable should leave the original 404 in place.
            Logger.printException(() -> "Wayback Machine request failed", ex);
            return response;
        }

        if (snapshot == null) {
            Logger.printDebug(() -> "No archived copy of " + contentUrl);
            return response;
        }

        // Closed only once a replacement is certain, so the original is still returned intact
        // on any of the paths above.
        response.close();

        Logger.printDebug(() -> "Serving " + contentUrl + " from the Wayback Machine");
        return chain.proceed(request.newBuilder().url(snapshot).build());
    }
}
