package app.morphe.patches.reddit.customclients.sync.syncforreddit.http

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Sync bundles a modified Volley whose BasicNetwork issues okhttp3 calls directly, so every
 * Volley request goes through the client this method obtains. Volley is not obfuscated, which
 * makes it a more stable anchor than the client builders in OkHttpHelper, several of which are
 * near identical and only tell apart by register allocation.
 */
internal val volleyPerformRequestFingerprint = Fingerprint(
    definingClass = "Lcom/android/volley/toolbox/BasicNetwork;",
    name = "performRequest",
)

/**
 * Glide does not share Sync's Volley client. Its loader builds a plain OkHttpClient of its own
 * and keeps it in a static field, so image loads are only visible if that one is hooked too.
 * Matched on the source file, since the class itself is obfuscated.
 */
internal val glideClientFactoryFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.STATIC),
    returnType = "Lokhttp3/Call${'$'}Factory;",
    parameters = listOf(),
    custom = { _, classDef -> classDef.sourceFile == "OkHttpUrlLoader.java" }
)
