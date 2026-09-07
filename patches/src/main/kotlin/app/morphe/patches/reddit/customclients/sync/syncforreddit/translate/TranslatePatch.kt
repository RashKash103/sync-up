package app.morphe.patches.reddit.customclients.sync.syncforreddit.translate

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch

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
        // The settings screen comes from the patch this depends on. What reads those settings,
        // and what does the translating, is added here as each part lands.
    }
}
