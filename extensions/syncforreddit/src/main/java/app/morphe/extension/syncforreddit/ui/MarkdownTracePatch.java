package app.morphe.extension.syncforreddit.ui;

import app.morphe.extension.shared.Logger;

/**
 * Reports what Sync makes of a body it is about to draw.
 *
 * <p>An address written in a post is not drawn as a link, while the same address written in a
 * comment is, and everything either could turn on happens between the text arriving and the
 * words appearing. Saying what went in and what came out of the markdown says which half to look
 * at, and saying what the drawing then laid over the words says whether a link was made at all.
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

    /**
     * How many spans to report after an address has been appended. A body is drawn one piece at
     * a time and the spans covering a piece follow it, so a handful is enough to see whether the
     * address was made into a link, while leaving the rest of the screen out of the log.
     */
    private static final int FOLLOWING = 16;

    /** Counts down the spans still worth reporting; only ever touched from the drawing thread. */
    private static int following;

    /** Reports a piece of text appended without spans of its own. */
    public static void appended(CharSequence text) {
        try {
            if (text != null && text.toString().contains("://")) {
                following = FOLLOWING;
                Logger.printInfo(() -> "drew plain:   " + shorten(text.toString()));
            }
        } catch (Exception ex) {
            Logger.printInfo(() -> "Could not report the drawing: " + ex);
        }
    }

    /** Reports a piece of text appended carrying spans, and what those spans are. */
    public static void appendedWith(String text, Object[] spans) {
        try {
            if (text != null && text.contains("://")) {
                following = FOLLOWING;
                Logger.printInfo(() -> "drew spanned: " + shorten(text) + " " + names(spans));
            }
        } catch (Exception ex) {
            Logger.printInfo(() -> "Could not report the drawing: " + ex);
        }
    }

    /**
     * Reports a span laid over text already appended, which is how Sync makes the words of a
     * link tappable: the address is written out first and covered afterwards.
     */
    public static void spanApplied(Object span, int start, int end) {
        try {
            if (following > 0) {
                following--;
                Logger.printInfo(() -> "drew span:    " + name(span) + " over " + start + ".." + end);
            }
        } catch (Exception ex) {
            Logger.printInfo(() -> "Could not report the drawing: " + ex);
        }
    }

    private static String names(Object[] spans) {
        if (spans == null) {
            return "(none)";
        }
        StringBuilder joined = new StringBuilder("[");
        for (int index = 0; index < spans.length; index++) {
            joined.append(index == 0 ? "" : ", ").append(name(spans[index]));
        }
        return joined.append(']').toString();
    }

    private static String name(Object span) {
        return span == null ? "null" : span.getClass().getName();
    }

    private static String shorten(String text) {
        if (text == null) {
            return "null";
        }
        String flat = text.replace('\n', ' ');
        return flat.length() <= SHOWN ? flat : flat.substring(0, SHOWN) + "…";
    }
}
