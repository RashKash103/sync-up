package app.morphe.extension.syncforreddit.ui.fab;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
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
 * The feed's floating button, with more than one thing on it.
 *
 * <p>Sync's own button carries exactly one of three things. Everything else it can do is in the
 * actions sheet behind it, each known there by a number, drawn with an icon and carried out by
 * one call. Those same numbers are what this offers directly.
 *
 * <p>The button is not left sitting beside a second one: what is drawn is a single surface
 * holding the extra actions and, last, the button's own action, standing exactly where the
 * button stood. Sync's button is still there underneath, kept invisible and doing the work when
 * its cell is tapped, so everything it decides — which action it carries, when it hides itself —
 * still holds.
 *
 * <p>Nothing here is allowed to take the feed down with it: every step is guarded, and the
 * button Sync draws is put back exactly as it was if any of it fails.
 *
 * @noinspection unused
 */
public final class ExtraFabActions {
    /** How many extra actions can be put on the button. */
    public static final int SLOTS = 4;

    private static final String SLOT = "sync_up_fab_slot_";
    private static final String ORIENTATION = "sync_up_fab_orientation";
    private static final String SEPARATORS = "sync_up_fab_separators";

    /** What a slot holds when it holds nothing. */
    private static final int NOTHING = -1;

    /** Laid out down the screen, the button's own action at the bottom. */
    private static final int VERTICAL = 0;

    /** What the surface is tagged with, so it is found again rather than added twice. */
    private static final String TAG = "sync-up-fab-actions";

    /** Matches the button it stands in place of, so the two read as one thing. */
    private static final int SIZE_DP = 56;
    private static final int ICON_DP = 24;
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

            ViewGroup parent = fab.getParent() instanceof ViewGroup
                    ? (ViewGroup) fab.getParent() : null;
            if (parent == null) {
                Logger.printInfo(() -> "fab: the button has nothing to be added beside");
                return;
            }

            List<Integer> wanted = chosen();
            Logger.printInfo(() -> "fab: attach for r/" + subreddit + ", chosen " + wanted);

            if (wanted.isEmpty()) {
                // Nothing asked for, so Sync's own button is left to do its job by itself.
                Logger.printInfo(() -> "fab: nothing chosen, putting the button back");
                restore(fab);
                View previous = parent.findViewWithTag(TAG);
                if (previous != null) {
                    parent.removeView(previous);
                }
                return;
            }

            View existing = parent.findViewWithTag(TAG);
            if (existing != null) {
                parent.removeView(existing);
            }

