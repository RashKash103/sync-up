package app.morphe.patches.reddit.customclients.sync.syncforreddit.http.imgur

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.morphe.patches.reddit.customclients.sync.syncforreddit.http.interceptHttpRequests
import app.morphe.util.returnEarly

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/http/imgur/FixImgurProxyPatch;"

@Suppress("unused")
val fixImgurProxyPatch = bytecodePatch(
    name = "Fix Imgur links",
    description = "Sync resolves Imgur links through a proxy of its own that no longer exists, " +
            "so they fail to load. Answers those requests locally instead. Album contents are " +
            "read from an archived copy of the album page, which only works for albums the " +
            "archive captured while Imgur still rendered them.",
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
