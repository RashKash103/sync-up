package app.morphe.patches.reddit.customclients.sync.syncforreddit.redirects

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.returnEarly
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.morphe.patches.reddit.customclients.sync.syncforreddit.http.interceptHttpRequests
import app.morphe.patches.reddit.customclients.sync.syncforreddit.settings.answerTheScreenLookupWith
import app.morphe.patches.reddit.customclients.sync.syncforreddit.settings.fadeDisabledRowsPatch
import com.android.tools.smali.dexlib2.AccessFlags

private const val REDIRECTS = "Lapp/morphe/extension/syncforreddit/redirects/"

private const val SETTINGS_CLASS_DESCRIPTOR = "${REDIRECTS}RedirectSettings;"

private const val SCREEN_CLASS_DESCRIPTOR = "${REDIRECTS}RedirectsScreen;"

private const val UNBLOCK_CLASS_DESCRIPTOR = "${REDIRECTS}imgur/ImgurUnblock;"

private const val PICK_CLASS_DESCRIPTOR = "${REDIRECTS}PickAFile;"

private const val WHAT_CAME_BACK_METHOD = "whatCameBack(IILandroid/content/Intent;)V"

/**
 * Where a result from another app arrives.
 *
 * <p>Every activity in the app inherits this one, and it is the one place the picker's answer
 * can be caught: {@code onActivityResult} overrides a method of the framework's, so its name
 * survives the minifier, while the members a fragment is handed a result through do not.
 */
private val activityResultFingerprint = Fingerprint(
    definingClass = "Landroidx/activity/ComponentActivity;",
    name = "onActivityResult",
    parameters = listOf("I", "I", "Landroid/content/Intent;"),
    returnType = "V",
)

/**
 * Every class the app itself ends up calling into or building, which therefore has to be public.
 *
 * <p>A class that is not public is refused at the moment it is first reached, from whatever app
 * package the call was injected into — which for a settings screen means the screen comes down
 * rather than opens. The five screens are built by the framework when one is rotated, which is
 * the same problem arriving later and harder to find.
 */
private val reachedFromTheApp = listOf(
    SETTINGS_CLASS_DESCRIPTOR,
    SCREEN_CLASS_DESCRIPTOR,
    UNBLOCK_CLASS_DESCRIPTOR,
    PICK_CLASS_DESCRIPTOR,
    "${REDIRECTS}RedirectsScreen\$Front;",
    "${REDIRECTS}RedirectsScreen\$Imgur;",
    "${REDIRECTS}RedirectsScreen\$DuckDuckGo;",
    "${REDIRECTS}RedirectsScreen\$Rimgo;",
    "${REDIRECTS}RedirectsScreen\$Proxy;",
)

@Suppress("unused")
val redirectsPatch = bytecodePatch(
    name = "Unblock Imgur",
    description = "Loads Imgur pictures, videos and albums another way where Imgur refuses the " +
            "region, through DuckDuckGo's image proxy, a Rimgo instance or a proxy of your own. " +
            "Off until it is turned on under Settings > Redirects.",
    default = true,
) {
    dependsOn(
        sharedExtensionPatch,
        interceptHttpRequests,
        redirectsSettingsPatch,
        fadeDisabledRowsPatch,
    )

    compatibleWith(*SyncForRedditCompatible)

    execute {
        reachedFromTheApp.forEach { reached ->
            val extension = classDefByOrNull(reached)
                ?: throw PatchException("$reached is not in the app to be called")
            if (extension.accessFlags and AccessFlags.PUBLIC.value == 0) {
                throw PatchException("$reached is not public, so the app cannot reach it")
            }
        }

        // Nothing here does anything until it is turned on in the settings; this only says the
        // patch was applied, which is what keeps every route passing requests straight through
        // in a copy that was patched without it.
        listOf(SETTINGS_CLASS_DESCRIPTOR, UNBLOCK_CLASS_DESCRIPTOR).forEach { descriptor ->
            Fingerprint(definingClass = descriptor, name = "isPatchIncluded")
                .method.returnEarly(true)
        }

        answerTheScreenLookupWith(SCREEN_CLASS_DESCRIPTOR)

        // Told about every result the app is handed, and answers to one request code of its
        // own; anything else passes through it untouched. This is what lets a certificate be
        // read out of a file rather than only pasted in.
        activityResultFingerprint.method.addInstructions(
            0,
            "invoke-static { p1, p2, p3 }, $PICK_CLASS_DESCRIPTOR->$WHAT_CAME_BACK_METHOD",
        )
    }
}
