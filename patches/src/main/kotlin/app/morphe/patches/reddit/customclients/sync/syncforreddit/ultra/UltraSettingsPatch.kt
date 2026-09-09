package app.morphe.patches.reddit.customclients.sync.syncforreddit.ultra

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val ROOT_SCREEN = "res/xml/cat_root.xml"

private const val BACKUP_SCREEN = "res/xml/cat_backup.xml"

/** The way in to the subscription, which sits among the screens rather than under one. */
private const val THE_WAY_IN = "Sync Ultra"

/** What is kept on Sync's own servers, which stopped answering. */
private val KEPT_ON_THEIR_SERVERS = setOf("ultra_backup", "ultra_backup_divider")

/**
 * Takes the subscription out of the settings.
 *
 * <p>Nothing there can be bought any more, and what it offered is either turned on already or
 * kept on servers that no longer answer. A screen that can only disappoint is worse than no
 * screen at all.
 *
 * <p>Has no name, and so is not offered on its own.
 */
internal val ultraSettingsPatch = resourcePatch(
    description = "Removes the Sync Ultra screen and the cloud backup that needs it.",
) {
    execute {
        document(ROOT_SCREEN).use { document ->
            val rows = document.getElementsByTagName("*")
            val theWayIn = (0 until rows.length)
                .map { rows.item(it) as Element }
                .firstOrNull { it.getAttribute("android:title") == THE_WAY_IN }
                ?: throw PatchException("No way in to the subscription to take out")

            theWayIn.parentNode.removeChild(theWayIn)
        }

        document(BACKUP_SCREEN).use { document ->
            val rows = document.getElementsByTagName("*")
            val theirs = (0 until rows.length)
                .map { rows.item(it) as Element }
                .filter { it.getAttribute("android:key") in KEPT_ON_THEIR_SERVERS }

            if (theirs.isEmpty()) {
                throw PatchException("No cloud backup to take out")
            }
            theirs.forEach { it.parentNode.removeChild(it) }
        }
    }
}
