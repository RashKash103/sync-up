package app.morphe.patches.reddit.customclients.sync.syncforreddit.ui.comments

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.morphe.patches.reddit.customclients.sync.syncforreddit.http.interceptHttpRequests
import app.morphe.util.getReference
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/ui/comments/InlineCommentMediaPatch;"

/** Reads the sizes Reddit sends beside the comments, so a picture is drawn right the first time. */
private const val SIZES_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/ui/comments/CommentMediaSizes;"

private const val SHOULD_INLINE_METHOD = "shouldInline(ZLjava/lang/String;)Z"

private const val OPEN_THE_GATE_METHOD = "orInlineEverything(Z)Z"

private const val PARSING_METHOD = "parsing()V"

private const val HERE_METHOD = "here(Z)Z"

private const val DRAWING_METHOD = "drawing(Ljava/lang/String;)V"

private const val LINK_SEEN_METHOD = "linkSeen(Ljava/lang/String;)V"

private const val GIPHY_METHOD = "giphy(Ljava/lang/String;)V"

private const val GIPHY_SPANS_METHOD = "giphySpans([Ljava/lang/Object;)[Ljava/lang/Object;"

private const val CARD_OR_PICTURE_METHOD =
    "cardOrPicture(Lnc/b;Lnc/b\$a;Ljava/lang/String;)Ljava/lang/String;"

private const val SPANS_FOR_METHOD =
    "spansFor([Ljava/lang/Object;Ljava/lang/String;Lnc/a;)[Ljava/lang/Object;"

private const val STILL_SHOW_METHOD = "stillShowTheLink(ZZ)Z"

/** Sync's own setting for writing the address out after a link it has drawn. */
private const val WRITES_THE_ADDRESS_OUT = "commentsLinksExpanded"

/**
 * What Sync asks giphy for when it draws one of Reddit's own giphy pictures: a hundred pixels
 * of it. That is a thumbnail, not the picture, which is why one drawn where it sits looked like
 * a chip beside the address rather than the gif itself.
 */
private const val A_THUMBNAIL_OF_IT = "/100.gif"

/** Big enough to read in a comment without fetching the whole of a very large gif. */
private const val ENOUGH_OF_IT = "/200.gif"

/** The view every piece of text with markup in it is drawn through. */
private const val MARKUP_TEXT_VIEW =
    "Lcom/laurencedawson/reddit_sync/ui/views/text/spannable/children/HtmlTextView;"

/** What Sync asks about where a link is, before it asks anything about the link itself. */
private const val WHERE_IT_IS = "Lnc/a;"

/** The span Sync draws a link as when it draws the picture rather than a chip naming it. */
private const val INLINE_SPAN = "Lnb/d;"

/** Sync's own setting, which only ever covered its own narrow list of addresses. */
private const val ITS_OWN_SETTING = "inlineImagePreviews"

/**
 * What Sync asks before it asks anything else about the address: whether it is an Imgur one.
 * Anything that is not, and is not already known to it, leaves here and never reaches the test
 * further down — which is why a gif hosted anywhere else was drawn as a chip however the rest of
 * it answered.
 */
private const val THE_IMGUR_TEST = "Lf8/a;"

