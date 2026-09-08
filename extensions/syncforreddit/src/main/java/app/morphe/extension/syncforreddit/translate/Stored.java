package app.morphe.extension.syncforreddit.translate;

import android.content.ContentValues;
import android.content.Context;
import android.text.Html;
import android.text.SpannableStringBuilder;

import com.laurencedawson.reddit_sync.provider.RedditProvider;

import app.morphe.extension.shared.Logger;

/**
 * Putting text where the app reads it from.
 *
 * <p>Wanted in two places — when a translation is made, and when the app has written over one
 * with what its author wrote — so it belongs to neither.
 */
final class Stored {
    /** What the app calls a comment, where it says which kind of thing it has. */
    static final int A_COMMENT = 11;

    /** Where the text of each is kept. */
    private static final String COMMENT_WRITTEN = "body_raw";
    private static final String COMMENT_DRAWN = "body_processed";
    private static final String POST_WRITTEN = "selftext_raw";
    private static final String POST_DRAWN = "selftext_processed";
    private static final String POST_PREVIEW = "selftext_processed_preview";
    private static final String THE_TITLE = "title";

    /** Where Sync cuts the drawn text to take the first of it for a feed. */
    private static final String FIRST_OF_IT = "\n.";

    /** What Sync's renderer leaves where a spoiler was. */
    private static final String A_SPOILER = "<abbr>";

    private Stored() {}

    /**
     * Writes the text where the app reads it from and says so, which is what makes everything
     * showing this post or comment draw it again.
     */
    static void write(Context context, String id, boolean isAComment, String title,
                              String body) {
        ContentValues values = new ContentValues();
        if (body != null) {
            String drawn = d7.f.r(null, body);
            values.put(isAComment ? COMMENT_WRITTEN : POST_WRITTEN, body);
            values.put(isAComment ? COMMENT_DRAWN : POST_DRAWN, drawn);
            if (!isAComment) {
                // What a feed shows under a title is kept apart from the text it came from.
                values.put(POST_PREVIEW, previewOf(drawn));
            }
        }
        if (title != null) {
            values.put(THE_TITLE, title);
        }
        if (values.size() == 0) {
            return;
        }

        context.getContentResolver().update(RedditProvider.q, values, id, null);
        context.getContentResolver().notifyChange(RedditProvider.B, null);
    }

    /**
     * @return What a feed shows under a title: the first of the drawn text, as words rather
     *         than as tags, with a mark to say there is more. Built the way Sync builds it, so
     *         that a translated post reads in a feed exactly as an untranslated one does.
     */
    private static String previewOf(String drawn) {
        if (drawn == null || drawn.isEmpty()) {
            return null;
        }
        try {
            String[] parts = drawn.split(FIRST_OF_IT);
            boolean thereIsMore = parts.length > 1;
            String first = thereIsMore ? parts[0].trim() : drawn;

            if (first.contains(A_SPOILER)) {
                return "<Spoiler hidden>";
            }

            SpannableStringBuilder said =
                    (SpannableStringBuilder) Html.fromHtml(first);
            said.clearSpans();
            String words = said.toString().replaceAll("\uFFFC", "").trim();
            if (thereIsMore) {
                words = words + "\n…";
            }

            words = words.replace("\u200B", "").trim();
            return words.isEmpty() ? null : words;
        } catch (Exception ex) {
            Logger.printInfo(() -> "Could not build the preview: " + ex);
            return null;
        }
    }

}
