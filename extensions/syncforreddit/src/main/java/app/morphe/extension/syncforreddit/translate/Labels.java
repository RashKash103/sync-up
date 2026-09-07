package app.morphe.extension.syncforreddit.translate;

import com.laurencedawson.reddit_sync.ui.views.core.MaterialRow;

import java.lang.reflect.Field;

import app.morphe.extension.shared.Logger;

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

    /** What the row is called on the sheet, which Sync keeps its own name for. */
    private static final String THE_ROW = "mTranslate";

    private Labels() {}

    /** @return What the entry in a comment's menu should say. */
    public static String forComment(xa.d content) {
        return Originals.isTranslated(content) ? UNTRANSLATE_A_COMMENT : TRANSLATE_A_COMMENT;
    }

    /**
     * Names the entry in a post's menu, which is written into the layout rather than passed in
     * as a comment's is.
     *
     * <p>Only the sheet is handed over: every register around the entry is holding something,
     * one of them the argument the method was called with. Both the row and the post are read
     * off the sheet instead — the row by the name Sync gives it, which survived, and the post
     * by its type, which is the only one of its kind on the sheet.
     */
    public static void nameTheRow(Object sheet) {
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

            if (row != null && content != null) {
                row.k(Originals.isTranslated(content) ? UNTRANSLATE_TEXT : TRANSLATE_TEXT);
            }
        } catch (Throwable ex) {
            // A menu that opens saying the wrong thing beats one that does not open.
            Logger.printInfo(() -> "Could not name the translate row: " + ex);
        }
    }
}
