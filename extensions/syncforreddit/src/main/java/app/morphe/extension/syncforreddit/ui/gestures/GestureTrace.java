package app.morphe.extension.syncforreddit.ui.gestures;

import android.view.MotionEvent;
import android.view.View;

import app.morphe.extension.shared.Logger;

/**
 * A record of what the gestures see and what the app does about it, while the chrome of the
 * viewer is still coming and going for reasons not yet established.
 *
 * <p>Temporary, and noisy on purpose.
 *
 * @noinspection unused
 */
public final class GestureTrace {
    private static long lastAt;

    private GestureTrace() {}

    /** Called where Sync turns the chrome of the viewer on or off, whoever asked it to. */
    public static void chromeToggled() {
        try {
            Logger.printInfo(() -> "chrome: toggled by " + calledFrom(3, 16));
        } catch (Exception ignored) {
            // Nothing here is worth interrupting the app for.
        }
    }

    /** Every touch the gestures are offered, and what became of it. */
    public static void touch(MotionEvent event, String state, String decision, View chrome) {
        try {
            long now = event.getEventTime();
            long since = lastAt == 0 ? 0 : now - lastAt;
            lastAt = now;
            Logger.printInfo(() -> "touch: " + action(event.getActionMasked())
                    + " +" + since + "ms at " + Math.round(event.getX()) + ","
                    + Math.round(event.getY())
                    + " state=" + state + " -> " + decision
                    + " chrome=" + (chrome == null ? "?" : chrome.getVisibility()));
        } catch (Exception ignored) {
        }
    }

    /** Anything else worth knowing at the moment it happens. */
    public static void note(String what) {
        try {
            Logger.printInfo(() -> "gesture: " + what);
        } catch (Exception ignored) {
        }
    }

    /** As {@link #note}, with the frames that led there. */
    public static void noteWhere(String what) {
        try {
            Logger.printInfo(() -> "gesture: " + what + " <- " + calledFrom(3, 12));
        } catch (Exception ignored) {
        }
    }

    private static String action(int masked) {
        switch (masked) {
            case MotionEvent.ACTION_DOWN: return "down";
            case MotionEvent.ACTION_UP: return "up";
            case MotionEvent.ACTION_MOVE: return "move";
            case MotionEvent.ACTION_CANCEL: return "cancel";
            case MotionEvent.ACTION_POINTER_DOWN: return "down2";
            case MotionEvent.ACTION_POINTER_UP: return "up2";
            default: return "action" + masked;
        }
    }

    private static String calledFrom(int from, int upTo) {
        StackTraceElement[] frames = new Throwable().getStackTrace();
        StringBuilder path = new StringBuilder();
        for (int at = from; at < frames.length && at < upTo; at++) {
            String owner = frames[at].getClassName();
            if (owner.startsWith("app.morphe.extension.shared")) {
                continue;
            }
            path.append(at == from ? "" : " <- ")
                    .append(owner.substring(owner.lastIndexOf('.') + 1))
                    .append('.').append(frames[at].getMethodName());
        }
        return path.toString();
    }
}
