package app.morphe.patches.reddit.customclients.sync.syncforreddit.ui.archive

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/ui/AddArchiveLinksPatch;"

private const val ADD_ROWS_METHOD =
    "addArchiveRows(Landroid/view/View;Ljava/lang/String;)V"

private const val POST_MODEL = "Lxa/d;"
private const val SHEET_CLASS =
    "Lcom/laurencedawson/reddit_sync/ui/fragment_dialogs/bottom/PostMoreBottomSheetFragment;"

@Suppress("unused")
val addArchiveLinksPatch = bytecodePatch(
    name = "Add archive links to the post menu",
    description = "Adds Wayback Machine and archive.today options to the menu behind a post's " +
            "overflow button, next to \"Open in browser\". Useful for reading a page that has " +
            "since been taken down or put behind a paywall.",
    default = false
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*SyncForRedditCompatible)

    execute {
        postMoreSheetOnViewCreatedFingerprint.method.apply {
            // Appended, so Sync has finished hiding and showing its own rows first.
            addInstructions(
                instructions.lastIndex,
                """
                iget-object         v0, p0, $SHEET_CLASS->P0 $POST_MODEL
                invoke-interface    { v0 }, $POST_MODEL->e1()Ljava/lang/String;
                move-result-object  v0
                invoke-static       { p1, v0 }, $EXTENSION_CLASS_DESCRIPTOR->$ADD_ROWS_METHOD
                """
            )
        }
    }
}
