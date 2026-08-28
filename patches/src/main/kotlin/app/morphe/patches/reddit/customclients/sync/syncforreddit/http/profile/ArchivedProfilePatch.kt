package app.morphe.patches.reddit.customclients.sync.syncforreddit.http.profile

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.morphe.patches.reddit.customclients.sync.syncforreddit.http.interceptHttpRequests
import app.morphe.util.returnEarly

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/http/profile/ArchivedProfilePatch;"

@Suppress("unused")
val archivedProfilePatch = bytecodePatch(
    name = "Show a hidden profile from the archive",
    description = "Fills in a profile Reddit answers with nothing. An account can hide what it " +
            "has written from its own profile, which Sync shows as a user with no posts, while " +
            "Project Arctic Shift still serves what it recorded when those posts and comments " +
            "were public. Only a profile that comes back empty is filled in, and only its posts " +
            "and comments tabs.",
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
