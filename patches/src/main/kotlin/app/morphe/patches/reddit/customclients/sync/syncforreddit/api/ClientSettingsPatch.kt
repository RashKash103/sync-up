package app.morphe.patches.reddit.customclients.sync.syncforreddit.api

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val PREFERENCES = "com.laurencedawson.reddit_sync.ui.preferences"

private const val CATEGORY_HEADER = "$PREFERENCES.defaults.CategoryHeaderPreference"
private const val ROW = "$PREFERENCES.defaults.SyncPreference"

/**
 * Adds the rows that let what the app identifies itself with be typed in.
 *
 * <p>They go on the Other screen, beside what the app says about itself, rather than on a
 * screen of their own: there are four of them and they are set once, if ever.
 */
internal val clientSettingsPatch = resourcePatch(
    description = "Adds the rows for the client id, redirect URI and user agent to the settings.",
) {
    execute {
        document("res/xml/cat_other.xml").use { document ->
            val screen = document.getElementsByTagName("PreferenceScreen").item(0) as? Element
                ?: throw PatchException("The Other settings screen is not a screen")

            val category = document.createElement("PreferenceCategory")

            val header = document.createElement(CATEGORY_HEADER)
            header.setAttribute("app:categoryTitle", "Sync Up client")
            category.appendChild(header)

            fun row(key: String, title: String, summary: String) {
                val preference = document.createElement(ROW)
                preference.setAttribute("android:key", key)
                preference.setAttribute("android:title", title)
                preference.setAttribute("android:summary", summary)
                category.appendChild(preference)
            }

            // The summaries are replaced as the screen loads with what is actually set; these
            // are what shows if that never runs.
            row(
                "sync_up_client_id",
                "OAuth client ID",
                "The id Reddit was asked to issue. Without one the app cannot sign in.",
            )
            row(
                "sync_up_redirect_uri",
                "Redirect URI",
                "Where Reddit sends you back to after signing in.",
            )
            row(
                "sync_up_user_agent",
                "User agent",
                "What the app calls itself when it asks Reddit for anything.",
            )
            row(
                "sync_up_imgur_client_id",
                "Imgur client ID",
                "What uploading a picture identifies as.",
            )

            // First, since signing in is what the rest of the app waits on.
            screen.insertBefore(category, screen.firstChild)
        }
    }
}
