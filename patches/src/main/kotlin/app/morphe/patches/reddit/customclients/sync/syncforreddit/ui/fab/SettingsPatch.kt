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

/** How many actions can stand beside the button. Matches the extension. */
private const val SLOTS = 3

/**
 * What is added to an action's number when it is stored in Sync's own setting for what its
 * button is for. That setting already means three things of Sync's own by the numbers 0, 1 and
 * 2, and those are left meaning them.
 */
private const val OURS_START_AT = 100

/** What Sync's own button can be, which its setting has always chosen between. */
private val ITS_OWN = listOf("Submit", "Hide read", "More actions")

/**
 * Sync's own actions, in the order it numbers them. Written out here rather than read from the
 * app because the settings are built before anything runs; the numbers are what the extension
 * hands back to Sync, so the order is the whole of it.
 *
 * <p>Taken from the switch that answers what an action is called, read by its jump table rather
 * than by the order the names appear in the code — which is the reverse, and choosing an action
 * by it hands Sync a different one entirely.
 */
private val ACTIONS = listOf(
    "Hide read", "Saved", "Profile", "Submit", "Change view",
    "Random", "Random NSFW", "Dark mode", "Friends", "Settings",
    "Data saving", "Filters", "Recents", "Toggle account", "Sort",
    "Refresh", "About", "Sync", "Search", "Watched",
    "Swipe mode", "Inbox", "Explore", "Dark overlay", "Scroll to top",
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

            // Sync's own setting for what its button is for, opened up: the three it already
            // offered, and then every action the sheet behind it knows.
            array("sync_up_main_fab_entries", ITS_OWN + ACTIONS)
            array(
                "sync_up_main_fab_values",
                ITS_OWN.indices.map { it.toString() } +
                    ACTIONS.indices.map { (OURS_START_AT + it).toString() },
            )
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

            // Sync's own setting is kept and opened up rather than replaced. Taking it away
            // is what crashed this screen: Sync looks it up by name and speaks to whatever
            // comes back, which was nothing.
            val its = (0 until category.childNodes.length)
                .map { category.childNodes.item(it) }
                .filterIsInstance<Element>()
                .firstOrNull { it.getAttribute("android:key") == "main_fab_action" }
                ?: throw PatchException("Sync no longer chooses what its floating button is for")

            its.setAttribute("android:title", "Main action")
            its.setAttribute("android:entries", "@array/sync_up_main_fab_entries")
            its.setAttribute("android:entryValues", "@array/sync_up_main_fab_values")
            its.setAttribute("android:summary", "%s")

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
