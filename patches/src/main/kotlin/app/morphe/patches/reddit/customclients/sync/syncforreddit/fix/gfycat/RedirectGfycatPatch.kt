package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.gfycat

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/RedirectGfycatPatch;"

private const val INSTALL_CLIENT_METHOD =
    "install(Lokhttp3/OkHttpClient;)Lokhttp3/OkHttpClient;"

@Suppress("unused")
val redirectGfycatPatch = bytecodePatch(
    name = "Redirect Gfycat links to RedGifs",
    description = "Answers Gfycat requests from RedGifs, which hosts much of the content that " +
            "moved there before Gfycat shut down. Gfycat's domains no longer resolve, so " +
            "without this every Gfycat link fails to load.",
    default = true
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*SyncForRedditCompatible)

    execute {
        // region Install the interceptor on the client Volley makes its calls with.

        volleyPerformRequestFingerprint.method.apply {
            val index = indexOfFirstInstructionOrThrow {
                val reference = getReference<MethodReference>()
                reference?.name == "newCall" && reference.definingClass == "Lokhttp3/OkHttpClient;"
            }
            val clientRegister = getInstruction<FiveRegisterInstruction>(index).registerC

            addInstructions(
                index,
                """
                invoke-static       { v$clientRegister }, $EXTENSION_CLASS_DESCRIPTOR->$INSTALL_CLIENT_METHOD
                move-result-object  v$clientRegister
                """
            )
        }

        // endregion

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
