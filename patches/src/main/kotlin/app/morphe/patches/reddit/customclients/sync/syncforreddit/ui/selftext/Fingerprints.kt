package app.morphe.patches.reddit.customclients.sync.syncforreddit.ui.selftext

import app.morphe.patcher.Fingerprint

internal const val SELFTEXT_VIEW_CLASS =
    "Lcom/laurencedawson/reddit_sync/ui/views/posts/simple/SimpleSelftextPreviewTextView;"

/**
 * Puts a post's own text into the view as flat characters: the stored plain copy of the body,
 * appended with nothing over it. Whatever the body said, what arrives is unformatted and inert.
 *
 * It is the only method on the view taking a post and nothing else.
 */
internal val plainSelftextFingerprint = Fingerprint(
    parameters = listOf("Lxa/d;"),
    returnType = "V",
    custom = { _, classDef -> classDef.type == SELFTEXT_VIEW_CLASS },
)

/**
 * Draws the same body the way Sync draws every other one, from the marked up copy, and is what
 * the app itself falls back to when its preview setting is off. The second argument is whether
 * the post is open, which decides how many lines are shown.
 */
internal val drawnSelftextFingerprint = Fingerprint(
    parameters = listOf("Lxa/d;", "Z"),
    returnType = "V",
    custom = { _, classDef -> classDef.type == SELFTEXT_VIEW_CLASS },
)
