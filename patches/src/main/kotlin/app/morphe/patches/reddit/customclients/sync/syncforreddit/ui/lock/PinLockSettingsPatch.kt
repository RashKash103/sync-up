package app.morphe.patches.reddit.customclients.sync.syncforreddit.ui.lock

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.util.drawableNamed
import org.w3c.dom.Element

private const val PREFERENCES = "com.laurencedawson.reddit_sync.ui.preferences"

private const val CATEGORY_HEADER = "$PREFERENCES.defaults.CategoryHeaderPreference"
private const val ROW = "$PREFERENCES.defaults.SyncPreference"

/** Adds the PIN row to the Locks category Sync's own account lock already sits in. */
internal val pinLockSettingsPatch = resourcePatch(
    description = "Adds the PIN row to the Security settings.",
) {
    execute {
        document("res/xml/cat_security.xml").use { document ->
            val headers = document.getElementsByTagName(CATEGORY_HEADER)
            val locks = (0 until headers.length)
                .map { headers.item(it) as Element }
                .firstOrNull { it.getAttribute("app:categoryTitle") == "Locks" }
                ?.parentNode as? Element
                ?: throw PatchException("No Locks category in the security settings")

            val row = document.createElement(ROW)
            row.setAttribute("android:key", "sync_up_pin")
            row.setAttribute("android:title", "PIN account lock")
            // Beside the two locks of Sync's own, which both carry one.
            row.setAttribute("android:icon", drawableNamed("baseline_lock_24"))
            // Replaced as the screen loads with whether one is actually set.
            row.setAttribute(
                "android:summary",
                "Asked for when this account is opened, beside any fingerprint",
            )
            locks.appendChild(row)
        }
    }
}
