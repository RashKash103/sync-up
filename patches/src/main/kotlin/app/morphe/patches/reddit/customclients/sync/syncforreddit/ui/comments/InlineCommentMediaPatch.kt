package app.morphe.patches.reddit.customclients.sync.syncforreddit.ui.comments

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.morphe.util.getReference
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/ui/comments/InlineCommentMediaPatch;"

private const val SHOULD_INLINE_METHOD = "shouldInline(ZLjava/lang/String;)Z"

private const val OPEN_THE_GATE_METHOD = "orInlineEverything(Z)Z"

private const val PARSING_METHOD = "parsing()V"

private const val HERE_METHOD = "here(Z)Z"

/** What Sync asks about where a link is, before it asks anything about the link itself. */
private const val WHERE_IT_IS = "Lnc/a;"

/** The span Sync draws a link as when it draws the picture rather than a chip naming it. */
private const val INLINE_SPAN = "Lnb/d;"

/** Sync's own setting, which only ever covered its own narrow list of addresses. */
private const val ITS_OWN_SETTING = "inlineImagePreviews"

@Suppress("unused")
val inlineCommentMediaPatch = bytecodePatch(
    name = "Show media in a comment where it sits",
    description = "Draws a link in a comment as the picture or video it points at rather than " +
            "as a chip naming where it goes.",
    default = true
) {
    dependsOn(sharedExtensionPatch, inlineCommentMediaSettingsPatch)

    compatibleWith(*SyncForRedditCompatible)

    execute {
        Fingerprint(
            definingClass = EXTENSION_CLASS_DESCRIPTOR,
            name = "isPatchIncluded",
        ).method.returnEarly(true)

        // Where a link in a comment is turned into either a drawn picture or a chip. Anchored on
        // the span itself, since the decision around it is a long run of unnamed tests.
        Fingerprint(
            definingClass = "Lnc/d;",
            name = "e",
            parameters = listOf("Loc/c;", "Lnc/a;", "Lnc/b;"),
            returnType = "V",
        ).method.apply {
            val spanIndex = instructions.indexOfFirst {
                it.opcode == Opcode.NEW_INSTANCE &&
                        it.getReference<TypeReference>()?.type == INLINE_SPAN
            }
            if (spanIndex < 0) {
                throw PatchException("Nothing in a comment is drawn where it sits")
            }

            // The address is whatever the span is built out of, rather than a register guessed
            // at: the method reuses its own registers throughout.
            val builtIndex = (spanIndex until instructions.size).first { at ->
                val reference = getInstruction(at).getReference<com.android.tools.smali.dexlib2.iface.reference.MethodReference>()
                reference?.definingClass == INLINE_SPAN && reference.name == "<init>"
            }
            val linkRegister = getInstruction<FiveRegisterInstruction>(builtIndex).registerD

            // What guards the span is the last thing asked before it is built.
            val guardIndex = (spanIndex - 1 downTo 0).first {
                instructions.elementAt(it).opcode == Opcode.IF_EQZ
            }
            val decisionRegister = getInstruction<OneRegisterInstruction>(guardIndex).registerA

            addInstructions(
                guardIndex,
                """
                invoke-static       { v$decisionRegister, v$linkRegister }, $EXTENSION_CLASS_DESCRIPTOR->$SHOULD_INLINE_METHOD
                move-result         v$decisionRegister
                """
            )

            // Sync asks its own setting first and gives up where it is off, so that has to be
            // answered too or the widened test above is never reached.
            val settingIndex = instructions.indexOfFirst {
                it.opcode == Opcode.IGET_BOOLEAN &&
                        it.getReference<FieldReference>()?.name == ITS_OWN_SETTING
            }
            if (settingIndex < 0) {
                throw PatchException("Sync no longer asks whether to draw a link in a comment")
            }
            val settingRegister = getInstruction<OneRegisterInstruction>(settingIndex).registerA

            addInstructions(
                settingIndex + 1,
                """
                invoke-static       { v$settingRegister }, $EXTENSION_CLASS_DESCRIPTOR->$OPEN_THE_GATE_METHOD
                move-result         v$settingRegister
                """
            )

            // Asked before either of those: something about where the link is, which is not
            // named in the app. Answered so that a capture says whether it is what stops a
            // picture being drawn where it sits, and opened where it is.
            val whereIndex = (settingIndex - 1 downTo 0).firstOrNull { at ->
                val instruction = instructions.elementAt(at)
                instruction.opcode == Opcode.IGET_BOOLEAN &&
                        instruction.getReference<FieldReference>()?.definingClass == WHERE_IT_IS
            }
            if (whereIndex != null) {
                val whereRegister = getInstruction<OneRegisterInstruction>(whereIndex).registerA
                addInstructions(
                    whereIndex + 1,
                    """
                    invoke-static       { v$whereRegister }, $EXTENSION_CLASS_DESCRIPTOR->$HERE_METHOD
                    move-result         v$whereRegister
                    """
                )
            }

            // Last, so the indices above are not moved by it: said for every link drawn, before
            // anything is decided, so that a capture with none of these in it means this is not
            // the code that draws the thing in question.
            addInstructions(0, "invoke-static { }, $EXTENSION_CLASS_DESCRIPTOR->$PARSING_METHOD")
        }
    }
}
