package app.morphe.extension.syncforreddit.ui.comments;

import java.util.Locale;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.syncforreddit.settings.SyncUpSettings;

/**
 * Whether a link in a comment is drawn as the picture it points at, rather than as a chip naming
 * where it goes.
 *
 * <p>Sync already draws some of them inline, but only where the address ends in one of five
 * extensions or belongs to one of a handful of named services. Anything else — a Reddit gallery
 * link, an Imgur page, a RedGifs or Giphy link, an address that names no extension at all —
 * falls through to the chip, which is what makes the behaviour look arbitrary from a thread
 * where one comment draws and the next does not.
 *
 * <p>This widens that test to everything Sync can actually draw, on a setting of its own.
 *
 * @noinspection unused
 */
public final class InlineCommentMediaPatch {
    private static final String INLINE_EVERYTHING = "sync_up_inline_comment_media";

    /** Hosts that serve a picture or a video whatever the address on them looks like. */
    private static final String[] MEDIA_HOSTS = {
            "i.redd.it", "preview.redd.it", "v.redd.it", "i.imgur.com", "imgur.com",
            "redgifs.com", "gfycat.com", "giphy.com", "tenor.com", "media.giphy.com",
            "i.redditmedia.com", "redditmedia.com", "imgflip.com", "i.imgflip.com",
            "streamable.com", "media.tumblr.com", "youtube.com", "youtu.be",
    };

    /** What an address ends with when it is media, whatever host it is on. */
    private static final String[] MEDIA_ENDINGS = {
            ".jpg", ".jpeg", ".png", ".gif", ".gifv", ".webp", ".bmp", ".apng",
            ".mp4", ".webm", ".mov", ".m4v",
    };

    private static boolean said;

    /** How many calls are worth writing down before the point is made. */
    private static final int WORTH_SAYING = 200;

    private static int parsed;
    private static int gated;
    private static int asked;

    private InlineCommentMediaPatch() {}

    private static int drew;

    private static int seen;

    /**
     * Called with every address the link handling is given, before any of it is decided.
     *
     * <p>Said for each one because a capture showing only some of them cannot be told from a
     * capture where only some were handled, and which of the two it was has mattered twice.
     */
    public static void linkSeen(String link) {
        if (seen < WORTH_SAYING) {
            seen++;
            Logger.printInfo(() -> "inline comments: handling the link " + link);
        }
    }

    /** Called where Sync draws one of Reddit's own giphy pictures, which it has its own path for. */
    public static void giphy(String link) {
        Logger.printInfo(() -> "inline comments: a giphy picture, drawn from " + link);
    }

    /**
     * Called wherever text with markup in it is turned into something drawable, before any of
     * it is looked at.
     *
     * <p>Nothing at all was heard from the link handling, and a capture that says nothing
     * cannot tell "the code declined" from "the code never ran". This says which.
     *
     * @param markup What is about to be turned into drawable text.
     */
    public static void drawing(String markup) {
        if (drew < WORTH_SAYING) {
            drew++;
            String head = markup == null ? "nothing"
                    : markup.substring(0, Math.min(120, markup.length())).replace('\n', ' ');
            Logger.printInfo(() -> "inline comments: drawing text (" + drew + "): " + head);
        }
    }

    /**
     * Called for every link drawn in a post or a comment, before anything is decided.
     *
     * <p>Said unconditionally for the first few: a capture with none of these in it means this
     * is not the code that draws the thing in question, rather than that it declined to.
     */
    public static void parsing() {
        if (parsed < WORTH_SAYING) {
            parsed++;
            Logger.printInfo(() -> "inline comments: a link is being drawn (" + parsed + ")");
        }
    }

    /**
     * Sync asks something about where the link is before it asks anything about the link. What
     * that is, is not named in the app; it is answered here so that a capture says whether it
     * is what stops a picture being drawn where it sits.
     *
     * @param sync What Sync decided about where this link is.
     */
    public static boolean here(boolean sync) {
        boolean on = inlineEverything();
        if (gated < WORTH_SAYING) {
            gated++;
            Logger.printInfo(() -> "inline comments: Sync says this is "
                    + (sync ? "" : "not ") + "somewhere to draw one, widening is "
                    + (on ? "on" : "off"));
        }
        return sync || on;
    }

    public static boolean isPatchIncluded() {
        // Overridden by patch.
        return false;
    }

    /** Whether the setting is on, so Sync's own gate can be opened alongside it. */
    public static boolean inlineEverything() {
        if (!isPatchIncluded()) {
            return false;
        }
        boolean on = SyncUpSettings.flag(INLINE_EVERYTHING, false);
        if (!said) {
            said = true;
            SyncUpSettings.report("inline comments", INLINE_EVERYTHING);
            Logger.printInfo(() -> "inline comments: widening is " + (on ? "on" : "off"));
        }
        return on;
    }

    /**
     * Sync will not draw anything inline unless its own inline-previews setting is on, and that
     * setting is about its own narrow list. Opening the gate here lets the widened test below be
     * the one that decides.
     *
     * @param sync What Sync's own setting says.
     */
    public static boolean orInlineEverything(boolean sync) {
        boolean on = inlineEverything();
        if (asked < WORTH_SAYING) {
            asked++;
            Logger.printInfo(() -> "inline comments: Sync's own setting is "
                    + (sync ? "on" : "off") + ", widening is " + (on ? "on" : "off"));
        }
        return sync || on;
    }

    /**
     * Whether this link is one to draw where it sits rather than to show a preview of the page
     * it is on.
     *
     * <p>Sync decides whether to preview a page before it decides anything about drawing a
     * picture, and a link it has already made a preview of is marked as dealt with and never
     * looked at again. A gif was therefore always a preview of the page it is on, whatever the
     * rest of this answered.
     */
    public static boolean claimsAsMedia(String link) {
        if (!isPatchIncluded() || link == null || link.isEmpty() || !inlineEverything()) {
            return false;
        }
        boolean media = isMedia(link);
        if (media) {
            Logger.printInfo(() -> "inline comments: claiming " + link
                    + " rather than letting it become a preview");
        }
        return media;
    }

    /**
     * @param sync     What Sync decided on its own.
     * @param link     The address in the comment.
     * @return Whether to draw the link as the media it points at.
     */
    public static boolean shouldInline(boolean sync, String link) {
        if (sync) {
            Logger.printDebug(() -> "inline comments: Sync already draws " + link);
            return true;
        }
        if (!inlineEverything()) {
            Logger.printDebug(() -> "inline comments: leaving " + link + " as a chip");
            return false;
        }
        if (link == null || link.isEmpty()) {
            return false;
        }

        boolean media = isMedia(link);
        Logger.printInfo(() -> "inline comments: " + link + " -> "
                + (media ? "drawing it inline" : "not media, left as a chip"));
        return media;
    }

    /** @return Whether the address points at something that can be drawn. */
    private static boolean isMedia(String link) {
        String lower = link.toLowerCase(Locale.ROOT);

        // Anything after the address itself is no help in telling what it points at.
        int query = lower.indexOf('?');
        String path = query < 0 ? lower : lower.substring(0, query);
        for (String ending : MEDIA_ENDINGS) {
            if (path.endsWith(ending)) {
                return true;
            }
        }

        for (String host : MEDIA_HOSTS) {
            // Matched against the whole address rather than a parsed host, since what arrives
            // here is sometimes written without a scheme.
            if (lower.contains("//" + host + "/") || lower.contains("." + host + "/")
                    || lower.contains("//" + host) && lower.endsWith(host)) {
                return true;
            }
        }
        return false;
    }
}
