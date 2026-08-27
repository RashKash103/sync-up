package app.morphe.patches.reddit.customclients.sync.syncforreddit.ui.archive

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/syncforreddit/ui/AddArchiveLinksPatch;"

private const val ADD_ROWS_METHOD =
    "addArchiveRows(Landroid/view/View;Ljava/lang/String;)V"

// An abstract class rather than an interface, so its methods take invoke-virtual.
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
            // Sync reuses p1 as a scratch local two instructions in, so the rows have to be
            // added while it still holds the view that was passed in. Anchored just after the
            // view binding call, which is the last point where that is true.
            val index = indexOfFirstInstructionOrThrow {
                val reference = getReference<MethodReference>()
                reference?.definingClass == "Lbutterknife/ButterKnife;"
            } + 1

            addInstructions(
                index,
                """
                iget-object         v0, p0, $SHEET_CLASS->P0:$POST_MODEL
                invoke-virtual      { v0 }, $POST_MODEL->e1()Ljava/lang/String;
                move-result-object  v0
                invoke-static       { p1, v0 }, $EXTENSION_CLASS_DESCRIPTOR->$ADD_ROWS_METHOD
                """
            )
        }
    }
}
