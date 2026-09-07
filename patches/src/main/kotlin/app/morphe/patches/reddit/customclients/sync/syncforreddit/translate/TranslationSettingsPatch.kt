package app.morphe.patches.reddit.customclients.sync.syncforreddit.translate

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val PREFERENCES = "com.laurencedawson.reddit_sync.ui.preferences"

private const val HEADER = "$PREFERENCES.HeaderPreference"
private const val CATEGORY = "$PREFERENCES.defaults.CategoryHeaderPreference"
private const val CHECK_BOX = "$PREFERENCES.defaults.SyncCheckBoxPreference"
private const val LIST = "$PREFERENCES.defaults.SyncListPreference"
private const val ROW = "$PREFERENCES.defaults.SyncPreference"
private const val ROOT_ROW = "$PREFERENCES.custom.RootPreference"

/** Sync's own screen of settings, written the way Sync writes its own. */
private const val SCREEN = "res/xml/cat_translation.xml"

/**
 * Gives translation a screen of Sync's settings, built from the same preferences Sync builds its
 * own screens from so that it is drawn by the app rather than beside it: the same rows, the same
 * headings, the same theme.
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

        // The screen itself, written where Sync keeps its own.
        get(SCREEN).writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto">
                <$HEADER app:title="Translation" />

                <PreferenceCategory>
                    <$CATEGORY app:categoryTitle="General" />
                    <$CHECK_BOX
                        android:key="sync_up_translate"
                        android:title="Translate posts and comments"
                        android:summary="Adds translation to the post and comment menus"
                        android:defaultValue="true" />
                    <$LIST
                        android:key="sync_up_translate_service"
                        android:title="Translate with"
                        android:entries="@array/sync_up_translate_services"
                        android:entryValues="@array/sync_up_translate_service_values"
                        android:defaultValue="device" />
                    <$LIST
                        android:key="sync_up_translate_language"
                        android:title="Translate into"
                        android:entries="@array/sync_up_translate_languages"
                        android:entryValues="@array/sync_up_translate_language_values"
                        android:defaultValue="en-US" />
                </PreferenceCategory>

                <PreferenceCategory>
                    <$CATEGORY app:categoryTitle="DeepL" />
                    <$ROW
                        android:key="sync_up_translate_deepl_key"
                        android:title="API key"
                        android:summary="Not set" />
                    <$ROW
                        android:key="sync_up_translate_deepl_usage"
                        android:title="Usage"
                        android:summary="Tap to check" />
                    <$LIST
                        android:key="sync_up_translate_deepl_context"
                        android:title="Send along with a comment"
                        android:summary="What a comment is translated alongside, so that it reads in context"
                        android:entries="@array/sync_up_translate_context"
                        android:entryValues="@array/sync_up_translate_context_values"
                        android:defaultValue="thread" />
                    <$CHECK_BOX
                        android:key="sync_up_translate_deepl_instructions_on"
                        android:title="Use custom instructions"
                        android:defaultValue="false" />
                    <$ROW
                        android:key="sync_up_translate_deepl_instructions"
                        android:title="Custom instructions"
                        android:summary="Not set" />
                </PreferenceCategory>

                <PreferenceCategory>
                    <$CATEGORY app:categoryTitle="Google Cloud" />
                    <$ROW
                        android:key="sync_up_translate_google_key"
                        android:title="API key"
                        android:summary="Not set" />
                </PreferenceCategory>

                <PreferenceCategory>
                    <$CATEGORY app:categoryTitle="Cache" />
                    <$LIST
                        android:key="sync_up_translate_keep"
                        android:title="Keep translations for"
                        android:entries="@array/sync_up_translate_keep"
                        android:entryValues="@array/sync_up_translate_keep_values"
                        android:defaultValue="30" />
                    <$ROW
                        android:key="sync_up_translate_cache_clear"
                        android:title="Clear the translation cache"
                        android:summary="Empty" />
                </PreferenceCategory>
            </PreferenceScreen>
            """.trimIndent()
        )

        // A row on the front page of the settings that opens it, as Sync's own screens are opened.
        document("res/xml/cat_root.xml").use { document ->
            val headers = document.getElementsByTagName(CATEGORY)
            val appearance = (0 until headers.length)
                .map { headers.item(it) as Element }
                .firstOrNull { it.getAttribute("app:categoryTitle") == "Appearance" }
                ?.parentNode as? Element
                ?: throw PatchException("No category on the settings front page to add the row to")

            val row = document.createElement(ROOT_ROW)
            row.setAttribute("android:title", "Translation")
            row.setAttribute("android:summary", "Read posts and comments in your own language")
            row.setAttribute("app:custom_icon", "@drawable/outline_translate_24")
            row.setAttribute("app:preference_ref", "@xml/cat_translation")
            appearance.appendChild(row)
        }
    }
}
