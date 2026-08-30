package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.markdown

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/ui/MarkdownTracePatch;"

private const val TRACE_IN_METHOD = "traceIn(Ljava/lang/String;)V"

private const val TRACE_OUT_METHOD =
    "traceOut(Ljava/lang/String;)Ljava/lang/String;"

private const val APPENDED_METHOD = "appended(Ljava/lang/CharSequence;)V"

private const val APPENDED_WITH_METHOD =
    "appendedWith(Ljava/lang/String;[Ljava/lang/Object;)V"

private const val SPAN_APPLIED_METHOD = "spanApplied(Ljava/lang/Object;II)V"

private const val DRAWN_INTO_METHOD =
    "drawnInto(Ljava/lang/Object;Ljava/lang/CharSequence;)V"

@Suppress("unused")
val markdownTracePatch = bytecodePatch(
    name = "Report what Sync makes of a body",
    description = "Writes to the log what a post or comment body looked like before Sync drew " +
            "it, what it turned into, and what the words of an address were finally given to " +
            "make them tappable. For working out why an address written in one is drawn as a " +
            "link and the same address written in the other is not. Of no use unless a log is " +
            "being read, and noisy while it is.",
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

        // What the markdown produced only matters if the drawing keeps it, so the other half of
        // the report is the three ways a piece of text reaches the screen.
        appendPlainFingerprint.method.addInstructions(
            0,
            "invoke-static { p1 }, $EXTENSION_CLASS_DESCRIPTOR->$APPENDED_METHOD"
        )

        appendSpannedFingerprint.method.addInstructions(
            0,
            "invoke-static { p1, p2 }, $EXTENSION_CLASS_DESCRIPTOR->$APPENDED_WITH_METHOD"
        )

        applySpanFingerprint.method.addInstructions(
            0,
            "invoke-static { p1, p2, p3 }, $EXTENSION_CLASS_DESCRIPTOR->$SPAN_APPLIED_METHOD"
        )

        // Reported where the built text is taken off the builder rather than at the end of the
        // method: a view that has its measuring worked out ahead of time jumps straight to the
        // return, so nothing put in front of that is reached on the way there.
        handToViewFingerprint.method.apply {
            val built = indexOfFirstInstructionOrThrow {
                val reference = getReference<MethodReference>()
                reference?.name == "d" && reference.returnType == "Ljava/lang/CharSequence;"
            }
            val text = getInstruction<OneRegisterInstruction>(built + 1).registerA
            addInstructions(
                built + 2,
                "invoke-static { p0, v$text }, $EXTENSION_CLASS_DESCRIPTOR->$DRAWN_INTO_METHOD"
            )
        }
    }
}
