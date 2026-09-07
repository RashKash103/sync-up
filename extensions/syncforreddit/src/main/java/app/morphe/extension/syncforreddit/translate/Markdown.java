package app.morphe.extension.syncforreddit.translate;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translating text that was written as markdown, without translating the markdown.
 *
 * <p>Sync hands its own translation the text as it is drawn rather than as it was written, so
 * everything that made it more than words — the line it sat on, the address behind a link — is
 * gone before a translation is asked for. What comes back is one long paragraph with the links
 * flattened into their own words, which is the text of the post but not the post.
 *
 * <p>Here the text as written is translated instead, a line at a time so that the lines survive.
 * Anything on a line that is not language — an address, the name of a subreddit or a person,
 * code, where a link points — is stood in for by a numbered marker before the line is sent, and
 * put back afterwards. The line is translated whole, so a sentence with a link in the middle of
 * it is still a sentence when it arrives.
 *
 * <p>The library on the device has nothing of its own for this: it will not keep so much as a
 * line break, let alone a tag. Both services abroad do — DeepL asks for XML tags with an id on
 * each and Google for HTML with the parts to leave alone marked — and a marker is what those
 * want too, so this is the shape all three can be given.
 *
 * <p>A translation is under no obligation to give a marker back, and is known to drop them and
 * to hand them back in another order. So every marker is looked for afterwards, and where one
 * did not survive the line is translated again in pieces around what it must not touch. That is
 * worse to read and cannot be wrong.
 */
final class Markdown {
    /** What a translation is asked of, so that this need not know which service answers. */
    interface Translates {
        String translate(String text) throws Exception;
    }

    /** Where a block of code begins or ends, inside which nothing is language. */
    private static final Pattern A_FENCE = Pattern.compile("^\\s{0,3}(```|~~~)");

    /** A line held far enough from the margin to be code rather than prose. */
    private static final Pattern INDENTED = Pattern.compile("^(?: {4}|\\t)");

    /**
     * What a line may begin with and still be a line of prose: any depth of quoting, then at
     * most one marker for a list or a heading.
     */
    private static final Pattern LEADING = Pattern.compile(
            "^((?:\\s*>)*\\s*(?:[-*+]\\s+|\\d+[.)]\\s+|#{1,6}\\s+)?)");

    /**
     * What is kept as it is. In order: code between backticks, a link written out in full, an
     * address inside angle brackets, a bare address, and the name of a subreddit or a person.
     */
    private static final Pattern KEEP = Pattern.compile(
            "(`[^`]*`)"
                    + "|(\\[[^\\]\\n]*\\]\\([^)\\s]*(?:\\s+\"[^\"]*\")?\\))"
                    + "|(<[^>\\s]+>)"
                    + "|((?:https?://|www\\.)\\S+)"
                    + "|((?<![\\w/])/?[ru]/[A-Za-z0-9_][A-Za-z0-9_-]*)");

    private static final int LINK = 2;

    /** A link written out in full, split into what it says and where it points. */
    private static final Pattern A_LINK = Pattern.compile("^\\[([^\\]\\n]*)\\](\\(.*\\))$");

    /** Whether there is anything on a line worth asking about. */
    private static final Pattern A_LETTER = Pattern.compile("\\p{L}");

    private Markdown() {}

    /**
     * @param markdown The text as it was written.
     * @param by       What translates a run of words.
     * @return The same text, translated, with everything that was not words left alone.
     */
    static String translated(String markdown, Translates by) throws Exception {
        StringBuilder said = new StringBuilder(markdown.length());
        boolean inCode = false;

        int at = 0;
        while (at <= markdown.length()) {
            int ends = markdown.indexOf('\n', at);
            boolean last = ends < 0;
            String line = markdown.substring(at, last ? markdown.length() : ends);

            if (A_FENCE.matcher(line).find()) {
                inCode = !inCode;
                said.append(line);
            } else if (inCode || INDENTED.matcher(line).find()
                    || !A_LETTER.matcher(line).find()) {
                // A blank line, a rule, a line of code: nothing to ask about, and the line has
                // to stay where it is.
                said.append(line);
            } else {
                Matcher leading = LEADING.matcher(line);
                String marker = leading.find() ? leading.group(1) : "";
                said.append(marker).append(inLine(line.substring(marker.length()), by));
            }

            if (last) {
                break;
            }
            said.append('\n');
            at = ends + 1;
        }

        return said.toString();
    }

    /**
     * A marker standing in for something the translation must not touch. Letters and a number,
     * with no space in it, which is what a translation is most likely to carry across unharmed.
     */
    private static final String A_MARKER = "DNT";

    /**
     * How a marker is found again. A translation may change its case or work a space into it,
     * and it is still the marker; anything more than that and it is not.
     */
    private static final Pattern MARKER = Pattern.compile("(?i)d\\s*n\\s*t\\s*(\\d+)");

    /** A line with its markers in place, and what each of them stands for. */
    private static final class Masked {
        final String line;
        final List<String> kept = new ArrayList<>();
        final List<Boolean> isALinkTarget = new ArrayList<>();

        Masked(String line) {
            this.line = line;
        }
    }

