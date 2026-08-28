package app.morphe.patches.reddit.customclients.sync.syncforreddit.http.undelete

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.morphe.patches.reddit.customclients.sync.syncforreddit.http.interceptHttpRequests
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/http/undelete/UndeleteRedditPatch;"

private const val NOTES_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/http/undelete/RestoredComments;"

private const val APPEND_NOTE_METHOD =
    "appendNote($HEADER_BUILDER_CLASS$POST_MODEL)V"

@Suppress("unused")
val undeleteRedditPatch = bytecodePatch(
    name = "Automatically undelete Reddit content",
    description = "Restores the text of removed posts and comments from Project Arctic Shift, and " +
            "the name of an author whose account has since been deleted. A comment says why " +
            "it was taken down on the line under its author. Only text can be recovered, and " +
            "only where the archive has it.",
    default = false
) {
    dependsOn(sharedExtensionPatch, interceptHttpRequests)

    compatibleWith(*SyncForRedditCompatible)

    execute {
        Fingerprint(
            definingClass = EXTENSION_CLASS_DESCRIPTOR,
            name = "isPatchIncluded",
        ).method.returnEarly(true)

        // The note goes in after the separator that follows Sync's own flair. That separator
        // is where both branches of the setting governing the flair meet again, so putting the
        // note before it would leave it out for anyone who has flair turned off. The registers
        // are read off the flair call rather than assumed, and no new ones are needed: it
        // already holds the builder and the comment side by side.
        commentHeaderFingerprint.method.apply {
            val flairIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_STATIC &&
                        getReference<MethodReference>()?.parameterTypes?.toList() ==
                        listOf(HEADER_BUILDER_CLASS, "Landroid/widget/TextView;", POST_MODEL)
            }
            val flairCall = getInstruction<FiveRegisterInstruction>(flairIndex)

            addInstructions(
                flairIndex + 2,
                "invoke-static { v${flairCall.registerC}, v${flairCall.registerE} }, " +
                        "$NOTES_CLASS_DESCRIPTOR->$APPEND_NOTE_METHOD"
            )
        }
    }
}
