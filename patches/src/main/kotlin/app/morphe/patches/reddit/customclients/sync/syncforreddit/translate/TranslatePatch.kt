package app.morphe.patches.reddit.customclients.sync.syncforreddit.translate

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.morphe.patches.reddit.customclients.sync.syncforreddit.ui.notes.notesInTheHeaderPatch
import com.android.tools.smali.dexlib2.iface.instruction.Instruction

private const val WRAPPER_CLASS =
    "Lcom/laurencedawson/reddit_sync/ui/fragments/preferences/PreferenceWrapperFragment;"

private const val SCREEN_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/translate/TranslationScreen;"

private const val SCREEN_FOR_METHOD = "screenFor(I)Lpa/d;"

private const val TITLE_FOR_METHOD = "titleFor(I)Ljava/lang/String;"

private const val NOW_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/translate/TranslateNow;"

private const val INSTEAD_METHOD =
    "instead(Lda/d;Ljava/lang/String;Ljava/lang/String;)V"

private const val SHEET_CLASS_DESCRIPTOR = "Lda/d;"

private const val IN_PLACE_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/translate/InPlace;"

private const val INSTEAD_OF_COMPOSING_METHOD =
    "instead(Lxa/d;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"

/** What Sync writes at the end of a translation, and draws as a picture from a dead domain. */
private const val NAMES_THE_SERVICE = "[Translated by Google](translate)"

/** Where what is stored after a translation is composed, once for a post and once for a comment. */
private val composesWhatIsStoredFingerprint = Fingerprint(
    parameters = listOf("Lxa/d;", "Ljava/lang/String;", "Ljava/lang/String;"),
    returnType = "Ljava/lang/String;",
    strings = listOf(NAMES_THE_SERVICE),
)

/**
 * The two ways back into the sheet. They are accessors the compiler wrote so that the sheet
 * could reach its own private methods from a callback, which leaves them open to the package
 * and to nothing else. The extension is not in that package, so they are opened here.
 */
private val WAYS_BACK_IN = setOf("x4", "v4")

private const val SETTINGS_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/translate/TranslationSettings;"

private const val OFFERED_METHOD = "offered()Z"

/** What Sync calls the row, naming a service that is no longer necessarily the one used. */
private const val TRANSLATE_ROW = "Translate comment with Google"

private const val TRANSLATE_ROW_INSTEAD = "Translate comment"

/** Whether the feature has been turned on from afar, which nothing here waits on. */
private val turnedOnFromAfarFingerprint = Fingerprint(
    parameters = emptyList(),
    returnType = "Z",
    strings = listOf("ultra_translate"),
)

private const val ROWS_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/translate/SettingsRows;"

private const val DRAW_ENABLED_METHOD =
    "drawEnabled(Landroidx/preference/Preference;Landroidx/preference/h;)V"

/**
 * Where Sync asks for a translation. It has worked out the text and what it is written in by
 * this point, and what comes back goes on to be written where the app reads its text from, so
 * this is the one step to answer differently.
 */
private val asksForATranslationFingerprint = Fingerprint(
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
    returnType = "V",
    custom = { method, classDef ->
        classDef.type == "Lda/d;" && method.indexOfFirstInstruction {
            getReference<MethodReference>()
                ?.definingClass
                ?.startsWith("Lcom/google/mlkit/nl/translate/") == true
        } >= 0
    },
)

/** The kinds of row Sync draws itself, each of which sets its own colours as it does. */
private val rowKinds = listOf(
    "SyncPreference;",
    "SyncListPreference;",
    "SyncCheckBoxPreference;",
)

/** Where a row of a given kind draws itself. */
private fun drawsRow(kind: String) = Fingerprint(
    parameters = listOf("Landroidx/preference/h;"),
    returnType = "V",
    custom = { method, classDef ->
        classDef.type.endsWith(kind) && method.name == "S"
    },
)

/** What every screen of the settings is looked up in, and what it says when it does not know. */
private const val UNKNOWN_SCREEN = "Unsupported preference fragment."

