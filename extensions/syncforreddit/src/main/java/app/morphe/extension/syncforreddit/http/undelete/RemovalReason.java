/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.syncforreddit.http.undelete;

import org.json.JSONObject;

/**
 * Marks restored text with why it was taken down, using the same symbols Patcheddit's Boost
 * patch shows. Boost puts them in a field of its own model and renders them beside the author.
 * Sync has no such field, so the marker is prefixed to the text instead.
 */
final class RemovalReason {
    private static final String BY_AUTHOR = "🗑";      // wastebasket
    private static final String BY_MODERATOR = "🧹";   // broom
    private static final String BY_ADMIN = "🚓";       // police car
    private static final String BY_ANTI_SPAM = "🤖";   // robot
    private static final String BY_ANTI_EVIL = "👿";   // imp
    private static final String COPYRIGHT = "©️";      // copyright
    private static final String UNKNOWN = "🕒";        // clock, restored from an archive

    private RemovalReason() {}

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
