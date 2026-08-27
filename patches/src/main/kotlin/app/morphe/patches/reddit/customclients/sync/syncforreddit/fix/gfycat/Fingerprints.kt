package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.gfycat

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Sync bundles a modified Volley whose BasicNetwork issues okhttp3 calls directly, so every
 * Volley request, Gfycat's included, goes through the client this method obtains. Volley is
 * not obfuscated, which makes this a more stable anchor than the client builders in
 * OkHttpHelper, several of which are near identical.
 */
internal val volleyPerformRequestFingerprint = Fingerprint(
    definingClass = "Lcom/android/volley/toolbox/BasicNetwork;",
    name = "performRequest",
)

internal val getUserAgentFingerprint = Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getUserAgent",
)

internal val getOriginalUserAgentFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    custom = { _, classDef -> classDef.sourceFile == "AccountSingleton.java" }
)