@Suppress("unused")
val inlineCommentMediaPatch = bytecodePatch(
    name = "Show media in a comment where it sits",
    description = "Draws a link in a comment as the picture or video it points at rather than " +
            "as a chip naming where it goes.",
    default = true
) {
    dependsOn(sharedExtensionPatch, inlineCommentMediaSettingsPatch, interceptHttpRequests)

    compatibleWith(*SyncForRedditCompatible)

    execute {
        Fingerprint(
            definingClass = EXTENSION_CLASS_DESCRIPTOR,
            name = "isPatchIncluded",
        ).method.returnEarly(true)

        Fingerprint(
            definingClass = SIZES_CLASS_DESCRIPTOR,
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

            // Where the card is finally written into the text. Chosen over the test that
            // guards it because a branch further up jumps to that test rather than through it,
            // so anything put in front of it is stepped over by every link but a tenor one.
            // Where the address itself is styled. The span that draws a picture stands in
            // place of the text it covers, so putting one here is what turns the address into
            // the picture rather than leaving both.
            instructions.withIndex()
                .filter { (_, instruction) ->
                    instruction.getReference<MethodReference>()?.let {
                        it.definingClass == "Lnc/d;" && it.name == "r"
                    } == true
                }
                .map { (at, _) -> at }
                .reversed()
                .forEach { at ->
                    val spans = getInstruction<FiveRegisterInstruction>(at).registerE
                    addInstructions(
                        at,
                        """
                        invoke-static       { v$spans, v$linkRegister, p1 }, $EXTENSION_CLASS_DESCRIPTOR->$SPANS_FOR_METHOD
                        move-result-object  v$spans
                        """
                    )
                }

            // And the card underneath it, which would be the same picture again, small.
            val addsIndex = (spanIndex until instructions.count()).first { at ->
                val called = getInstruction(at).getReference<MethodReference>()
                called?.definingClass == "Lnc/b;" && called.name == "c"
            }
            val adds = getInstruction<FiveRegisterInstruction>(addsIndex)
            replaceInstruction(
                addsIndex,
                "invoke-static { v${adds.registerC}, v${adds.registerD}, v$linkRegister }, " +
                    "$EXTENSION_CLASS_DESCRIPTOR->$CARD_OR_PICTURE_METHOD"
            )

            // Where the card is written into the text. What Sync puts there is a card — a
            // small picture with the address beside it — and what goes there instead is the
            // picture itself, drawn at the width the text has.
            val cardIndex = (spanIndex until instructions.count()).first { at ->
                val called = getInstruction(at).getReference<MethodReference>()
                called?.definingClass == "Loc/c;" && called.name == "c"
            }
            val spansRegister = getInstruction<FiveRegisterInstruction>(cardIndex).registerE

            addInstructions(
                cardIndex,
                """
                invoke-static       { v$spansRegister, v$linkRegister, p1 }, $EXTENSION_CLASS_DESCRIPTOR->$SPANS_FOR_METHOD
                move-result-object  v$spansRegister
                """
            )

            // Sync writes the address out after a link when that is turned on, which would
            // stand under the picture just put in.
            val writesIndex = instructions.indexOfFirst {
                it.opcode == Opcode.IGET_BOOLEAN &&
                        it.getReference<FieldReference>()?.name == WRITES_THE_ADDRESS_OUT
            }
            if (writesIndex >= 0) {
                val writes = getInstruction<OneRegisterInstruction>(writesIndex).registerA
                addInstructions(
                    writesIndex + 1,
                    """
                    invoke-static       { v$writes, v$writes }, $EXTENSION_CLASS_DESCRIPTOR->$STILL_SHOW_METHOD
                    move-result         v$writes
                    """
                )
            }

            // The first of the two, and the one that actually turned a gif away: anything not
            // on Imgur leaves here, long before the test above is reached.
            val imgurIndex = instructions.indexOfFirst {
                it.opcode == Opcode.INVOKE_STATIC &&
                        it.getReference<MethodReference>()?.definingClass == THE_IMGUR_TEST
            }
            if (imgurIndex < 0) {
                throw PatchException("Sync no longer asks whether a link in a comment is Imgur")
            }
            val imgurResult = imgurIndex + 1
            val imgurRegister = getInstruction<OneRegisterInstruction>(imgurResult).registerA

            addInstructions(
                imgurResult + 1,
                """
                invoke-static       { v$imgurRegister, v$linkRegister }, $EXTENSION_CLASS_DESCRIPTOR->$SHOULD_INLINE_METHOD
                move-result         v$imgurRegister
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

            // Every address it is given, said before anything is decided about it, so a
            // capture shows which links reached here rather than only which were acted on.
            val hrefIndex = instructions.indexOfFirst {
                it.opcode == Opcode.INVOKE_VIRTUAL &&
                        it.getReference<MethodReference>()?.let { called ->
                            called.definingClass == "Loc/c;" && called.name == "t"
                        } == true
            }
            if (hrefIndex >= 0) {
                val href = getInstruction<OneRegisterInstruction>(hrefIndex + 1).registerA
                addInstructions(
                    hrefIndex + 2,
                    "invoke-static { v$href }, $EXTENSION_CLASS_DESCRIPTOR->$LINK_SEEN_METHOD"
                )
            }

            // Last, so the indices above are not moved by it: said for every link drawn, before
            // anything is decided, so that a capture with none of these in it means this is not
            // the code that draws the thing in question.
            addInstructions(0, "invoke-static { }, $EXTENSION_CLASS_DESCRIPTOR->$PARSING_METHOD")
        }

        // Reddit's own giphy pictures have a path of their own, which asks for a hundred
        // pixels of the gif. Said so, and asked for enough of it to be worth drawing.
        mutableClassDefBy("Lnc/d;").methods
            .filter { it.name == "a" && it.parameters.size == 2 && it.implementation != null }
            .forEach { method ->
                val at = method.implementation!!.instructions.toList().indexOfFirst {
                    it.opcode == Opcode.CONST_STRING &&
                            it.getReference<StringReference>()?.string == A_THUMBNAIL_OF_IT
                }
                if (at < 0) {
                    return@forEach
                }
                val register = method.getInstruction<OneRegisterInstruction>(at).registerA
                method.replaceInstruction(at, "const-string v$register, \"$ENOUGH_OF_IT\"")

                // Said once the address has been built out of it.
                val built = (at until method.implementation!!.instructions.count()).first { on ->
                    method.getInstruction(on).opcode == Opcode.MOVE_RESULT_OBJECT &&
                            on > at + 1
                }
                val link = method.getInstruction<OneRegisterInstruction>(built).registerA

                // Drawn as the picture rather than as a card, the same as every other one.
                val writes = method.implementation!!.instructions.toList().indexOfFirst {
                    it.getReference<MethodReference>()?.let { called ->
                        called.definingClass == "Loc/c;" && called.name == "c"
                    } == true
                }
                if (writes >= 0) {
                    val spans = method.getInstruction<FiveRegisterInstruction>(writes).registerE
                    method.addInstructions(
                        writes,
                        """
                        invoke-static       { v$spans }, $EXTENSION_CLASS_DESCRIPTOR->$GIPHY_SPANS_METHOD
                        move-result-object  v$spans
                        """
                    )
                }

                method.addInstructions(
                    built + 1,
                    "invoke-static { v$link }, $EXTENSION_CLASS_DESCRIPTOR->$GIPHY_METHOD"
                )
            }

        // A step further out again: the text view every piece of marked-up text goes through.
        // Nothing was heard from the link handling at all, and this says whether the text even
        // reaches the code that would draw a picture in it.
        mutableClassDefBy(MARKUP_TEXT_VIEW).methods
            .filter { it.name == "G" && it.parameters.size == 1 && it.implementation != null }
            .forEach { method ->
                method.addInstructions(
                    0,
                    "invoke-static { p1 }, $EXTENSION_CLASS_DESCRIPTOR->$DRAWING_METHOD"
                )
            }
    }
}
