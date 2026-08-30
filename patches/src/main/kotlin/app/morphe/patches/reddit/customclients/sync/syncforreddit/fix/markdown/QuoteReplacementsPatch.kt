package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.markdown

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val QUOTE_REPLACEMENT =
    "Ljava/util/regex/Matcher;->quoteReplacement(Ljava/lang/String;)Ljava/lang/String;"

@Suppress("unused")
val quoteReplacementsPatch = bytecodePatch(
    name = "Load threads whose text contains a dollar sign",
    description = "Stops a thread failing to load because of what someone wrote in it. Sync " +
            "rewrites code blocks and links before drawing them, and puts the text it matched " +
            "back in as a replacement, where a dollar sign does not stand for itself: text such " +
            "as \"\${SYS_USER}\" is read as naming part of the pattern, and finding no such part " +
            "the whole thread is abandoned with \"Error loading page\". Taken as the characters " +
            "it is written with, as Sync already does when rewriting a comment's code, the " +
            "thread loads.",
    default = true
) {
    compatibleWith(*SyncForRedditCompatible)

    execute {
        listOf(codeBlockFingerprint, markdownLinkFingerprint).forEach { fingerprint ->
            fingerprint.method.apply {
                // Every place matched text is handed back as a replacement, of which the link
                // rewriting has several.
                val replacements = instructions.withIndex().filter { (_, instruction) ->
                    instruction.getReference<MethodReference>()?.name == "appendReplacement"
                }.map { (index, _) -> index }

                replacements.reversed().forEach { index ->
                    val register = getInstruction<FiveRegisterInstruction>(index).registerE

                    addInstructions(
                        index,
                        """
                        invoke-static       { v$register }, $QUOTE_REPLACEMENT
                        move-result-object  v$register
                        """
                    )
                }
            }
        }
    }
}
