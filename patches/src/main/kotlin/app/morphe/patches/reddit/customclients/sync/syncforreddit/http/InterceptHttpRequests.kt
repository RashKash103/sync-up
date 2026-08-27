package app.morphe.patches.reddit.customclients.sync.syncforreddit.http

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal const val OKHTTP_EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/http/OkHttpRequestHook;"

private const val INSTALL_CLIENT_METHOD =
    "install(Lokhttp3/OkHttpClient;)Lokhttp3/OkHttpClient;"

/**
 * Has no name, so it is not offered to users directly. The patches that need to see Reddit
 * traffic depend on it.
 */
val interceptHttpRequests = bytecodePatch(
    description = "Routes Sync's Reddit API traffic through the extension's interceptors.",
    default = false
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*SyncForRedditCompatible)

    execute {
        volleyPerformRequestFingerprint.method.apply {
            val index = indexOfFirstInstructionOrThrow {
                val reference = getReference<MethodReference>()
                reference?.name == "newCall" && reference.definingClass == "Lokhttp3/OkHttpClient;"
            }
            val clientRegister = getInstruction<FiveRegisterInstruction>(index).registerC

            addInstructions(
                index,
                """
                invoke-static       { v$clientRegister }, $OKHTTP_EXTENSION_CLASS_DESCRIPTOR->$INSTALL_CLIENT_METHOD
                move-result-object  v$clientRegister
                """
            )
        }
    }
}
