package app.morphe.patches.reddit.customclients.sync.syncforreddit.ui.fab

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val CATEGORY_HEADER =
    "com.laurencedawson.reddit_sync.ui.preferences.defaults.CategoryHeaderPreference"

private const val CHECK_BOX =
    "com.laurencedawson.reddit_sync.ui.preferences.defaults.SyncCheckBoxPreference"

private const val LIST =
    "com.laurencedawson.reddit_sync.ui.preferences.defaults.SyncListPreference"

/** How many actions can be put beside the button. Matches the extension. */
private const val SLOTS = 4

/**
 * Sync's own actions, in the order it numbers them. Written out here rather than read from the
 * app because the settings are built before anything runs; the numbers are what the extension
 * hands back to Sync, so only the order matters.
 */
private val ACTIONS = listOf(
    "Scroll to top", "Dark overlay", "Explore", "Inbox", "Swipe mode",
    "Watched", "Search", "Sync", "About", "Refresh",
    "Sort", "Toggle account", "Recents", "Filters", "Data saving",
    "Settings", "Friends", "Dark mode", "Random NSFW", "Random",
    "Change view", "Submit", "Profile", "Saved", "Hide read",
)

/**
 * Adds the settings to the Floating Action Button category Sync already has, which is where the
 * button's own options are.
 */
internal val extraFabActionsSettingsPatch = resourcePatch(
    description = "Adds the extra floating button actions to the Floating Action Button category.",
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

            array("sync_up_fab_entries", listOf("None") + ACTIONS)
            array(
                "sync_up_fab_values",
                listOf("-1") + ACTIONS.indices.map { it.toString() },
            )
            array("sync_up_fab_orientation_entries", listOf("Vertical", "Horizontal"))
            array("sync_up_fab_orientation_values", listOf("0", "1"))
        }

        document("res/xml/cat_general.xml").use { document ->
            val headers = document.getElementsByTagName(CATEGORY_HEADER)
            val category = (0 until headers.length)
                .map { headers.item(it) as Element }
                .firstOrNull { it.getAttribute("app:categoryTitle") == "Floating Action Button" }
                ?.parentNode as? Element
                ?: throw PatchException("No Floating Action Button category to add the settings to")

            fun add(tag: String, attributes: Map<String, String>) {
                val preference = document.createElement(tag)
                attributes.forEach { (name, value) -> preference.setAttribute(name, value) }
                category.appendChild(preference)
            }

            (1..SLOTS).forEach { slot ->
                add(
                    LIST,
                    mapOf(
                        "android:key" to "sync_up_fab_slot_$slot",
                        "android:title" to "Extra action $slot",
                        "android:entries" to "@array/sync_up_fab_entries",
                        "android:entryValues" to "@array/sync_up_fab_values",
                        "android:defaultValue" to "-1",
                        // Shows what is chosen without having to open it.
                        "android:summary" to "%s",
                    ),
                )
            }
            add(
                LIST,
                mapOf(
                    "android:key" to "sync_up_fab_orientation",
                    "android:title" to "Orientation",
                    "android:entries" to "@array/sync_up_fab_orientation_entries",
                    "android:entryValues" to "@array/sync_up_fab_orientation_values",
                    "android:defaultValue" to "0",
                    "android:summary" to "%s",
                ),
            )
            add(
                CHECK_BOX,
                mapOf(
                    "android:key" to "sync_up_fab_separators",
                    "android:title" to "Separate the extra actions",
                    "android:summary" to "Draw a line between them",
                    "android:defaultValue" to "true",
                ),
            )
        }
    }
}
