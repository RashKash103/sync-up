package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.thumbnail

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.morphe.patches.reddit.customclients.sync.syncforreddit.http.interceptHttpRequests
import app.morphe.util.returnEarly

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/http/imgur/RecoverThumbnailsPatch;"

private const val REMEMBER_METHOD =
    "rememberThumbnails(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V"

@Suppress("unused")
val recoverThumbnailsPatch = bytecodePatch(
    name = "Recover post thumbnails from the archive",
    description = "Restores the thumbnail beside a post whose Reddit preview has been purged, " +
            "which is why an old Imgur album shows a blank tile even though opening it works. " +
            "The linked image is fetched from the Wayback Machine only once its preview has " +
            "actually failed.",
    default = true
) {
    dependsOn(sharedExtensionPatch, interceptHttpRequests)

    compatibleWith(*SyncForRedditCompatible)

    execute {
        Fingerprint(
            definingClass = EXTENSION_CLASS_DESCRIPTOR,
            name = "isPatchIncluded",
        ).method.returnEarly(true)

        // Injected at the top of the method, where the locals it uses are still free. Reading
        // the addresses here rather than at the load call keeps this clear of the registers
        // Sync has in flight by then.
        postThumbnailBindFingerprint.method.addInstructions(
            0,
            """
            invoke-virtual      { p1 }, $POST_MODEL->Z0()Ljava/lang/String;
            move-result-object  v0
            invoke-virtual      { p1 }, $POST_MODEL->H0()Ljava/lang/String;
            move-result-object  v1
            invoke-virtual      { p1 }, $POST_MODEL->e1()Ljava/lang/String;
            move-result-object  v2
            invoke-static       { v0, v1, v2 }, $EXTENSION_CLASS_DESCRIPTOR->$REMEMBER_METHOD
            """
        )
    }
}
