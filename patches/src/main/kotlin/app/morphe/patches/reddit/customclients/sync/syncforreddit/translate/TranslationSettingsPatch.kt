package app.morphe.patches.reddit.customclients.sync.syncforreddit.translate

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val PREFERENCES = "com.laurencedawson.reddit_sync.ui.preferences"

private const val CATEGORY = "$PREFERENCES.defaults.CategoryHeaderPreference"
private const val CHECK_BOX = "$PREFERENCES.defaults.SyncCheckBoxPreference"
private const val LIST = "$PREFERENCES.defaults.SyncListPreference"
private const val ROW = "$PREFERENCES.defaults.SyncPreference"

/**
 * Puts the translation settings among Sync's own, built from the same preferences Sync builds
 * its own screens from so that they are drawn by the app rather than beside it: the same rows,
 * the same headings, the same theme.
 */
internal val translationSettingsPatch = resourcePatch(
    description = "Adds a translation screen to Sync's settings.",
) {
    execute {
        document("res/values/arrays.xml").use { document ->
            val resources = document.getElementsByTagName("resources").item(0) as Element

            fun array(name: String, vararg items: String) {
                val array = document.createElement("string-array")
                array.setAttribute("name", name)
                items.forEach {
                    val item = document.createElement("item")
                    item.appendChild(document.createTextNode(it))
                    array.appendChild(item)
                }
                resources.appendChild(array)
            }

            array("sync_up_translate_services", "On this device", "DeepL", "Google Cloud")
            array("sync_up_translate_service_values", "device", "deepl", "google")

            array(
                "sync_up_translate_languages",
                "English (US)", "English (UK)", "Arabic", "Bulgarian", "Chinese (simplified)",
                "Czech", "Danish", "Dutch", "Estonian", "Finnish", "French", "German", "Greek",
                "Hungarian", "Indonesian", "Italian", "Japanese", "Korean", "Latvian",
                "Lithuanian", "Norwegian", "Polish", "Portuguese (Brazil)", "Portuguese",
                "Romanian", "Russian", "Slovak", "Slovenian", "Spanish", "Swedish", "Turkish",
                "Ukrainian",
            )
            array(
                "sync_up_translate_language_values",
                "en-US", "en-GB", "ar", "bg", "zh", "cs", "da", "nl", "et", "fi", "fr", "de",
                "el", "hu", "id", "it", "ja", "ko", "lv", "lt", "nb", "pl", "pt-BR", "pt-PT",
                "ro", "ru", "sk", "sl", "es", "sv", "tr", "uk",
            )

            array(
                "sync_up_translate_context",
                "The post it is under", "The post and the comments above it", "Nothing",
            )
            array("sync_up_translate_context_values", "post", "thread", "none")

            array(
                "sync_up_translate_keep",
                "A day", "A week", "A month", "Three months", "Forever",
            )
            array("sync_up_translate_keep_values", "1", "7", "30", "90", "0")
        }

        // Added to a screen Sync already has rather than given one of its own. A screen of
        // its own is reached by an id that Sync maps to a fragment of its own, and nothing that
        // is not in that map can be reached at all; the row for one only crashed the settings.
        document("res/xml/cat_general.xml").use { document ->
            val headers = document.getElementsByTagName(CATEGORY)
            val screen = (0 until headers.length)
                .map { headers.item(it) as Element }
                .firstOrNull()
                ?.parentNode?.parentNode as? Element
                ?: throw PatchException("No screen to add the translation settings to")

            fun category(title: String): Element {
                val category = document.createElement("PreferenceCategory")
                val heading = document.createElement(CATEGORY)
                heading.setAttribute("app:categoryTitle", title)
                category.appendChild(heading)
                screen.appendChild(category)
                return category
            }

            fun Element.row(tag: String, attributes: Map<String, String>) {
                val preference = document.createElement(tag)
                attributes.forEach { (name, value) -> preference.setAttribute(name, value) }
                appendChild(preference)
            }

            category("Translation").apply {
                row(
                    CHECK_BOX,
                    mapOf(
                        "android:key" to "sync_up_translate",
                        "android:title" to "Translate posts and comments",
                        "android:summary" to "Adds translation to the post and comment menus",
                        "android:defaultValue" to "true",
                    ),
                )
                row(
                    LIST,
                    mapOf(
                        "android:key" to "sync_up_translate_service",
                        "android:title" to "Translate with",
                        "android:entries" to "@array/sync_up_translate_services",
                        "android:entryValues" to "@array/sync_up_translate_service_values",
                        "android:defaultValue" to "device",
                    ),
                )
                row(
                    LIST,
                    mapOf(
                        "android:key" to "sync_up_translate_language",
                        "android:title" to "Translate into",
                        "android:entries" to "@array/sync_up_translate_languages",
                        "android:entryValues" to "@array/sync_up_translate_language_values",
                        "android:defaultValue" to "en-US",
                    ),
                )
                row(
                    ROW,
                    mapOf(
                        "android:key" to "sync_up_translate_deepl_key",
                        "android:title" to "DeepL API key",
                        "android:summary" to "Not set",
                    ),
                )
                row(
                    ROW,
                    mapOf(
                        "android:key" to "sync_up_translate_deepl_usage",
                        "android:title" to "DeepL usage",
                        "android:summary" to "Tap to check",
                    ),
                )
                row(
                    LIST,
                    mapOf(
                        "android:key" to "sync_up_translate_deepl_context",
                        "android:title" to "Send along with a comment",
                        "android:summary" to
                            "What a comment is translated alongside, so that it reads in context",
                        "android:entries" to "@array/sync_up_translate_context",
                        "android:entryValues" to "@array/sync_up_translate_context_values",
                        "android:defaultValue" to "thread",
                    ),
                )
                row(
                    CHECK_BOX,
                    mapOf(
                        "android:key" to "sync_up_translate_deepl_instructions_on",
                        "android:title" to "Use custom instructions",
                        "android:defaultValue" to "false",
                    ),
                )
                row(
                    ROW,
                    mapOf(
                        "android:key" to "sync_up_translate_deepl_instructions",
                        "android:title" to "Custom instructions",
                        "android:summary" to "Not set",
                    ),
                )
                row(
                    ROW,
                    mapOf(
                        "android:key" to "sync_up_translate_google_key",
                        "android:title" to "Google Cloud API key",
                        "android:summary" to "Not set",
                    ),
                )
                row(
                    LIST,
                    mapOf(
                        "android:key" to "sync_up_translate_keep",
                        "android:title" to "Keep translations for",
                        "android:entries" to "@array/sync_up_translate_keep",
                        "android:entryValues" to "@array/sync_up_translate_keep_values",
                        "android:defaultValue" to "30",
                    ),
                )
                row(
                    ROW,
                    mapOf(
                        "android:key" to "sync_up_translate_cache_clear",
                        "android:title" to "Clear the translation cache",
                        "android:summary" to "Empty",
                    ),
                )
            }
        }
    }
}
