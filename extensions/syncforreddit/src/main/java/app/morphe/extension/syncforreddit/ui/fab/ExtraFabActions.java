package app.morphe.extension.syncforreddit.ui.fab;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.fragment.app.FragmentActivity;

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.syncforreddit.settings.SyncUpSettings;

import com.laurencedawson.reddit_sync.ui.fragment_dialogs.bottom.ActionsBottomSheetFragment;

/**
 * Extra actions beside the feed's floating button.
 *
 * <p>Sync's own button carries exactly one of three things. Everything else it can do is in the
 * actions sheet behind it, each known there by a number, drawn with an icon and carried out by
 * one call. Those same numbers are what this offers directly, as a row or column of buttons
 * sharing one rounded surface beside the button itself.
 *
 * <p>Nothing here is allowed to take the feed down with it: every step is guarded, and the
 * button Sync draws is left exactly as it was if any of it fails.
 *
 * @noinspection unused
 */
public final class ExtraFabActions {
    /** How many actions can be put beside the button. */
    public static final int SLOTS = 4;

    private static final String SLOT = "sync_up_fab_slot_";
    private static final String ORIENTATION = "sync_up_fab_orientation";
    private static final String SEPARATORS = "sync_up_fab_separators";

    /** What a slot holds when it holds nothing. */
    private static final int NOTHING = -1;

    /** Laid out down the screen, above the button. */
    private static final int VERTICAL = 0;

    /** What the container is tagged with, so it is found again rather than added twice. */
    private static final String TAG = "sync-up-fab-actions";

    private static final int SIZE_DP = 44;
    private static final int ICON_DP = 22;
    private static final int GAP_DP = 12;
    private static final int SEPARATOR_DP = 1;

    private ExtraFabActions() {}

    public static boolean isPatchIncluded() {
        // Overridden by patch.
        return false;
    }

    /**
     * Called as Sync sets its own button up, which is where what the feed is about is in reach.
     *
     * @param fab       Sync's own floating button.
     * @param subreddit What the feed is showing, which an action is carried out against.
     */
    public static void attach(View fab, String subreddit) {
        if (!isPatchIncluded()) {
            return;
        }
        try {
            SyncUpSettings.report("fab", SLOT + "1", SLOT + "2", SLOT + "3", SLOT + "4",
                    ORIENTATION, SEPARATORS);

            List<Integer> wanted = chosen();
            Logger.printInfo(() -> "fab: attach for r/" + subreddit + ", chosen " + wanted
                    + ", parent " + (fab.getParent() == null ? "none"
                    : fab.getParent().getClass().getName()));

            ViewGroup parent = fab.getParent() instanceof ViewGroup
                    ? (ViewGroup) fab.getParent() : null;
            if (parent == null) {
                Logger.printInfo(() -> "fab: the button has nothing to be added beside");
                return;
            }

            View existing = parent.findViewWithTag(TAG);
            if (existing != null) {
                Logger.printDebug(() -> "fab: taking the previous row away before rebuilding");
                parent.removeView(existing);
            }
            if (wanted.isEmpty()) {
                Logger.printInfo(() -> "fab: nothing chosen, leaving the button alone");
                return;
            }

            LinearLayout row = build(fab.getContext(), wanted, subreddit);
            if (row == null) {
                return;
            }
            parent.addView(row);
            follow(fab, row);
            Logger.printInfo(() -> "fab: added " + wanted.size() + " action(s) beside the button");
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
            if (action == NOTHING) {
                continue;
            }
            if (actions.contains(action)) {
                final int repeated = action;
                Logger.printDebug(() -> "fab: action " + repeated + " chosen twice, kept once");
                continue;
            }
            actions.add(action);
        }
        return actions;
    }

    private static LinearLayout build(Context context, List<Integer> actions, String subreddit) {
        boolean vertical = SyncUpSettings.number(ORIENTATION, VERTICAL) == VERTICAL;
        boolean separators = SyncUpSettings.flag(SEPARATORS, true);

        LinearLayout row = new LinearLayout(context);
        row.setTag(TAG);
        row.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setElevation(fromDp(context, 6));
        row.setBackground(surface(context, vertical));

        for (int at = 0; at < actions.size(); at++) {
            int action = actions.get(at);
            if (separators && at > 0) {
                row.addView(separator(context, vertical));
            }
            View button = button(context, action, subreddit);
            if (button == null) {
                continue;
            }
            row.addView(button);
        }

        if (row.getChildCount() == 0) {
            Logger.printInfo(() -> "fab: none of the chosen actions could be drawn");
            return null;
        }
        row.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    /** The one surface the buttons share, shaped like the button it sits beside. */
    private static GradientDrawable surface(Context context, boolean vertical) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(fromDp(context, SIZE_DP / 2f));
        shape.setColor(colourOfTheButton(context));
        return shape;
    }

