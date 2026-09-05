package app.morphe.extension.syncforreddit.ui.gestures;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import app.morphe.extension.shared.Logger;

/**
 * The gesture settings, read from the same store Sync writes its own settings to.
 *
 * <p>They are read at the moment a gesture is made rather than held, so turning one off in the
 * settings takes effect on the next touch without the app being restarted or repatched. The
 * entries themselves are added to Sync's own settings screen by the patch, so Sync's preference
 * machinery is what displays them and what writes them here.
 *
 * @noinspection unused
 */
public final class VideoGestureSettings {
    static final String DOUBLE_TAP = "sync_up_video_double_tap";
    static final String SEEK = "sync_up_video_seek";
    static final String SEEK_NEEDS_DOUBLE_TAP = "sync_up_video_seek_double_tap";

    /** Never. */
    static final int SEEK_NEVER = 0;
    /** Where a sideways drag means nothing else, which is a video on its own. */
    static final int SEEK_OUTSIDE_GALLERIES = 1;
    /** Everywhere, taking the gesture from an album, which pages with it. */
    static final int SEEK_ANYWHERE = 2;
    static final String VOLUME = "sync_up_video_volume";
    static final String SEEK_SPAN = "sync_up_video_seek_span";

    /** Seconds covered by a swipe across the whole width, when the setting is unset. */
    static final int DEFAULT_SEEK_SPAN = 90;

    /** Where a video is on its own, and nothing else wants a sideways drag. */
    static final int DEFAULT_SEEK = SEEK_OUTSIDE_GALLERIES;

    private VideoGestureSettings() {}

    static boolean enabled(Context context, String key, boolean fallback) {
        SharedPreferences settings = store(context);
        if (settings == null) {
            return fallback;
        }
        try {
            return settings.getBoolean(key, fallback);
        } catch (ClassCastException ex) {
            // Written by something else under the same name; the default is the safer answer.
            Logger.printInfo(() -> "Could not read " + key + ": " + ex);
            return fallback;
        }
    }

    /**
     * A setting Sync stores as text even though it is a number, which is how its own list
     * settings are written.
     */
    static int number(Context context, String key, int fallback) {
        SharedPreferences settings = store(context);
        if (settings == null) {
            return fallback;
        }
        try {
            String held = settings.getString(key, null);
            return held == null || held.isEmpty() ? fallback : Integer.parseInt(held.trim());
        } catch (Exception ex) {
            Logger.printInfo(() -> "Could not read " + key + ": " + ex);
            return fallback;
        }
    }

    private static SharedPreferences store(Context context) {
        try {
            return PreferenceManager.getDefaultSharedPreferences(context);
        } catch (Exception ex) {
            Logger.printInfo(() -> "Could not reach the settings: " + ex);
            return null;
        }
    }
}
