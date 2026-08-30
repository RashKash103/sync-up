package app.morphe.patches.reddit.customclients.sync.syncforreddit.http.posts

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.morphe.patches.reddit.customclients.sync.syncforreddit.http.interceptHttpRequests
import app.morphe.util.returnEarly

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/http/posts/TappableLinksPatch;"

@Suppress("unused")
val tappableLinksPatch = bytecodePatch(
    name = "Make an address in a post tappable",
    description = "Writes an address in a post's own body as a link. Sync draws a link where " +
            "one is written as a link, and turns a bare address into one only where it " +
            "recognises it, which is Reddit's own addresses, Imgur's and a couple besides, so a " +
            "post whose body is an address to anywhere else has nothing to tap. An address " +
            "already written as a link is left exactly as it is.",
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
