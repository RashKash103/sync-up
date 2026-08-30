package app.morphe.patches.reddit.customclients.sync.syncforreddit.ui.selftext

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible

@Suppress("unused")
val drawSelftextPatch = bytecodePatch(
    name = "Draw the text of a post the way it was written",
    description = "Draws the body shown under a post in Sync's Slide layout, both in a feed " +
            "and above the comments, from the marked up text rather than from a flat copy of " +
            "it, so that quotes, emphasis and above all links appear in it. Without this an " +
            "address written in a post is drawn as ordinary words: the same colour as the text " +
            "around it, and nothing happens when it is tapped. Sync draws the body this way " +
            "itself whenever its own selftext preview setting is off, and draws it this way in " +
            "its other layouts regardless.",
    default = true
) {
    compatibleWith(*SyncForRedditCompatible)

    execute {
        // Both draw the same body into the same view and differ only in where they take it from,
        // so the flat one is made to defer to the other. Its own two lines of setting up are
        // dropped with the rest of it: the drawn one begins by doing the same.
        val drawn = drawnSelftextFingerprint.method.name

        plainSelftextFingerprint.method.apply {
            val replaced = instructions.count()

            addInstructions(
                0,
                """
                const/4             v0, 0x0
                invoke-virtual      { p0, p1, v0 }, $SELFTEXT_VIEW_CLASS->$drawn(Lxa/d;Z)V
                return-void
                """
            )

            removeInstructions(3, replaced)
        }
    }
}
