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
    name = "Make plain links in a post tappable",
    description = "Writes an address in a post's own body as a link. Sync turns certain " +
            "addresses into links itself, and the ones it knows are Reddit's own, Imgur's and a " +
            "couple besides, so a post whose body is a link to anywhere else is a post with " +
            "nothing to tap. An address already written as a link is left alone.",
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
