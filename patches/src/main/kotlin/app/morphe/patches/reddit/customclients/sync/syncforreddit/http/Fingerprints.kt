package app.morphe.patches.reddit.customclients.sync.syncforreddit.http

import app.morphe.patcher.Fingerprint

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