            LinearLayout surface = build(fab, wanted, subreddit);
            if (surface == null) {
                restore(fab);
                return;
            }
            parent.addView(surface);
            follow(fab, surface);
            Logger.printInfo(() -> "fab: standing in for the button with " + wanted.size()
                    + " extra action(s) plus its own");
        } catch (Throwable ex) {
            // A feed that draws is worth more than the actions on its button.
            Logger.printInfo(() -> "fab: could not stand in for the button: " + ex);
            restore(fab);
        }
    }

    /** Puts Sync's own button back the way it was found. */
    private static void restore(View fab) {
        try {
            fab.setAlpha(1f);
            fab.setClickable(true);
        } catch (Throwable ex) {
            Logger.printDebug(() -> "fab: could not put the button back: " + ex);
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

    private static LinearLayout build(View fab, List<Integer> actions, String subreddit) {
        Context context = fab.getContext();
        boolean vertical = SyncUpSettings.number(ORIENTATION, VERTICAL) == VERTICAL;
        boolean separators = SyncUpSettings.flag(SEPARATORS, true);
        ColorStateList tint = iconTint(fab);

        LinearLayout surface = new LinearLayout(context);
        surface.setTag(TAG);
        surface.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        surface.setGravity(Gravity.CENTER);
        surface.setElevation(fab.getElevation() > 0 ? fab.getElevation() : fromDp(context, 6));
        surface.setBackground(surfaceOf(fab, vertical));

        for (int at = 0; at < actions.size(); at++) {
            View button = extra(context, actions.get(at), subreddit, tint);
            if (button == null) {
                continue;
            }
            if (separators && surface.getChildCount() > 0) {
                surface.addView(separator(context, vertical));
            }
            surface.addView(button);
        }

        if (surface.getChildCount() == 0) {
            Logger.printInfo(() -> "fab: none of the chosen actions could be drawn");
            return null;
        }

        // The button's own action goes last, so it ends up where the button itself was and
        // everything added grows away from it rather than pushing it aside.
        if (separators) {
            surface.addView(separator(context, vertical));
        }
        surface.addView(itsOwn(context, fab));

        surface.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return surface;
    }

    /** The cell standing in for the button, which hands the tap straight back to it. */
    private static View itsOwn(Context context, View fab) {
        ImageView cell = cell(context);
        try {
            Drawable icon = ((ImageView) fab).getDrawable();
            if (icon != null) {
                cell.setImageDrawable(icon.getConstantState() == null
                        ? icon : icon.getConstantState().newDrawable().mutate());
                cell.setImageTintList(((ImageView) fab).getImageTintList());
            } else {
                Logger.printInfo(() -> "fab: the button is drawn with nothing to copy");
            }
        } catch (Throwable ex) {
            Logger.printInfo(() -> "fab: could not copy what the button is drawn with: " + ex);
        }
        cell.setContentDescription(fab.getContentDescription());
        cell.setOnClickListener(view -> {
            Logger.printInfo(() -> "fab: handing the tap back to Sync's own button");
            fab.setClickable(true);
            fab.performClick();
            fab.setClickable(false);
        });
        cell.setOnLongClickListener(view -> {
            fab.setClickable(true);
            boolean handled = fab.performLongClick();
            fab.setClickable(false);
            return handled;
        });
        return cell;
    }

    private static View extra(Context context, int action, String subreddit, ColorStateList tint) {
        try {
            int icon = t7.b.d(action);
            String label = t7.b.h(action);
            Logger.printDebug(() -> "fab: cell for action " + action + " is " + label);

            ImageView cell = cell(context);
            cell.setImageResource(icon);
            if (tint != null) {
                cell.setImageTintList(tint);
            }
            cell.setContentDescription(label == null ? null : label.replace('\n', ' '));
            cell.setOnClickListener(view -> carryOut(view, action, subreddit));
            return cell;
        } catch (Throwable ex) {
            Logger.printInfo(() -> "fab: action " + action + " could not be drawn: " + ex);
            return null;
        }
    }

    private static ImageView cell(Context context) {
        ImageView cell = new ImageView(context);
        int size = (int) fromDp(context, SIZE_DP);
        cell.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        int padding = (int) ((size - fromDp(context, ICON_DP)) / 2f);
        cell.setPadding(padding, padding, padding, padding);
        cell.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cell.setClickable(true);
        cell.setFocusable(true);
        return cell;
    }

    /** What the icons on the button are tinted with, so the added ones match them. */
    private static ColorStateList iconTint(View fab) {
        try {
            ColorStateList tint = ((ImageView) fab).getImageTintList();
            if (tint != null) {
                Logger.printDebug(() -> "fab: tinting the added icons as the button's are");
                return tint;
            }
        } catch (Throwable ex) {
            Logger.printDebug(() -> "fab: the button says nothing about its tint: " + ex);
        }
        return ColorStateList.valueOf(Color.WHITE);
    }

    /** The one surface the cells share, coloured and shaped like the button it stands for. */
    private static GradientDrawable surfaceOf(View fab, boolean vertical) {
        Context context = fab.getContext();
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(fromDp(context, SIZE_DP / 2f));
        shape.setColor(colourOfTheButton(fab));
        return shape;
    }

    /**
     * The colour Sync's own button is drawn in, asked of the button itself first so that the
     * two match whatever theme is in use.
     */
    private static int colourOfTheButton(View fab) {
        try {
            ColorStateList tint = fab.getBackgroundTintList();
            if (tint != null) {
                int colour = tint.getDefaultColor();
                Logger.printInfo(() -> "fab: drawing the surface in the button's own colour "
                        + String.format("#%08X", colour));
                return colour;
            }
        } catch (Throwable ex) {
            Logger.printDebug(() -> "fab: the button says nothing about its colour: " + ex);
        }
        try {
            android.util.TypedValue found = new android.util.TypedValue();
            if (fab.getContext().getTheme()
                    .resolveAttribute(android.R.attr.colorAccent, found, true)) {
                int colour = found.data;
                Logger.printInfo(() -> "fab: drawing the surface in the theme's accent "
                        + String.format("#%08X", colour));
                return colour;
            }
        } catch (Throwable ex) {
            Logger.printDebug(() -> "fab: no theme colour to draw on: " + ex);
        }
        Logger.printInfo(() -> "fab: drawing the surface in a plain dark grey");
        return Color.parseColor("#333333");
    }

    private static View separator(Context context, boolean vertical) {
        View line = new View(context);
        int thickness = (int) fromDp(context, SEPARATOR_DP);
        int length = (int) fromDp(context, SIZE_DP * 0.5f);
        line.setLayoutParams(vertical
                ? new LinearLayout.LayoutParams(length, thickness)
                : new LinearLayout.LayoutParams(thickness, length));
        line.setBackgroundColor(Color.argb(60, 255, 255, 255));
        return line;
    }

    /** Hands the action to the same code the actions sheet runs it with. */
    private static void carryOut(View view, int action, String subreddit) {
        try {
            FragmentActivity activity = activityOf(view.getContext());
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
     * Keeps the surface standing where the button stands. Placed against the button's own
     * position rather than through the parent's layout rules, which differ between the layouts
     * Sync puts the button in and are not ours to reason about.
     */
    private static void follow(View fab, View surface) {
        ViewTreeObserver.OnGlobalLayoutListener place =
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        place(fab, surface);
                    }
                };
        fab.getViewTreeObserver().addOnGlobalLayoutListener(place);
        surface.post(() -> place(fab, surface));
    }

    /** How often a placement is worth writing down, so a layout pass does not flood the log. */
    private static int said;

    private static void place(View fab, View surface) {
        try {
            if (surface.getParent() == null) {
                // Replaced by a later pass over the feed; this one has nothing left to place.
                return;
            }
            if (fab.getWidth() == 0 || surface.getWidth() == 0) {
                return;
            }

            // Hidden rather than made invisible: what Sync sets on the button is how it says
            // whether the button should be showing at all, and that is still worth following.
            fab.setAlpha(0f);
            fab.setClickable(false);

            boolean vertical = SyncUpSettings.number(ORIENTATION, VERTICAL) == VERTICAL;
            float x;
            float y;
            if (vertical) {
                x = fab.getX() + (fab.getWidth() - surface.getWidth()) / 2f;
                y = fab.getY() + fab.getHeight() - surface.getHeight();
            } else {
                x = fab.getX() + fab.getWidth() - surface.getWidth();
                y = fab.getY() + (fab.getHeight() - surface.getHeight()) / 2f;
            }

            surface.setX(x);
            surface.setY(y);
            // Settled on every pass rather than only when it moves: the button starts out
            // invisible and is shown later, and a surface that copied that once stayed hidden.
            surface.setVisibility(fab.getVisibility() == View.GONE ? View.GONE : View.VISIBLE);

            if (said < 8) {
                said++;
                final float placedX = x;
                final float placedY = y;
                Logger.printInfo(() -> "fab: standing at " + placedX + "," + placedY
                        + " (" + surface.getWidth() + "x" + surface.getHeight()
                        + ") over a button at " + fab.getX() + "," + fab.getY()
                        + " (" + fab.getWidth() + "x" + fab.getHeight() + "), the button says "
                        + fab.getVisibility() + " so the surface is "
                        + surface.getVisibility());
            }
        } catch (Throwable ex) {
            Logger.printDebug(() -> "fab: could not place the surface: " + ex);
        }
    }

    private static float fromDp(Context context, float dp) {
        return dp * context.getResources().getDisplayMetrics().density;
    }
}