    /** @return One line of prose translated, around whatever on it is not prose. */
    private static String inLine(String line, Translates by) throws Exception {
        Masked masked = masked(line);
        if (masked == null) {
            // Either there is nothing to keep, or the line already reads like a marker.
            return inPieces(line, by);
        }

        String restored = restored(asWords(masked.line, by), masked);
        return restored != null ? restored : inPieces(line, by);
    }

    /**
     * @return The line with a marker in place of everything that must survive it, or null where
     *         the line cannot be marked up safely.
     */
    private static Masked masked(String line) {
        if (MARKER.matcher(line).find()) {
            // A marker put in here would not be told apart from what was already written.
            return null;
        }

        StringBuilder said = new StringBuilder(line.length());
        Masked masked = new Masked(null);
        Matcher keep = KEEP.matcher(line);
        int from = 0;

        while (keep.find()) {
            said.append(line, from, keep.start());

            String kept = keep.group();
            Matcher link = keep.group(LINK) == null ? null : A_LINK.matcher(kept);
            if (link != null && link.matches()) {
                // What a link says stays in the sentence and is translated with it. Only where
                // it points is stood in for.
                String points = link.group(2);
                said.append('[').append(link.group(1)).append("](")
                        .append(A_MARKER).append(masked.kept.size()).append(')');
                masked.kept.add(points.substring(1, points.length() - 1));
                masked.isALinkTarget.add(true);
            } else {
                said.append(A_MARKER).append(masked.kept.size());
                masked.kept.add(kept);
                masked.isALinkTarget.add(false);
            }

            from = keep.end();
        }

        if (masked.kept.isEmpty()) {
            return null;
        }

        Masked marked = new Masked(said.append(line.substring(from)).toString());
        marked.kept.addAll(masked.kept);
        marked.isALinkTarget.addAll(masked.isALinkTarget);
        return marked;
    }

    /**
     * @return The translated line with everything that was stood in for put back, or null where
     *         a marker did not come back, came back twice, or came back somewhere it cannot be
     *         put back into.
     */
    private static String restored(String translated, Masked masked) {
        StringBuilder said = new StringBuilder(translated.length());
        boolean[] found = new boolean[masked.kept.size()];
        Matcher marker = MARKER.matcher(translated);
        int from = 0;

        while (marker.find()) {
            int which;
            try {
                which = Integer.parseInt(marker.group(1));
            } catch (NumberFormatException ex) {
                return null;
            }
            if (which >= found.length || found[which]) {
                return null;
            }

            if (masked.isALinkTarget.get(which) && !isWhereALinkPoints(translated, marker)) {
                // The words around it are what make it a link, and they did not survive.
                return null;
            }

            found[which] = true;
            said.append(translated, from, marker.start()).append(masked.kept.get(which));
            from = marker.end();
        }

        for (boolean each : found) {
            if (!each) {
                return null;
            }
        }

        return said.append(translated.substring(from)).toString();
    }

    /** @return Whether a marker is still sitting where a link points, brackets and all. */
    private static boolean isWhereALinkPoints(String translated, Matcher marker) {
        int opens = marker.start();
        int closes = marker.end();
        return opens >= 2
                && translated.charAt(opens - 1) == '('
                && translated.charAt(opens - 2) == ']'
                && closes < translated.length()
                && translated.charAt(closes) == ')';
    }

    /**
     * Translating a line in the pieces between what must not be touched. A sentence broken this
     * way reads worse, since each piece is translated without the rest of it, so this is only
     * what happens when the markers did not survive.
     *
     * @return The line translated, piece by piece.
     */
    private static String inPieces(String line, Translates by) throws Exception {
        StringBuilder said = new StringBuilder(line.length());
        Matcher keep = KEEP.matcher(line);
        int from = 0;

        while (keep.find()) {
            said.append(asWords(line.substring(from, keep.start()), by));

            String kept = keep.group();
            Matcher link = keep.group(LINK) == null ? null : A_LINK.matcher(kept);
            if (link != null && link.matches()) {
                // What a link says is words; where it points is not.
                said.append('[').append(asWords(link.group(1), by)).append(']')
                        .append(link.group(2));
            } else {
                said.append(kept);
            }

            from = keep.end();
        }

        return said.append(asWords(line.substring(from), by)).toString();
    }

    /** @return A run translated, or the run itself where there is nothing to translate. */
    private static String asWords(String run, Translates by) throws Exception {
        if (run.isEmpty() || !A_LETTER.matcher(run).find()) {
            return run;
        }

        // What sits either side of the words is punctuation and spacing that belongs to the
        // line rather than to the language, and a translation would not necessarily give it
        // back.
        int begins = 0;
        while (begins < run.length() && Character.isWhitespace(run.charAt(begins))) {
            begins++;
        }
        int ends = run.length();
        while (ends > begins && Character.isWhitespace(run.charAt(ends - 1))) {
            ends--;
        }

        String words = run.substring(begins, ends);
        String translated = by.translate(words);
        return run.substring(0, begins) + (translated == null ? words : translated)
                + run.substring(ends);
    }

    /** @return Whether the text is worth asking a service about at all. */
    static boolean worthTranslating(String markdown) {
        return markdown != null && A_LETTER.matcher(markdown).find();
    }
}
