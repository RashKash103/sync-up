package app.morphe.extension.syncforreddit.translate;

import android.view.View;
import android.view.ViewGroup;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import com.laurencedawson.reddit_sync.ui.views.core.MaterialRow;

import java.lang.reflect.Field;
import java.util.List;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;

/**
 * What the entry in a menu says, which depends on what it would do.
 *
 * <p>Translating something already translated puts back what was written, so the entry that
 * does it should not still offer to translate. Sync renames an entry itself where the same is
 * true of saving — "Save" becomes "Unsave" — through the same method used here.
 *
 * @noinspection unused
 */
public final class Labels {
    private static final String TRANSLATE_A_COMMENT = "Translate comment";
    private static final String UNTRANSLATE_A_COMMENT = "Untranslate comment";
    private static final String TRANSLATE_TEXT = "Translate text";
    private static final String UNTRANSLATE_TEXT = "Untranslate text";
    private static final String TRANSLATE_EVERY = "Translate all comments";
    private static final String UNTRANSLATE_EVERY = "Untranslate all comments";

    /** How the row for all of them is told from the one Sync put there. */
    private static final String OURS = "sync-up-translate-all";

    /**
     * What marks something as standing translated, wherever that is worth showing: a blue as
     * bright against the rest as the amber Sync gives a saved comment, and the same wherever it
     * appears.
     */
    static final int WHILE_TRANSLATED = 0xFF4FB0F5;

    /** What the row is called on the sheet, which Sync keeps its own name for. */
    private static final String THE_ROW = "mTranslate";

    /** What Sync's own settings row for translating is drawn with. */
    private static final String THE_PICTURE = "outline_translate_24";

    private Labels() {}

    /**
     * Puts a row for all the comments of a thread under the one for the post, where there is a
     * thread to translate. A menu opened over a feed has no comments read yet, so it gets none.
     */
    private static void forAllOfThem(Object sheet, MaterialRow beside) {
        try {
            if (!(beside.getParent() instanceof ViewGroup)) {
                return;
            }
            ViewGroup menu = (ViewGroup) beside.getParent();

            List<xa.d> comments = EveryComment.of(sheet);
            View found = menu.findViewWithTag(OURS);
            if (comments.isEmpty()) {
                if (found != null) {
                    found.setVisibility(View.GONE);
                }
                return;
            }

            int translated = EveryComment.translatedAmong(comments);
            boolean back = translated > 0;

            MaterialRow ours;
            if (found instanceof MaterialRow) {
                ours = (MaterialRow) found;
            } else {
                ours = new MaterialRow(menu.getContext());
                ours.setTag(OURS);
                ours.d(ResourceUtils.getIdentifier(ResourceType.DRAWABLE, THE_PICTURE));
                menu.addView(ours, menu.indexOfChild(beside) + 1);
            }

            ours.setVisibility(View.VISIBLE);
            ours.k(back ? marked(UNTRANSLATE_EVERY) : TRANSLATE_EVERY);
            ours.setOnClickListener(tapped -> {
                EveryComment.translate(comments, back, what -> Utils.showToastShort(what));
                // Every row of Sync's own closes the sheet once it has done what it does.
                closed(sheet);
            });
        } catch (Throwable ex) {
            Logger.printInfo(() -> "Could not offer to translate all of them: " + ex);
        }
    }

    /** Closes the sheet, as tapping any of the rows Sync put there does. */
    private static void closed(Object sheet) {
        try {
            if (sheet instanceof s9.f) {
                ((s9.f) sheet).x3();
            }
        } catch (Throwable ex) {
            Logger.printInfo(() -> "Could not close the menu: " + ex);
        }
    }

    /** @return The words, marked as standing translated. */
    private static CharSequence marked(String words) {
        SpannableString said = new SpannableString(words);
        said.setSpan(new ForegroundColorSpan(WHILE_TRANSLATED), 0, words.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return said;
    }

    /**
     * @return Whether there is anything about this post worth translating: a title always,
     *         and a body where there is one. Nothing is offered while the setting is off.
     */
    private static boolean worthOffering(xa.d content) {
        try {
            if (!TranslationSettings.enabled(Utils.getContext())) {
                return false;
            }
            return Markdown.worthTranslating(content.b1())
                    || Markdown.worthTranslating(content.P0());
        } catch (Exception ex) {
            return false;
        }
    }

    /** @return What the entry in a comment's menu should say. */
    public static String forComment(xa.d content) {
        return Originals.isTranslated(content) ? UNTRANSLATE_A_COMMENT : TRANSLATE_A_COMMENT;
    }

    /**
     * Makes the entry in a post's menu ready: whether it is there at all, and what it says.
     *
     * <p>Sync shows it only for a post with a body, opened from the comments screen. A post
     * with nothing but a title is a post someone may want translated too, and a menu opened
     * over a feed is the same menu. So the entry is shown for anything with words in it,
     * wherever the menu was opened.
     *
     * <p>Only the sheet is handed over: every register around the entry is holding something,
     * one of them an argument the method was called with. Both the row and the post are read
     * off the sheet instead — the row by the name Sync gives it, which survived, and the post
     * by its type, which is the only one of its kind on the sheet.
     */
    public static void readyTheRow(Object sheet) {
        try {
            if (sheet == null) {
                return;
            }

            MaterialRow row = null;
            xa.d content = null;

            for (Field each : sheet.getClass().getDeclaredFields()) {
                if (row == null && THE_ROW.equals(each.getName())
                        && MaterialRow.class.isAssignableFrom(each.getType())) {
                    each.setAccessible(true);
                    row = (MaterialRow) each.get(sheet);
                } else if (content == null && xa.d.class.isAssignableFrom(each.getType())) {
                    each.setAccessible(true);
                    content = (xa.d) each.get(sheet);
                }
            }

            if (row == null || content == null) {
                return;
            }

            boolean translated = Originals.isTranslated(content);
            if (translated || worthOffering(content)) {
                row.setVisibility(View.VISIBLE);
            }
            // The row takes what it is told to say as it is given, so what it says can be
            // coloured where it is worth marking, as a saved comment is marked.
            row.k(translated ? marked(UNTRANSLATE_TEXT) : TRANSLATE_TEXT);

            forAllOfThem(sheet, row);
        } catch (Throwable ex) {
            // A menu that opens saying the wrong thing beats one that does not open.
            Logger.printInfo(() -> "Could not name the translate row: " + ex);
        }
    }
}
