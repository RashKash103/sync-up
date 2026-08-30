package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.markdown

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/ui/MarkdownTracePatch;"

private const val TRACE_IN_METHOD = "traceIn(Ljava/lang/String;)V"

private const val TRACE_OUT_METHOD =
    "traceOut(Ljava/lang/String;)Ljava/lang/String;"

@Suppress("unused")
val markdownTracePatch = bytecodePatch(
    name = "Report what Sync makes of a body",
    description = "Writes to the log what a post or comment body looked like before Sync drew " +
            "it and what it turned into. For working out why an address written in one is drawn " +
            "as a link and the same address written in the other is not. Of no use unless a log " +
            "is being read, and noisy while it is.",
    default = false
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*SyncForRedditCompatible)

    execute {
        bodyProcessorFingerprint.method.apply {
            instructions.withIndex()
                .filter { (_, instruction) -> instruction.opcode == Opcode.RETURN_OBJECT }
                .map { (index, _) -> index }
                .reversed()
                .forEach { index ->
                    val result = getInstruction<OneRegisterInstruction>(index).registerA
                    addInstructions(
                        index,
                        """
                        invoke-static       { v$result }, $EXTENSION_CLASS_DESCRIPTOR->$TRACE_OUT_METHOD
                        move-result-object  v$result
                        """
                    )
                }

            // The body is the second of the two it is given, and the register holding it is put
            // to another use further down, so it is reported where it arrives.
            val text = implementation!!.registerCount - parameters.size + 1
            addInstructions(
                0,
                "invoke-static { v$text }, $EXTENSION_CLASS_DESCRIPTOR->$TRACE_IN_METHOD"
            )
        }
    }
}
