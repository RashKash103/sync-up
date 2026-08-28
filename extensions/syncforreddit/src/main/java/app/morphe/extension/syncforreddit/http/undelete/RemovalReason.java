/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.syncforreddit.http.undelete;

import org.json.JSONObject;

/**
 * Says why something was taken down. Patcheddit's Boost patch shows this as a symbol in a field
 * of its own model, rendered beside the author. Sync has no such field, so the wording is put
 * into the line under the author instead, where it reads as part of the comment's own header
 * rather than as part of what the author wrote.
 */
final class RemovalReason {
    private static final String DELETED = "[deleted]";
    private static final String REMOVED = "[removed]";

    private static final String BY_AUTHOR = "🗑";      // wastebasket
    private static final String BY_MODERATOR = "🧹";   // broom
    private static final String BY_ADMIN = "🚓";       // police car
    private static final String BY_ANTI_SPAM = "🤖";   // robot
    private static final String BY_ANTI_EVIL = "👿";   // imp
    private static final String COPYRIGHT = "©️";      // copyright
    private static final String UNKNOWN = "🕒";        // clock, restored from an archive

    private static final String BY_AUTHOR_TEXT = "deleted by author";
    private static final String BY_MODERATOR_TEXT = "removed by moderator";
    private static final String BY_ADMIN_TEXT = "removed by admin";
    private static final String BY_ANTI_SPAM_TEXT = "caught by the spam filter";
    private static final String BY_ANTI_EVIL_TEXT = "removed by anti evil operations";
    private static final String COPYRIGHT_TEXT = "removed over copyright";
    private static final String REMOVED_TEXT = "removed";
    private static final String UNKNOWN_TEXT = "recovered from the archive";

    private RemovalReason() {}

    /**
     * Reddit does not always say who removed something, and Arctic Shift only has what Reddit
     * exposed at the time, so an unattributed category is the common case rather than an error.
     * What Reddit leaves in place of the text says more than it looks: an author deleting their
     * own comment leaves "[deleted]", while anyone else removing it leaves "[removed]".
     *
     * @param placeholder What Reddit served in place of the text that was taken away.
     */
    static String describe(JSONObject archived, String placeholder) {
        if (DELETED.equals(placeholder)) {
            return BY_AUTHOR_TEXT;
        }

        String category = archived.optString("removed_by_category", "");

        switch (category) {
            case "deleted":
            case "author":
                return BY_AUTHOR_TEXT;
            case "moderator":
            case "community_ops":
                return BY_MODERATOR_TEXT;
            case "reddit":
            case "admin":
                return BY_ADMIN_TEXT;
            case "automod_filtered":
            case "anti_spam":
                return BY_ANTI_SPAM_TEXT;
            case "anti_evil_ops":
                return BY_ANTI_EVIL_TEXT;
            case "copyright_takedown":
            case "content_takedown":
                return COPYRIGHT_TEXT;
            default:
                // Removed by someone other than the author, and Reddit did not say who.
                return REMOVED.equals(placeholder) ? REMOVED_TEXT : UNKNOWN_TEXT;
        }
    }

    /**
     * Reddit does not always say who removed something, and Arctic Shift only has what Reddit
     * exposed at the time, so an unlabelled marker is the common case rather than an error.
     */
    static String markerFor(JSONObject archived) {
        String category = archived.optString("removed_by_category", "");

        switch (category) {
            case "deleted":
            case "author":
                return BY_AUTHOR;
            case "moderator":
            case "community_ops":
                return BY_MODERATOR;
            case "reddit":
            case "admin":
                return BY_ADMIN;
            case "automod_filtered":
            case "anti_spam":
                return BY_ANTI_SPAM;
            case "anti_evil_ops":
                return BY_ANTI_EVIL;
            case "copyright_takedown":
            case "content_takedown":
                return COPYRIGHT;
            default:
                return UNKNOWN;
        }
    }
}
