/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.sync.syncforreddit.api

import app.morphe.patcher.StringComparisonType
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import app.morphe.patches.all.misc.string.replaceStringPatch
import app.morphe.patches.reddit.customclients.spoofClientPatch
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.patches.reddit.customclients.sync.detection.piracy.disablePiracyDetectionPatch
import app.morphe.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.morphe.patcher.util.returnEarly
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod

/**
 * What Sync identifies itself to Imgur as when uploading a picture. Its own is shared by every
 * copy of the app and has spent its daily allowance — Imgur answers an upload with it
 * `x-ratelimit-clientremaining: 0` — so uploading works only with one of your own.
 */
private const val SYNCS_OWN_IMGUR_CLIENT_ID = "981c2f80e996ca3"

/** What Imgur issues: an even number of hex digits, and not many of them. */
private val LOOKS_LIKE_A_CLIENT_ID = Regex("^[0-9a-fA-F]{15,16}$")

/** How the Client-ID is written in the header Sync sends. */
private const val CLIENT_ID_HEADER = "Client-ID "

private const val CLIENT_SETTINGS =
    "Lapp/morphe/extension/syncforreddit/api/ClientSettings;"

private const val CLIENT_ROWS =
    "Lapp/morphe/extension/syncforreddit/api/ClientRows;"

private const val SETTINGS_SCREEN = "Lpa/d;"

private const val SETTLE_ROWS_METHOD = "settle(Lpa/d;)V"

/**
 * Makes a method answer with what the extension says, rather than with what was written in
 * while patching. The four things the app identifies itself with can be set in the settings
 * now, so what they are is not known until the app asks.
 */
private fun MutableMethod.answerWith(method: String) {
    val free = implementation!!.registerCount - parameters.size - if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1
    if (free < 1) {
        throw PatchException("No register free to answer $name with $method")
    }
    addInstructions(
        0,
        """
        invoke-static       { }, $CLIENT_SETTINGS->$method
        move-result-object  v0
        return-object       v0
        """
    )
}

val spoofClientPatch = spoofClientPatch { clientId, redirectUri, userAgent ->
    val imgurClientId = stringOption(
        "imgur-client-id",
        SYNCS_OWN_IMGUR_CLIENT_ID,
        null,
        "Imgur client ID",
        "What to identify as when uploading a picture to Imgur. Sync's own has run out of its " +
                "daily allowance, so uploading fails until this is one of your own; refer to Sync " +
                "Up documentation for how to get one.",
        false,
        validator = { value -> value != null && LOOKS_LIKE_A_CLIENT_ID.matches(value) }
    )

    dependsOn(
        disablePiracyDetectionPatch,
        clientSettingsPatch,
        sharedExtensionPatch,
        // Redirects from SSL to WWW domain are bugged causing auth problems.
        // Manually rewrite the URLs to fix this.
        replaceStringPatch("ssl.reddit.com", "www.reddit.com", comparison = StringComparisonType.CONTAINS)
    )

    compatibleWith(*SyncForRedditCompatible)

    execute {
        // region What was given while patching, which is what these answer with until they are
        // set in the settings instead.

        fun bake(named: String, value: String) = Fingerprint(
            definingClass = CLIENT_SETTINGS,
            name = named,
        ).method.returnEarly(value)

        bake("bakedClientId", clientId.value ?: "")
        bake("bakedRedirectUri", redirectUri.value ?: "")
        bake("bakedUserAgent", userAgent.value ?: "")
        bake("bakedImgurClientId", imgurClientId.value ?: "")

        // The address the app holds, which the client id in use is written into at the time of
        // asking. Taken from the app rather than written out here, so that the rest of it stays
        // whatever the app meant it to be.
        val authorizationUrl = getAuthorizationStringFingerprint.method.getInstruction<
                ReferenceInstruction>(
            getAuthorizationStringFingerprint.stringMatches.first().index,
        ).let { (it.reference as StringReference).string }
        bake("bakedAuthorizationUrl", authorizationUrl)

        // endregion

        // region Ask the extension rather than answering with what was written in.

        getBearerTokenFingerprint.method.answerWith("basicAuth()Ljava/lang/String;")
        getRedirectUriFingerprint.method.answerWith("redirectUri()Ljava/lang/String;")
        getUserAgentFingerprint.method.answerWith("userAgent()Ljava/lang/String;")

        getAuthorizationStringFingerprint.method.apply {
            val at = getAuthorizationStringFingerprint.stringMatches.first().index
            val register = getInstruction<OneRegisterInstruction>(at).registerA

            addInstructions(
                at,
                """
                invoke-static       { }, $CLIENT_SETTINGS->authorizationUrl()Ljava/lang/String;
                move-result-object  v$register
                """
            )
            // The address it used to hold, now two instructions further on.
            removeInstruction(at + 2)
        }

        // endregion

        // region Patch Imgur API URL.

        imgurImageAPIFingerprint.let {
            val apiUrlIndex = it.stringMatches.first().index
            it.method.replaceInstruction(
                apiUrlIndex,
                "const-string v1, \"https://api.imgur.com/3/image\"",
            )
        }

        // endregion

        // region Patch the Imgur client id.

        // Sending the upload to Imgur rather than to the proxy above only gets as far as the
        // Client-ID it is sent with, and Sync's is spent.
        imgurUploadHeadersFingerprint.method.apply {
            val at = instructions.indexOfFirst {
                it.opcode == Opcode.CONST_STRING &&
                        it.getReference<StringReference>()?.string?.startsWith(CLIENT_ID_HEADER) == true
            }
            if (at < 0) {
                throw PatchException("The Imgur upload does not name a client id")
            }

            val register = getInstruction<OneRegisterInstruction>(at).registerA
            addInstructions(
                at,
                """
                invoke-static       { }, $CLIENT_SETTINGS->imgurHeader()Ljava/lang/String;
                move-result-object  v$register
                """
            )
            // The header it used to hold, now two instructions further on.
            removeInstruction(at + 2)
        }

        // endregion

        // region The rows that let these be typed in.

        Fingerprint(
            definingClass = CLIENT_ROWS,
            name = "isPatchIncluded",
        ).method.returnEarly(true)

        // Sync's settings screens are its own fragments, so there is nowhere of ours to run
        // from. Done as a screen finishes loading its rows, which every screen passes through;
        // a screen without these rows is left alone.
        Fingerprint(
            definingClass = SETTINGS_SCREEN,
            name = "t3",
            parameters = listOf("I"),
            returnType = "V",
        ).method.apply {
            addInstructions(
                instructions.count() - 1,
                "invoke-static { p0 }, $CLIENT_ROWS->$SETTLE_ROWS_METHOD",
            )
        }

        // endregion
    }
}
