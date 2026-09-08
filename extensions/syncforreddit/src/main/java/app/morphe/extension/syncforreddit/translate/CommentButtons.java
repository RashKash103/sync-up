package app.morphe.extension.syncforreddit.translate;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.lang.reflect.Field;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * A button for translating in the row that appears under a comment when it is tapped.
 *
 * <p>Sync has no such button. Its own translating is offered from the menu at the top of a
 * thread and from a comment's own menu, and the row under a comment is a fixed set laid out in
 * the layout for it. So one is made and put at the end of that row as each comment is bound,
 * which asks nothing of the layout and leaves the row exactly as it was where translation is
 * turned off.
 *
 * @noinspection unused
 */
public final class CommentButtons {
    /** What Sync calls the row, which it kept the name of. */
    private static final String THE_ROW = "mCommentActions";

    /** How ours is told from the app's own, which are held in fields rather than looked up. */
    private static final String OURS = "sync-up-translate";

    private CommentButtons() {}

    /**
     * Called as a comment is bound to the row that draws it.
     *
     * @param holder  What draws the comment, which holds the row of buttons.
     * @param comment What is being drawn.
     */
    public static void onBind(Object holder, xa.d comment) {
        try {
            ViewGroup row = rowOf(holder);
            if (row == null || comment == null) {
                return;
            }

            TranslateButton ours = alreadyThere(row);
            if (!worthOffering(comment)) {
                if (ours != null) {
                    ours.setVisibility(View.GONE);
                }
                return;
            }

            if (ours == null) {
                ours = added(row);
                if (ours == null) {
                    return;
                }
            }

            ours.setVisibility(View.VISIBLE);
            ours.setContentDescription(Originals.isTranslated(comment)
                    ? "Undo the translation" : "Translate this comment");

            // A row is drawn again for another comment, so what it does is set every time.
            xa.d about = comment;
            ours.setOnClickListener(tapped -> WithoutTheSheet.forThis(about, null));
        } catch (Throwable ex) {
            // A comment that draws without the button beats one that does not draw.
            Logger.printInfo(() -> "Could not put a translate button under a comment: " + ex);
        }
    }

    /** @return Whether there is anything about this comment worth offering to translate. */
    private static boolean worthOffering(xa.d comment) {
        try {
            return TranslationSettings.enabled(Utils.getContext())
                    && Markdown.worthTranslating(comment.o());
        } catch (Exception ex) {
            return false;
        }
    }

    /** @return The row of buttons, read off what draws the comment by the name Sync gives it. */
    private static ViewGroup rowOf(Object holder) throws Exception {
        if (holder == null) {
            return null;
        }
        for (Field each : holder.getClass().getDeclaredFields()) {
            if (THE_ROW.equals(each.getName()) && ViewGroup.class.isAssignableFrom(each.getType())) {
                each.setAccessible(true);
                return (ViewGroup) each.get(holder);
            }
        }
        return null;
    }

    /** @return Ours, where this row has already been given one. */
    private static TranslateButton alreadyThere(ViewGroup row) {
        View found = row.findViewWithTag(OURS);
        return found instanceof TranslateButton ? (TranslateButton) found : null;
    }

    /**
     * @return A button added to the end of the row, sized as the buttons beside it are.
     *
     * <p>Every one of them is as wide as the whole row and carries a weight of one, which is how
     * a row of buttons is made to share the width evenly. The weight is the part that does that:
     * a button as wide as the row without one takes the whole of it and leaves the others
     * nothing, so it has to be carried across rather than the width alone.
     */
    private static TranslateButton added(ViewGroup row) {
        if (row.getChildCount() == 0) {
            return null;
        }

        TranslateButton ours = new TranslateButton(row.getContext());
        ours.setTag(OURS);

        ViewGroup.LayoutParams like = row.getChildAt(row.getChildCount() - 1).getLayoutParams();
        if (like instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams beside = (LinearLayout.LayoutParams) like;
            LinearLayout.LayoutParams mine =
                    new LinearLayout.LayoutParams(beside.width, beside.height, beside.weight);
            mine.setMargins(beside.leftMargin, beside.topMargin, beside.rightMargin,
                    beside.bottomMargin);
            mine.gravity = beside.gravity;
            row.addView(ours, mine);
        } else if (like != null) {
            row.addView(ours, new ViewGroup.LayoutParams(like));
        } else {
            row.addView(ours);
        }
        return ours;
    }
}
