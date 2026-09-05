package app.morphe.patches.reddit.customclients.sync.syncforreddit.ui.gestures

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch

private const val TRACE_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/ui/gestures/GestureTrace;"

/** The interface Sync answers a tap on the viewer through. */
private const val TAP_LISTENER = "Lbc/d;"

private const val VIEWER = "Lcom/laurencedawson/reddit_sync/ui/fragments/ImageViewerFragment"

/**
 * The two answers to a tap on the viewer. Each turns the chrome on where it is off and off where
 * it is on, which is the flashing, and neither is reached by anything the gestures consume.
 */
private fun togglesTheChrome(type: String) = Fingerprint(
    parameters = emptyList(),
    returnType = "V",
    custom = { method, classDef ->
        classDef.type == type && TAP_LISTENER in classDef.interfaces && method.name == "a"
    },
)

private val firstChromeToggleFingerprint = togglesTheChrome("$VIEWER\$p;")
private val secondChromeToggleFingerprint = togglesTheChrome("$VIEWER\$b0;")

/**
 * Reports who turns the chrome of the viewer on and off. A stack trace from inside the toggle
 * names the path a tap takes to reach it, which nothing read from the outside has managed.
 */
internal val traceChromePatch = bytecodePatch(
    description = "Reports what turns the viewer's chrome on and off.",
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*SyncForRedditCompatible)

    execute {
        listOf(firstChromeToggleFingerprint, secondChromeToggleFingerprint).forEach { toggle ->
            toggle.method.addInstructions(
                0,
                "invoke-static { }, $TRACE_CLASS_DESCRIPTOR->chromeToggled()V"
            )
        }
    }
}
