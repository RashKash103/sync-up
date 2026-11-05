package app.morphe.patches.reddit.customclients.sync.syncforreddit.ui.lock

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.returnEarly
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/ui/lock/AccountPin;"

private const val GUARD_METHOD =
    "guard(Landroid/content/Context;Ljava/lang/String;Ljava/lang/Runnable;)Ljava/lang/Runnable;"

private const val SETTLE_ROW_METHOD = "settle(Lpa/d;)V"

private const val SETTINGS_SCREEN = "Lpa/d;"

private const val ACTIVITY = "Landroidx/fragment/app/FragmentActivity;"

/** Where Sync asks for a fingerprint before an account is opened. */
private val accountPickerLockFingerprint = Fingerprint(
    definingClass =
        "Lcom/laurencedawson/reddit_sync/ui/fragment_dialogs/AccountPickerFragment;",
    name = "M3",
    parameters = listOf(
        ACTIVITY,
        "Landroid/view/View;",
        "Ljava/lang/String;",
        "Ljava/lang/Runnable;",
    ),
    returnType = "V",
)

/** The same, where an account is acted on rather than chosen. */
private val accountActionsLockFingerprint = Fingerprint(
    definingClass =
        "Lcom/laurencedawson/reddit_sync/ui/fragment_dialogs/bottom/ActionsBottomSheetFragment;",
    name = "s4",
    parameters = listOf(ACTIVITY, "Ljava/lang/String;", "Ljava/lang/Runnable;"),
    returnType = "V",
)

@Suppress("unused")
val pinLockPatch = bytecodePatch(
    name = "Lock an account with a PIN",
    description = "Adds a PIN to the Security settings that is asked for when an account is " +
            "opened, beside or instead of Sync's fingerprint lock.",
    default = true
) {
    dependsOn(sharedExtensionPatch, pinLockSettingsPatch)

    compatibleWith(*SyncForRedditCompatible)

    execute {
        Fingerprint(
            definingClass = EXTENSION_CLASS_DESCRIPTOR,
            name = "isPatchIncluded",
        ).method.returnEarly(true)

        // Both places hand what to do next to whatever unlocks the account, so what is handed
        // over is swapped for something that asks for the PIN first. Done in front of the first
        // instruction, which is before the branch deciding whether to ask for a fingerprint, so
        // the PIN is asked on either path: beside the fingerprint where Sync's own lock is on,
        // and on its own where it is not.
        accountPickerLockFingerprint.method.addInstructions(
            0,
            """
            invoke-static       { p0, p2, p3 }, $EXTENSION_CLASS_DESCRIPTOR->$GUARD_METHOD
            move-result-object  p3
            """
        )

        accountActionsLockFingerprint.method.addInstructions(
            0,
            """
            invoke-static       { p0, p1, p2 }, $EXTENSION_CLASS_DESCRIPTOR->$GUARD_METHOD
            move-result-object  p2
            """
        )

        // Sync's settings screens are its own fragments, so the row is wired as a screen
        // finishes loading its rows; a screen without it is left alone.
        Fingerprint(
            definingClass = SETTINGS_SCREEN,
            name = "t3",
            parameters = listOf("I"),
            returnType = "V",
        ).method.apply {
            addInstructions(
                instructions.count() - 1,
                "invoke-static { p0 }, $EXTENSION_CLASS_DESCRIPTOR->$SETTLE_ROW_METHOD",
            )
        }
    }
}
