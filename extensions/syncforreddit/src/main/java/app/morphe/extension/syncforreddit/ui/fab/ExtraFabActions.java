package app.morphe.extension.syncforreddit.ui.fab;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.syncforreddit.settings.SyncUpSettings;

import com.laurencedawson.reddit_sync.ui.activities.BaseActivity;
import com.laurencedawson.reddit_sync.ui.fragment_dialogs.bottom.ActionsBottomSheetFragment;

/**
 * More actions on the feed's floating button.
 *
 * <p>Sync's own button carries exactly one of three things. Everything else it can do is in the
 * actions sheet behind it, each known there by a number and carried out by one call.
 *
 * <p>Nothing is drawn by hand. Each extra action is another of Sync's own floating buttons, put
 * in the same place the first one is and given a copy of its layout — which carries the rule
 * that hides it as the feed scrolls and the spacing that keeps it clear of the bottom bar — so
 * they behave as that button behaves rather than as an imitation of it.
 *
 * <p>Nothing here is allowed to take the feed down with it: every step is guarded, and Sync's
 * button is left untouched if any of it fails.
 *
 * @noinspection unused
 */
public final class ExtraFabActions {
    /** How many extra actions can be put on the button. */
    public static final int SLOTS = 4;

    private static final String SLOT = "sync_up_fab_slot_";
    private static final String ORIENTATION = "sync_up_fab_orientation";

    /** What a slot holds when it holds nothing. */
    private static final int NOTHING = -1;

    /** Stacked up the screen above Sync's own button. */
    private static final int VERTICAL = 0;

    /** What each added button is tagged with, so they are found again rather than added twice. */
    private static final String TAG = "sync-up-fab-action-";

    /** Sync's own button, which the added ones are made to match. */
    private static final String THE_BUTTON =
            "com.laurencedawson.reddit_sync.ui.views.monet.MonetFab";

    /** How far apart the buttons stand, centre to centre, in the smaller size. */
    private static final int STEP_DP = 52;

    private ExtraFabActions() {}

    public static boolean isPatchIncluded() {
        // Overridden by patch.
        return false;
    }

    /**
     * Called as Sync sets its own button up.
     *
     * @param fab       Sync's own floating button.
     * @param subreddit What the feed is showing. Only written down; an action is carried out
     *                  against whatever Sync says the screen is about at the time.
     */
    public static void attach(View fab, String subreddit) {
        if (!isPatchIncluded()) {
            return;
        }
        try {
            SyncUpSettings.report("fab", SLOT + "1", SLOT + "2", SLOT + "3", SLOT + "4",
                    ORIENTATION);

            ViewGroup parent = fab.getParent() instanceof ViewGroup
                    ? (ViewGroup) fab.getParent() : null;
            if (parent == null) {
                Logger.printInfo(() -> "fab: the button has nothing to stand beside");
                return;
            }

            List<Integer> wanted = chosen();
            Logger.printInfo(() -> "fab: attach for r/" + subreddit + ", chosen " + wanted);

            // Whatever was there before: the chosen set may have changed, or this may be a
            // second pass over the same feed.
            for (int slot = 0; slot < SLOTS; slot++) {
                View previous = parent.findViewWithTag(TAG + slot);
                if (previous != null) {
                    parent.removeView(previous);
                }
            }
            if (wanted.isEmpty()) {
                Logger.printInfo(() -> "fab: nothing chosen, leaving the button alone");
                return;
            }

            boolean vertical = SyncUpSettings.number(ORIENTATION, VERTICAL) == VERTICAL;
            int step = (int) (STEP_DP * fab.getContext().getResources()
                    .getDisplayMetrics().density);

            int placed = 0;
            for (int at = 0; at < wanted.size(); at++) {
                View added = another(fab, wanted.get(at), at);
                if (added == null) {
                    continue;
                }
                parent.addView(added);
                // Copied rather than built: what holds the button also holds the rule that
                // hides it as the feed scrolls and the spacing that keeps it off the bottom bar.
                if (!standBeside(fab, added, (at + 1) * step, vertical)) {
                    parent.removeView(added);
                    continue;
                }
                placed++;
            }

            final int drawn = placed;
            Logger.printInfo(() -> "fab: put " + drawn + " more button(s) beside Sync's own");
        } catch (Throwable ex) {
            // A feed that draws is worth more than the actions beside its button.
            Logger.printInfo(() -> "fab: could not add the actions: " + ex);
        }
    }