/**
 * Sync's own list of which fragment belongs to which screen of the settings. It answers with a
 * fragment for every id it knows and throws for every other, so a screen of ours has to be
 * answered before it gets there.
 */
private val screenForFingerprint = Fingerprint(
    parameters = listOf("I"),
    returnType = "Landroidx/fragment/app/Fragment;",
    strings = listOf(UNKNOWN_SCREEN),
)

/** The same list again, for the name shown at the top of a screen, and it throws the same way. */
private val titleForFingerprint = Fingerprint(
    parameters = emptyList(),
    returnType = "Ljava/lang/String;",
    strings = listOf(UNKNOWN_SCREEN),
)

/** The id of the screen being shown, which the name is looked up by. */
private val screenIdFingerprint = Fingerprint(
    parameters = emptyList(),
    returnType = "I",
    custom = { method, classDef ->
        classDef.type.endsWith("preferences/PreferenceWrapperFragment;") &&
                method.implementation?.instructions?.count() == 2
    },
)

@Suppress("unused")
val translatePatch = bytecodePatch(
    name = "Translate posts and comments",
    description = "Translates a post or comment where it sits, on this device or through DeepL " +
            "or Google Cloud. Everything about it is set up under Translation in Sync's settings.",
    default = true
) {
    dependsOn(sharedExtensionPatch, translationSettingsPatch, notesInTheHeaderPatch)

    compatibleWith(*SyncForRedditCompatible)

    execute {
        screenForFingerprint.method.apply {
            // Answered before Sync looks, and only for the one id that is ours: for every other
            // the screen is null and Sync goes on to look the id up as it always did.
            addInstructionsWithLabels(
                0,
                """
                invoke-static       { p1 }, $SCREEN_CLASS_DESCRIPTOR->$SCREEN_FOR_METHOD
                move-result-object  v0
                if-eqz              v0, :not_ours
                return-object       v0
                """,
                ExternalLabel("not_ours", getInstruction<Instruction>(0)),
            )
        }

        // The name at the top of it, looked up in the same list and throwing the same way. The
        // id is not passed here, so it is asked of the screen itself.
        val screenId = screenIdFingerprint.originalMethod.name

        titleForFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                invoke-virtual      { p0 }, $WRAPPER_CLASS->$screenId()I
                move-result         v0
                invoke-static       { v0 }, $SCREEN_CLASS_DESCRIPTOR->$TITLE_FOR_METHOD
                move-result-object  v0
                if-eqz              v0, :not_ours
                return-object       v0
                """,
                ExternalLabel("not_ours", getInstruction<Instruction>(0)),
            )
        }

        // Asked of the service that was chosen, into the language that was chosen, and only
        // once for the same text. What comes back goes back the way it always did.
        asksForATranslationFingerprint.method.apply {
            addInstructions(
                0,
                """
                invoke-static       { p0, p1, p2 }, $NOW_CLASS_DESCRIPTOR->$INSTEAD_METHOD
                return-void
                """
            )
        }

        mutableClassDefBy(SHEET_CLASS_DESCRIPTOR).methods
            .filter { it.name in WAYS_BACK_IN }
            .also {
                if (it.size != WAYS_BACK_IN.size) {
                    throw PatchException("The sheet no longer has both ways back into it")
                }
            }
            .forEach { wayBackIn ->
                wayBackIn.accessFlags = wayBackIn.accessFlags and
                        (AccessFlags.PRIVATE.value or AccessFlags.PROTECTED.value).inv() or
                        AccessFlags.PUBLIC.value
            }

        // What is stored after a translation is composed twice over, once for a post and once
        // for a comment, from the same three things and to the same shape. Both are replaced:
        // only the translation is stored now, and what was written is kept aside instead of
        // being left above it under a rule.
        val composes = composesWhatIsStoredFingerprint.originalClassDef
        var replaced = 0

        mutableClassDefBy(composes.type).methods.forEach { composing ->
            val says = composing.implementation?.instructions?.any {
                it.getReference<StringReference>()?.string == NAMES_THE_SERVICE
            } ?: false
            if (!says) return@forEach

            composing.addInstructions(
                0,
                """
                invoke-static { p0, p1, p2 }, $IN_PLACE_CLASS_DESCRIPTOR->$INSTEAD_OF_COMPOSING_METHOD
                move-result-object p2
                return-object p2
                """
            )
            replaced++
        }

        if (replaced != 2) {
            throw PatchException("Expected a post and a comment to be composed, found $replaced")
        }

        // Whether translation is offered is ours to answer. Sync asks two things before it
        // offers it, one after the other: whether the copy is paid for, and whether the feature
        // was turned on from afar. Neither bears on translating on the device or with one's own
        // key, so both are answered from the setting instead. The pair is only replaced where
        // the two questions sit together, which is the two menus that offer translating; the
        // paid-copy question is asked all over the app and is left alone everywhere else.
        val offered = "invoke-static { }, $SETTINGS_CLASS_DESCRIPTOR->$OFFERED_METHOD"

        val turnedOnFromAfar = turnedOnFromAfarFingerprint.originalMethod
        val askedFromAfar = turnedOnFromAfar.name
        val askedFromAfarBy = turnedOnFromAfar.definingClass

        val gates = mutableListOf<Triple<ClassDef, Method, Pair<Int, Int>>>()

        classDefForEach { candidate ->
            if (candidate.type == askedFromAfarBy) return@classDefForEach

            candidate.methods.forEach eachMethod@{ method ->
                val instructions =
                    method.implementation?.instructions?.toList() ?: return@eachMethod

                val fromAfar = instructions.indexOfFirst {
                    val called = it.getReference<MethodReference>() ?: return@indexOfFirst false
                    called.name == askedFromAfar && called.definingClass == askedFromAfarBy
                }
                if (fromAfar < 0) return@eachMethod

                // The paid-copy question, asked a few instructions earlier and branched on.
                val paidFor = (maxOf(0, fromAfar - 4) until fromAfar).lastOrNull { at ->
                    val called = instructions[at].getReference<MethodReference>()
                    instructions[at].opcode == Opcode.INVOKE_STATIC &&
                            called != null &&
                            called.returnType == "Z" &&
                            called.parameterTypes.isEmpty() &&
                            called.definingClass != askedFromAfarBy
                } ?: return@eachMethod

                gates += Triple(candidate, method, paidFor to fromAfar)
            }
        }

        gates.forEach { (candidate, method, asked) ->
            mutableClassDefBy(candidate).methods.first {
                it.name == method.name &&
                        it.parameters.map { parameter -> parameter.type } ==
                        method.parameters.map { parameter -> parameter.type }
            }.apply {
                replaceInstruction(asked.first, offered)
                replaceInstruction(asked.second, offered)

                // Sync names the service it uses in the row it draws, and it is no longer the
                // one doing the work.
                implementation!!.instructions.toList().forEachIndexed { at, instruction ->
                    if (instruction.opcode != Opcode.CONST_STRING) return@forEachIndexed
                    val said = instruction.getReference<StringReference>()?.string
                    if (said != TRANSLATE_ROW) return@forEachIndexed

                    val register = (instruction as OneRegisterInstruction).registerA
                    replaceInstruction(at, "const-string v$register, \"$TRANSLATE_ROW_INSTEAD\"")
                }
            }
        }

        // A row that cannot be used should look like it. Sync sets the colour of every row's
        // words as it draws them, whatever state the row is in, so the whole row is faded
        // instead, once for every kind of row there is.
        //
        // Said before the row draws itself rather than after: by the end of drawing, the
        // register the holder arrived in has been put to another use and holds a view, and
        // nothing in the drawing touches what is set here.
        rowKinds.forEach { kind ->
            drawsRow(kind).method.addInstructions(
                0,
                "invoke-static { p0, p1 }, $ROWS_CLASS_DESCRIPTOR->$DRAW_ENABLED_METHOD"
            )
        }
    }
}
