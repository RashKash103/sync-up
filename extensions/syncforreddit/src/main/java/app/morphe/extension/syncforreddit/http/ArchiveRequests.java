package app.morphe.extension.syncforreddit.http;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Semaphore;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.requests.Requester;

/**
 * Requests to the archives the undelete patches read from.
 *
 * <p>These are free community services, and a feed can ask for dozens of images at once, so
 * the calls are held to a few at a time and spaced apart. They are also slow enough to matter:
 * without a timeout a stalled lookup holds an image loading thread indefinitely, which is worse
 * than simply not recovering the image.
 */
public final class ArchiveRequests {
    /** Enough to keep a feed moving without flooding the archive. */
    private static final int CONCURRENT_REQUESTS = 2;

    private static final long MINIMUM_SPACING_MS = 250;

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    private static final Semaphore inFlight = new Semaphore(CONCURRENT_REQUESTS, true);
    private static final Object spacingLock = new Object();
    private static long lastRequestAt;

    private ArchiveRequests() {}

    /**
     * @return The response body, or null if the request did not come back with a 200.
     */
    @Nullable
    public static String get(String url, String accept) throws IOException {
        try {
            inFlight.acquire();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting to call the archive", ex);
        }

        try {
            space();

            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", accept);
            connection.setUseCaches(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                connection.disconnect();
                return null;
            }
            return Requester.parseStringAndDisconnect(connection);
        } catch (IOException ex) {
            // A slow or unhappy archive is an ordinary outcome here, not a failure worth
            // taking the caller down over.
            Logger.printDebug(() -> "Archive request failed: " + url + " (" + ex.getMessage() + ")");
            return null;
        } finally {
            inFlight.release();
        }
    }

    private static void space() {
        long waitFor;
        synchronized (spacingLock) {
            long now = System.currentTimeMillis();
            waitFor = Math.max(0, lastRequestAt + MINIMUM_SPACING_MS - now);
            lastRequestAt = now + waitFor;
        }

        if (waitFor > 0) {
            try {
                Thread.sleep(waitFor);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
