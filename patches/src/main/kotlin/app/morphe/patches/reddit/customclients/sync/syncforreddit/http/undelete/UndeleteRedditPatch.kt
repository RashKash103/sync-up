package app.morphe.patches.reddit.customclients.sync.syncforreddit.http.undelete

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.morphe.patches.reddit.customclients.sync.syncforreddit.http.interceptHttpRequests
import app.morphe.util.returnEarly

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/http/undelete/UndeleteRedditPatch;"

@Suppress("unused")
val undeleteRedditPatch = bytecodePatch(
    name = "Automatically undelete Reddit content",
    description = "Restores the text of removed posts and comments from Project Arctic Shift. " +
            "Restored text is marked to show why it was taken down. Only text can be " +
            "recovered, and only where the archive has it.",
    default = false
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
