package app.morphe.extension.syncforreddit.ui;

import app.morphe.extension.shared.Logger;

/**
 * Reports what Sync makes of a body it is about to draw.
 *
 * <p>An address written in a post is not drawn as a link, while the same address written in a
 * comment is, and everything either could turn on happens between the text arriving and the
 * words appearing. Saying what went in and what came out of that says which half to look at.
 *
 * @noinspection unused
 */
public final class MarkdownTracePatch {
    /** Enough of each to tell what happened without filling the log with bodies. */
    private static final int SHOWN = 220;

    private MarkdownTracePatch() {}

    /**
     * Reported as it arrives and again as it leaves, rather than both at the end: what arrived
     * is held in a register the method goes on to use for something else.
     */
    public static void traceIn(String before) {
        try {
            if (before != null && before.contains("://")) {
                Logger.printInfo(() -> "markdown in:  " + shorten(before));
            }
        } catch (Exception ex) {
            Logger.printInfo(() -> "Could not report the markdown: " + ex);
        }
    }

    public static String traceOut(String after) {
        try {
            if (after != null && after.contains("://")) {
                Logger.printInfo(() -> "markdown out: " + shorten(after));
            }
        } catch (Exception ex) {
            Logger.printInfo(() -> "Could not report the markdown: " + ex);
        }
        return after;
    }

    private static String shorten(String text) {
        if (text == null) {
            return "null";
        }
        String flat = text.replace('\n', ' ');
        return flat.length() <= SHOWN ? flat : flat.substring(0, SHOWN) + "…";
    }
}
