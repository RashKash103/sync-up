package app.morphe.patches.reddit.customclients.sync.syncforreddit.ui.archive

import app.morphe.patcher.Fingerprint

/**
 * Sets up the menu behind a post's overflow button. The rows are already inflated by the time
 * this runs, and it is the last thing to touch them, so rows added at the end of it survive
 * the visibility changes it makes.
 */
internal val postMoreSheetOnViewCreatedFingerprint = Fingerprint(
    definingClass = "Lcom/laurencedawson/reddit_sync/ui/fragment_dialogs/bottom/PostMoreBottomSheetFragment;",
    name = "o2",
    parameters = listOf("Landroid/view/View;", "Landroid/os/Bundle;"),
)
