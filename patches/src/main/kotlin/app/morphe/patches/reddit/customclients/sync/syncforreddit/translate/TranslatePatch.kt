package app.morphe.patches.reddit.customclients.sync.syncforreddit.translate

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.iface.instruction.Instruction

private const val WRAPPER_CLASS =
    "Lcom/laurencedawson/reddit_sync/ui/fragments/preferences/PreferenceWrapperFragment;"

private const val SCREEN_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/translate/TranslationScreen;"

private const val SCREEN_FOR_METHOD = "screenFor(I)Lpa/d;"

private const val TITLE_FOR_METHOD = "titleFor(I)Ljava/lang/String;"

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
    dependsOn(sharedExtensionPatch, translationSettingsPatch)

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
    }
}
