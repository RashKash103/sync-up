package app.morphe.patches.reddit.customclients.sync.syncforreddit.http.comments

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.morphe.patches.reddit.customclients.sync.syncforreddit.http.interceptHttpRequests
import app.morphe.util.returnEarly

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/http/comments/PlayCommentVideoPatch;"

@Suppress("unused")
val playCommentVideoPatch = bytecodePatch(
    name = "Play videos posted in comments",
    description = "Points a video posted in a comment at the video. Reddit writes one into the " +
            "comment as a link to a player page on its own site, which Sync can only hand to a " +
            "browser, where it opens on an address naming no subreddit and is answered with a " +
            "banned notice. Rewritten to where the video is actually kept, it opens in Sync's " +
            "own player.",
    default = true
) {
    dependsOn(sharedExtensionPatch, interceptHttpRequests)

    compatibleWith(*SyncForRedditCompatible)

    execute {
        Fingerprint(
            definingClass = EXTENSION_CLASS_DESCRIPTOR,
            name = "isPatchIncluded",
        ).method.returnEarly(true)
    }
}
