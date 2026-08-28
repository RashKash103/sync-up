package app.morphe.patches.reddit.customclients.sync.syncforreddit.http.imgur

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.morphe.patches.reddit.customclients.sync.syncforreddit.http.interceptHttpRequests
import app.morphe.util.returnEarly

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/http/imgur/FixImgurProxyPatch;"

private const val TIMEOUT_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/http/imgur/ImgurRequestTimeout;"

private const val ALLOW_TIME_METHOD =
    "allowTimeForTheArchive(Lcom/android/volley/Request;)V"

/**
 * The requests whose answers now come from an archive rather than from Sync's proxy.
 */
private val imgurRequestSourceFiles = setOf(
    "ImgurGalleryRequest.java",
    "ImgurSingleImageRequest.java",
)

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

        // Volley gives these two and a half seconds by default, which an archive lookup
        // regularly exceeds, so Sync gave up and opened the link in a browser before the
        // answer arrived.
        imgurRequestSourceFiles.forEach { sourceFile ->
            Fingerprint(
                name = "<init>",
                accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
                parameters = listOf(
                    "Ljava/lang/String;",
                    "Lcom/android/volley/Response${'$'}Listener;",
                    "Lcom/android/volley/Response${'$'}ErrorListener;",
                ),
                custom = { _, classDef -> classDef.sourceFile == sourceFile }
            ).method.apply {
                addInstructions(
                    instructions.lastIndex,
                    """
                    invoke-static { p0 }, $TIMEOUT_CLASS_DESCRIPTOR->$ALLOW_TIME_METHOD
                    """
                )
            }
        }
    }
}
