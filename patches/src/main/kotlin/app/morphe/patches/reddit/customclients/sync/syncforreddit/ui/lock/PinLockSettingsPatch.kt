package app.morphe.patches.reddit.customclients.sync.syncforreddit.ui.lock

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.util.drawableNamed
import org.w3c.dom.Element

private const val PREFERENCES = "com.laurencedawson.reddit_sync.ui.preferences"

private const val CATEGORY_HEADER = "$PREFERENCES.defaults.CategoryHeaderPreference"
private const val ROW = "$PREFERENCES.defaults.SyncPreference"
private const val LIST = "$PREFERENCES.defaults.SyncListPreference"

/** Adds the PIN rows to the Locks category Sync's own account lock already sits in. */
internal val pinLockSettingsPatch = resourcePatch(
    description = "Adds the PIN rows to the Security settings.",
) {
    execute {
        document("res/values/arrays.xml").use { document ->
            val resources = document.getElementsByTagName("resources").item(0) as Element

            fun array(name: String, items: List<String>) {
                val array = document.createElement("string-array")
                array.setAttribute("name", name)
                items.forEach {
                    val item = document.createElement("item")
                    item.appendChild(document.createTextNode(it))
                    array.appendChild(item)
                }
                resources.appendChild(array)
            }

            array("sync_up_lock_mode_entries", listOf("Ask for both", "Ask for either"))
            array("sync_up_lock_mode_values", listOf("both", "either"))
        }

        document("res/xml/cat_security.xml").use { document ->
            val headers = document.getElementsByTagName(CATEGORY_HEADER)
            val locks = (0 until headers.length)
                .map { headers.item(it) as Element }
                .firstOrNull { it.getAttribute("app:categoryTitle") == "Locks" }
                ?.parentNode as? Element
                ?: throw PatchException("No Locks category in the security settings")

            fun add(tag: String, attributes: Map<String, String>) {
                val preference = document.createElement(tag)
                attributes.forEach { (name, value) -> preference.setAttribute(name, value) }
                locks.appendChild(preference)
            }

            add(
                ROW,
                mapOf(
                    "android:key" to "sync_up_pin",
                    "android:title" to "PIN account lock",
                    // Beside the two locks of Sync's own, which both carry one.
                    "android:icon" to drawableNamed("baseline_lock_24"),
                    // Replaced as the screen loads with whether one is actually set.
                    "android:summary" to
                            "Asked for when this account is opened, beside any fingerprint",
                ),
            )
            add(
                LIST,
                mapOf(
                    "android:key" to "sync_up_lock_mode",
                    "android:title" to "When a PIN and a fingerprint are both set",
                    "android:entries" to "@array/sync_up_lock_mode_entries",
                    "android:entryValues" to "@array/sync_up_lock_mode_values",
                    "android:defaultValue" to "both",
                    "android:summary" to "%s",
                    "android:icon" to drawableNamed("outline_lock_24"),
                ),
            )
        }
    }
}