    /** @return The actions chosen, in the order their slots are in, without the empty ones. */
    private static List<Integer> chosen() {
        List<Integer> actions = new ArrayList<>();
        for (int slot = 1; slot <= SLOTS; slot++) {
            int action = SyncUpSettings.number(SLOT + slot, NOTHING);
            if (action == NOTHING || actions.contains(action)) {
                continue;
            }
            actions.add(action);
        }
        return actions;
    }

    /**
     * @return Another of Sync's own buttons, carrying this action, or null where one could not
     *         be made.
     */
    private static View another(View fab, int action, int slot) {
        Context context = fab.getContext();
        try {
            // Sync's own class, so the added buttons are themed and shaped as the first one is.
            Class<?> kind = Class.forName(THE_BUTTON, true, fab.getClass().getClassLoader());
            View added = (View) kind
                    .getConstructor(Context.class, android.util.AttributeSet.class)
                    .newInstance(context, null);

            added.setTag(TAG + slot);
            if (added instanceof ImageView) {
                ((ImageView) added).setImageResource(t7.b.d(action));
                ((ImageView) added).setImageTintList(((ImageView) fab).getImageTintList());
            }
            String label = t7.b.h(action);
            added.setContentDescription(label == null ? null : label.replace('\n', ' '));
            added.setOnClickListener(view -> carryOut(view, action));

            Logger.printInfo(() -> "fab: made a button for action " + action + " (" + label + ")");
            return added;
        } catch (Throwable ex) {
            Logger.printInfo(() -> "fab: could not make a button for action " + action + ": " + ex);
            return null;
        }
    }

    /**
     * Gives the added button the same layout as Sync's own, moved along by one step.
     *
     * <p>Copied field by field rather than rebuilt: the layout carries the rule that hides the
     * button as the feed scrolls and the spacing that keeps it clear of the bottom bar, and
     * neither is ours to work out again.
     *
     * @return Whether the layout could be copied. A button placed by its own rules would land
     *         somewhere arbitrary, so one that cannot be placed is not shown at all.
     */
    private static boolean standBeside(View fab, View added, int along, boolean vertical) {
        try {
            ViewGroup.LayoutParams from = fab.getLayoutParams();
            ViewGroup.LayoutParams to = added.getLayoutParams();
            if (from == null || to == null || !from.getClass().equals(to.getClass())) {
                Logger.printInfo(() -> "fab: the added button is laid out as a "
                        + (to == null ? "nothing" : to.getClass().getName())
                        + " where Sync's is a "
                        + (from == null ? "nothing" : from.getClass().getName()));
                return false;
            }

            for (Class<?> level = from.getClass(); level != null; level = level.getSuperclass()) {
                for (Field field : level.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())
                            || Modifier.isFinal(field.getModifiers())) {
                        continue;
                    }
                    field.setAccessible(true);
                    field.set(to, field.get(from));
                }
            }

            if (to instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) to;
                if (vertical) {
                    margins.bottomMargin += along;
                } else {
                    margins.rightMargin += along;
                    margins.setMarginEnd(margins.getMarginEnd() + along);
                }
            }
            added.setLayoutParams(to);
            Logger.printDebug(() -> "fab: standing " + along + " along from Sync's own button");
            return true;
        } catch (Throwable ex) {
            Logger.printInfo(() -> "fab: could not lay the added button out as Sync's: " + ex);
            return false;
        }
    }

    /**
     * Hands the action to the same code the actions sheet runs it with, worked out from the
     * context exactly as Sync works it out when it runs one from a context alone.
     */
    private static void carryOut(View view, int action) {
        Context context = view.getContext();
        try {
            BaseActivity activity = t7.j.a(context);
            String about = t7.j.b(context);
            Logger.printInfo(() -> "fab: carrying out action " + action + " for r/" + about);
            ActionsBottomSheetFragment.w4(activity, t7.j.g(context), about, action, null);
        } catch (Throwable ex) {
            Logger.printInfo(() -> "fab: action " + action + " failed: " + ex);
        }
    }
}