    /**
     * The colour Sync's own button is drawn in, so the two read as one thing. Taken from the
     * theme rather than assumed, and falling back to a dark grey that is legible either way.
     */
    private static int colourOfTheButton(Context context) {
        try {
            android.util.TypedValue found = new android.util.TypedValue();
            // Asked for by name: the support library's attributes are not on the compile path
            // here, and the framework one is what Sync's own themes set anyway.
            if (context.getTheme().resolveAttribute(android.R.attr.colorAccent, found, true)) {
                int colour = found.data;
                Logger.printDebug(() -> "fab: drawing on the theme's primary colour");
                return colour;
            }
        } catch (Throwable ex) {
            Logger.printDebug(() -> "fab: no theme colour to draw on: " + ex);
        }
        return Color.parseColor("#333333");
    }

    private static View separator(Context context, boolean vertical) {
        View line = new View(context);
        int thickness = (int) fromDp(context, SEPARATOR_DP);
        int length = (int) fromDp(context, SIZE_DP * 0.55f);
        line.setLayoutParams(vertical
                ? new LinearLayout.LayoutParams(length, thickness)
                : new LinearLayout.LayoutParams(thickness, length));
        line.setBackgroundColor(Color.argb(60, 255, 255, 255));
        return line;
    }

    private static View button(Context context, int action, String subreddit) {
        try {
            int icon = t7.b.d(action);
            String label = t7.b.h(action);
            Logger.printDebug(() -> "fab: slot action " + action + " is " + label);

            ImageView button = new ImageView(context);
            int size = (int) fromDp(context, SIZE_DP);
            button.setLayoutParams(new LinearLayout.LayoutParams(size, size));
            int padding = (int) ((size - fromDp(context, ICON_DP)) / 2f);
            button.setPadding(padding, padding, padding, padding);
            button.setImageResource(icon);
            button.setScaleType(ImageView.ScaleType.FIT_CENTER);
            button.setContentDescription(label == null ? null : label.replace('\n', ' '));
            button.setClickable(true);
            button.setFocusable(true);
            button.setOnClickListener(view -> carryOut(view, action, subreddit));
            return button;
        } catch (Throwable ex) {
            Logger.printInfo(() -> "fab: action " + action + " could not be drawn: " + ex);
            return null;
        }
    }

    /** Hands the action to the same code the actions sheet runs it with. */
    private static void carryOut(View view, int action, String subreddit) {
        try {
            Context context = view.getContext();
            FragmentActivity activity = activityOf(context);
            if (activity == null) {
                Logger.printInfo(() -> "fab: action " + action + " has no activity to run in");
                return;
            }
            Logger.printInfo(() -> "fab: carrying out action " + action + " for r/" + subreddit);
            ActionsBottomSheetFragment.w4(
                    activity, activity.getSupportFragmentManager(), subreddit, action, null);
        } catch (Throwable ex) {
            Logger.printInfo(() -> "fab: action " + action + " failed: " + ex);
        }
    }

    private static FragmentActivity activityOf(Context context) {
        Context at = context;
        while (at instanceof android.content.ContextWrapper) {
            if (at instanceof FragmentActivity) {
                return (FragmentActivity) at;
            }
            at = ((android.content.ContextWrapper) at).getBaseContext();
        }
        return null;
    }

    /**
     * Keeps the row beside the button wherever the button ends up. Placed against the button's
     * own position rather than through the parent's layout rules, which differ between the
     * layouts Sync puts the button in and are not ours to reason about.
     */
    private static void follow(View fab, View row) {
        ViewTreeObserver.OnGlobalLayoutListener place =
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        try {
                            place(fab, row);
                        } catch (Throwable ex) {
                            Logger.printDebug(() -> "fab: could not place the row: " + ex);
                        }
                    }
                };
        fab.getViewTreeObserver().addOnGlobalLayoutListener(place);
        row.post(() -> {
            try {
                place(fab, row);
            } catch (Throwable ex) {
                Logger.printDebug(() -> "fab: could not place the row: " + ex);
            }
        });
    }

    private static void place(View fab, View row) {
        if (fab.getWidth() == 0 || row.getWidth() == 0) {
            return;
        }
        boolean vertical = SyncUpSettings.number(ORIENTATION, VERTICAL) == VERTICAL;
        float gap = fromDp(fab.getContext(), GAP_DP);

        float x;
        float y;
        if (vertical) {
            // Centred on the button, standing above it.
            x = fab.getX() + (fab.getWidth() - row.getWidth()) / 2f;
            y = fab.getY() - row.getHeight() - gap;
        } else {
            // Level with the button, running back from it.
            x = fab.getX() - row.getWidth() - gap;
            y = fab.getY() + (fab.getHeight() - row.getHeight()) / 2f;
        }

        if (row.getX() != x || row.getY() != y) {
            row.setX(x);
            row.setY(y);
            row.setVisibility(fab.getVisibility());
        }
    }

    private static float fromDp(Context context, float dp) {
        return dp * context.getResources().getDisplayMetrics().density;
    }
}
