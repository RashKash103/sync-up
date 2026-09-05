package app.morphe.extension.syncforreddit.ui.gestures;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import app.morphe.extension.shared.Logger;
import com.laurencedawson.reddit_sync.ui.views.video.CustomExoPlayerView;

/**
 * The player behind Sync's video view, for the two things the view itself cannot say.
 *
 * <p>Sync's own <code>isVideoPlaying</code> answers whether there is a player at all rather than
 * whether it is playing, so it is true even of a paused video: asking it whether to resume gets
 * the wrong answer every time. And the view offers muting but no volume, where the player behind
 * it takes any level at all, which is what a drag wants.
 *
 * <p>Reached by reflection, and by the names the player library uses: those survive where Sync's
 * own do not. The field is found by what it holds rather than by its name.
 */
final class Playback {
    private final Object player;
    private final Method isPlaying;
    private final Method setPlaying;
    private final Method getVolume;
    private final Method setVolume;

    private Playback(Object player, Method isPlaying, Method setPlaying,
                     Method getVolume, Method setVolume) {
        this.player = player;
        this.isPlaying = isPlaying;
        this.setPlaying = setPlaying;
        this.getVolume = getVolume;
        this.setVolume = setVolume;
    }

    /** @return The player behind the view, or null if it has not made one yet. */
    static Playback of(CustomExoPlayerView view) {
        try {
            for (Field field : view.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object held = field.get(view);
                if (held == null) {
                    continue;
                }
                Method isPlaying = methodOn(held, "getPlayWhenReady");
                if (isPlaying == null) {
                    continue;
                }
                return new Playback(held, isPlaying,
                        methodOn(held, "setPlayWhenReady", boolean.class),
                        methodOn(held, "getVolume"),
                        methodOn(held, "setVolume", float.class));
            }
        } catch (Throwable ex) {
            Logger.printInfo(() -> "Could not reach the player: " + ex);
        }
        return null;
    }

    private static Method methodOn(Object held, String name, Class<?>... takes) {
        try {
            Method method = held.getClass().getMethod(name, takes);
            method.setAccessible(true);
            return method;
        } catch (Throwable ignored) {
            return null;
        }
    }

    boolean isPlaying() {
        try {
            return isPlaying != null && Boolean.TRUE.equals(isPlaying.invoke(player));
        } catch (Throwable ex) {
            Logger.printInfo(() -> "Could not read whether the video is playing: " + ex);
            return false;
        }
    }

    void setPlaying(boolean playing) {
        try {
            if (setPlaying != null) {
                setPlaying.invoke(player, playing);
            }
        } catch (Throwable ex) {
            Logger.printInfo(() -> "Could not start or stop the video: " + ex);
        }
    }

    /** @return The level between silent and full, or -1 where the player will not say. */
    float volume() {
        try {
            return getVolume == null ? -1f : (Float) getVolume.invoke(player);
        } catch (Throwable ex) {
            Logger.printInfo(() -> "Could not read the volume: " + ex);
            return -1f;
        }
    }

    /** Anywhere between silent and full, rather than the handful of steps a device offers. */
    void setVolume(float level) {
        try {
            if (setVolume != null) {
                setVolume.invoke(player, Math.max(0f, Math.min(1f, level)));
            }
        } catch (Throwable ex) {
            Logger.printInfo(() -> "Could not set the volume: " + ex);
        }
    }
}
