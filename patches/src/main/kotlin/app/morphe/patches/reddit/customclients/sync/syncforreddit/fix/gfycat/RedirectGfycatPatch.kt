package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.gfycat

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.morphe.patches.reddit.customclients.sync.syncforreddit.http.interceptHttpRequests
import app.morphe.util.returnEarly

internal const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/RedirectGfycatPatch;"

@Suppress("unused")
val redirectGfycatPatch = bytecodePatch(
    name = "Redirect Gfycat links to RedGifs",
    description = "Loads Gfycat links from RedGifs. Gfycat's domains no longer resolve, so without " +
            "this every Gfycat link fails.",
    default = true
) {
    dependsOn(sharedExtensionPatch, interceptHttpRequests)

    compatibleWith(*SyncForRedditCompatible)

    execute {
        Fingerprint(
            definingClass = EXTENSION_CLASS_DESCRIPTOR,
            name = "isPatchIncluded",
        ).method.returnEarly(true)

        // region Supply Sync's user agent, which RedGifs ties its token to.

        getUserAgentFingerprint.method.addInstructions(
            0,
            """
            invoke-static       { }, ${getOriginalUserAgentFingerprint.method}
            move-result-object  v0
            return-object       v0
            """
        )

        // endregion
    }
}
