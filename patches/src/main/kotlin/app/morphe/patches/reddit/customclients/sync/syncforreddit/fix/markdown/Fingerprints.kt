package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.markdown

import app.morphe.patcher.Fingerprint

/**
 * Rewrites a fenced code block before the text is drawn, putting what it matched back in as the
 * replacement. This is the one a thread that will not load dies in.
 */
internal val codeBlockFingerprint = Fingerprint(
    parameters = listOf("Ljava/lang/String;"),
    returnType = "Ljava/lang/String;",
    strings = listOf("```(.*?)```"),
)

/**
 * Rewrites a link written in the text, likewise putting matched text back in as the replacement.
 * Vulnerable to exactly the same thing, and by the same means.
 */
internal val markdownLinkFingerprint = Fingerprint(
    parameters = listOf("Ljava/lang/String;"),
    returnType = "Ljava/lang/String;",
    strings = listOf("█", "]("),
)

/**
 * Turns a body into what Sync draws: the rewrites, then markdown, then the tidying afterwards.
 */
internal val bodyProcessorFingerprint = Fingerprint(
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
    returnType = "Ljava/lang/String;",
    strings = listOf("ESCAPED_SPOILER", "BODY: "),
)

/** Sync's builder for a piece of drawn text, the same one the recovery notes are written into. */
private const val SPAN_BUILDER_CLASS = "Loc/c;"

/** Appends a piece of text carrying nothing of its own. */
internal val appendPlainFingerprint = Fingerprint(
    parameters = listOf("Ljava/lang/CharSequence;"),
    returnType = "V",
    custom = { _, classDef -> classDef.type == SPAN_BUILDER_CLASS },
)

/** Appends a piece of text together with the spans it is to carry. */
internal val appendSpannedFingerprint = Fingerprint(
    parameters = listOf("Ljava/lang/String;", "[Ljava/lang/Object;"),
    returnType = "V",
    custom = { _, classDef -> classDef.type == SPAN_BUILDER_CLASS },
)

/**
 * Lays a span over text already appended. A link is drawn this way rather than in one piece: the
 * address is written out as the words arrive and covered afterwards, once its end is known.
 */
internal val applySpanFingerprint = Fingerprint(
    parameters = listOf("Ljava/lang/Object;", "I", "I", "I"),
    returnType = "V",
    custom = { _, classDef -> classDef.type == SPAN_BUILDER_CLASS },
)
