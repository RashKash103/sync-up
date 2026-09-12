package app.morphe.patches.reddit.customclients.sync.syncforreddit.ui.comments

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val CATEGORY_HEADER =
    "com.laurencedawson.reddit_sync.ui.preferences.defaults.CategoryHeaderPreference"

private const val CHECK_BOX =
    "com.laurencedawson.reddit_sync.ui.preferences.defaults.SyncCheckBoxPreference"

/**
 * Adds the setting to the comments settings. It belongs just as well beside Sync's own inline
 * linking options, but this is about comments and that is where it was looked for.
 */
internal val inlineCommentMediaSettingsPatch = resourcePatch(
    description = "Adds the inline media setting to the comments General category.",
) {
    execute {
        document("res/xml/cat_comments.xml").use { document ->
            val headers = document.getElementsByTagName(CATEGORY_HEADER)
            val category = (0 until headers.length)
                .map { headers.item(it) as Element }
                .firstOrNull { it.getAttribute("app:categoryTitle") == "General" }
                ?.parentNode as? Element
                ?: throw PatchException("No General category in comments to add the setting to")

            val preference = document.createElement(CHECK_BOX)
            mapOf(
                "android:key" to "sync_up_inline_comment_media",
                "android:title" to "Show media in comments where it sits",
                "android:summary" to
                    "Draw every picture and video linked in a comment rather than a chip",
                "android:defaultValue" to "false",
            ).forEach { (name, value) -> preference.setAttribute(name, value) }
            category.appendChild(preference)
        }
    }
}
