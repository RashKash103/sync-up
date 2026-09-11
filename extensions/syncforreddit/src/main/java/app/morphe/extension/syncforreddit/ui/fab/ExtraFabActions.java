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

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.syncforreddit.settings.SyncUpSettings;

import com.laurencedawson.reddit_sync.ui.activities.BaseActivity;
import com.laurencedawson.reddit_sync.ui.fragment_dialogs.bottom.ActionsBottomSheetFragment;

/**
 * The feed's floating button, with more than one thing on it.
 *
 * <p>Sync's own button carries exactly one of three things. Everything else it can do is in the
 * actions sheet behind it, each known there by a number and carried out by one call.
 *
 * <p>What is drawn is one surface: the extra actions and, last, the button's own action, on a
 * single rounded shape the size and colour of the button. Sync's button stays exactly where it
 * was and keeps doing everything it did — deciding what it is for, and hiding itself as the feed
 * scrolls — but draws nothing, and the surface is laid over it every frame, taking its position,
 * its scale and its fade. So it slides and fades away with the feed rather than being told to.
 *
 * <p>Nothing here is allowed to take the feed down with it: every step is guarded, and Sync's
 * button is given back what it draws with if any of it fails.
 *
 * @noinspection unused
 */
public final class ExtraFabActions {
    /** How many actions can stand beside the button. */
    public static final int SLOTS = 3;

    /**
     * Sync's own setting for what its button is for, which is now chosen out of everything
     * rather than the three it offered.
     */
    private static final String ITS_OWN_SETTING = "main_fab_action";

    /**
     * What is added to an action's number when it is stored in that setting, so that 0, 1 and 2
     * go on meaning the three things Sync has always meant by them.
     */
    private static final int OURS_START_AT = 100;

    private static final String SLOT = "sync_up_fab_slot_";
    private static final String ORIENTATION = "sync_up_fab_orientation";
    private static final String SEPARATORS = "sync_up_fab_separators";

    /** What a slot holds when it holds nothing. */
    private static final int NOTHING = -1;

    /** Stacked up the screen, the button's own action at the bottom. */
    private static final int VERTICAL = 0;

    private static final String TAG = "sync-up-fab-actions";

    private static final int SEPARATOR_DP = 1;

    /** What the part standing where the button stands is marked with. */
    private static final int ITS_OWN = 0x7E000010;

    /** How often the chosen actions are looked at again, in frames. */
    private static final int LOOK_AGAIN_EVERY = 30;

    private ExtraFabActions() {}

    public static boolean isPatchIncluded() {
        // Overridden by patch.
        return false;
    }

    /** Called as Sync sets its own button up. */
    public static void attach(View fab, String subreddit) {
        if (!isPatchIncluded()) {
            return;
        }
        try {
            SyncUpSettings.report("fab", ITS_OWN_SETTING, SLOT + "1", SLOT + "2", SLOT + "3",
                    ORIENTATION, SEPARATORS);
            rebuild(fab);
            watch(fab);
            listen(fab);
        } catch (Throwable ex) {
            Logger.printInfo(() -> "fab: could not put the actions on the button: " + ex);
        }
    }

    /** What the surface was built from, so it is only rebuilt when the answer changes. */
    private static String built;

    /** Whether anything is already listening, so it is only set up once. */
    private static boolean listened;

    /** Rebuilds the moment a setting changes, rather than when the feed next draws. */
    private static void listen(View fab) {
        if (listened) {
            return;
        }
        listened = true;
        Runnable rebuild = () -> fab.post(() -> {
            try {
                // Forgotten first, so the answer is built again rather than recognised as the
                // one already standing there.
                built = null;
                rebuild(fab);
                fab.invalidate();
            } catch (Throwable ex) {
                Logger.printInfo(() -> "fab: could not rebuild after a change: " + ex);
            }
        });
        SyncUpSettings.whenChanged("sync_up_fab_", rebuild);
        // Sync's own, which is what the button itself is for.
        SyncUpSettings.whenChanged(ITS_OWN_SETTING, rebuild);
    }

    private static void rebuild(View fab) {
        ViewGroup parent = fab.getParent() instanceof ViewGroup
                ? (ViewGroup) fab.getParent() : null;
        if (parent == null) {
            Logger.printInfo(() -> "fab: the button has nothing to be laid over");
            return;
        }

        List<Integer> wanted = chosen();
        String now = wanted + "+" + mainAction() + "/"
                + SyncUpSettings.number(ORIENTATION, VERTICAL)
                + "/" + SyncUpSettings.flag(SEPARATORS, true);
        if (now.equals(built) && parent.findViewWithTag(TAG) != null) {
            return;
        }
        built = now;
        Logger.printInfo(() -> "fab: building for " + now);

        View previous = parent.findViewWithTag(TAG);
        if (previous != null) {
            parent.removeView(previous);
        }
        if (wanted.isEmpty() && mainAction() == NOTHING) {
            Logger.printInfo(() -> "fab: nothing chosen, giving the button back what it draws");
            giveBack(fab);
            return;
        }

        LinearLayout surface = build(fab, wanted);
        if (surface == null) {
            giveBack(fab);
            return;
        }
        parent.addView(surface);
        Logger.printInfo(() -> "fab: laid a surface of " + surface.getChildCount()
                + " part(s) over the button");
    }

    /** @return What the button's own part carries, or NOTHING to leave it as Sync set it. */
    private static int mainAction() {
        int held = SyncUpSettings.number(ITS_OWN_SETTING, NOTHING);
        return held >= OURS_START_AT ? held - OURS_START_AT : NOTHING;
    }

    /** @return The actions standing beside the button, in the order their slots are in. */
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

    /** What the button draws with, kept so it can be given back and drawn in the surface. */
    private static Drawable itsIcon;
    private static Drawable itsBackground;

    private static LinearLayout build(View fab, List<Integer> actions) {
        Context context = fab.getContext();
        boolean vertical = SyncUpSettings.number(ORIENTATION, VERTICAL) == VERTICAL;
        boolean separators = SyncUpSettings.flag(SEPARATORS, true);

        if (itsIcon == null && fab instanceof ImageView) {
            itsIcon = ((ImageView) fab).getDrawable();
        }
        if (itsBackground == null) {
            itsBackground = fab.getBackground();
        }

        int size = fab.getHeight() > 0 ? fab.getHeight() : (int) fromDp(context, 56);
        ColorStateList tint = fab instanceof ImageView
                ? ((ImageView) fab).getImageTintList() : null;

        LinearLayout surface = new LinearLayout(context);
        surface.setTag(TAG);
        surface.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        surface.setGravity(Gravity.CENTER);
        surface.setElevation(fab.getElevation());
        surface.setBackground(surfaceFor(context, fab, size));
        surface.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int action : actions) {
            View part = extra(context, action, size, tint);
            if (part == null) {
                continue;
            }
            if (separators && surface.getChildCount() > 0) {
                surface.addView(separator(context, size, vertical));
            }
            surface.addView(part);
        }
        if (separators && surface.getChildCount() > 0) {
            surface.addView(separator(context, size, vertical));
        }
        surface.addView(itsOwn(context, fab, size, tint));
        return surface;
    }

    /**
     * The part standing where the button stands.
     *
     * <p>Sync lets its button be one of three things. Where an action has been chosen for it
     * here, it carries that instead — any of the twenty-five — and where none has, it hands the
     * tap straight back to the button and is drawn with whatever the button is drawn with.
     */
    private static View itsOwn(Context context, View fab, int size, ColorStateList tint) {
        int main = mainAction();
        if (main != NOTHING) {
            View chosen = extra(context, main, size, tint);
            if (chosen != null) {
                chosen.setTag(ITS_OWN, Boolean.TRUE);
                Logger.printInfo(() -> "fab: the button itself carries action " + main
                        + ", drawn with " + (((ImageView) chosen).getDrawable() == null
                        ? "nothing" : "an icon"));
                return chosen;
            }
            Logger.printInfo(() -> "fab: action " + main + " could not be drawn for the button");
        }

        Logger.printInfo(() -> "fab: the button keeps its own action, drawn with "
                + (itsIcon == null ? "nothing yet" : "its own icon"));
        ImageView part = part(context, size);
        part.setTag(ITS_OWN, Boolean.TRUE);
        if (itsIcon != null) {
            part.setImageDrawable(itsIcon.getConstantState() == null
                    ? itsIcon : itsIcon.getConstantState().newDrawable().mutate());
            if (fab instanceof ImageView) {
                part.setImageTintList(((ImageView) fab).getImageTintList());
            }
        } else {
            Logger.printInfo(() -> "fab: the button is drawn with nothing to copy");
        }
        part.setContentDescription(fab.getContentDescription());
        part.setOnClickListener(view -> {
            Logger.printInfo(() -> "fab: handing the tap back to the button");
            fab.performClick();
        });
        part.setOnLongClickListener(view -> fab.performLongClick());
        return part;
    }

    private static View extra(Context context, int action, int size, ColorStateList tint) {
        try {
            ImageView part = part(context, size);
            part.setImageResource(t7.b.d(action));
            if (tint != null) {
                part.setImageTintList(tint);
            }
            String label = t7.b.h(action);
            part.setContentDescription(label == null ? null : label.replace('\n', ' '));
            part.setOnClickListener(view -> carryOut(view, action));
            Logger.printInfo(() -> "fab: part for action " + action + " (" + label + ")");
            return part;
        } catch (Throwable ex) {
            Logger.printInfo(() -> "fab: action " + action + " could not be drawn: " + ex);
            return null;
        }
    }

    private static ImageView part(Context context, int size) {
        ImageView part = new ImageView(context);
        part.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        int padding = (int) (size * 0.28f);
        part.setPadding(padding, padding, padding, padding);
        part.setScaleType(ImageView.ScaleType.FIT_CENTER);
        part.setClickable(true);
        part.setFocusable(true);
        return part;
    }

    /**
     * The one shape the parts share.
     *
     * <p>Drawn here rather than taken from the button: the button's own shape was there to copy
     * on one pass and not on the next, so which of the two was used depended on when the surface
     * happened to be built, and the ends did not match.
     */
    private static Drawable surfaceFor(Context context, View fab, int size) {
        Logger.printInfo(() -> "fab: rounding the surface by " + (size / 2f)
                + " at every corner");
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(size / 2f);
        shape.setColor(colourOfTheButton(fab));
        return shape;
    }

    private static int colourOfTheButton(View fab) {
        try {
            ColorStateList tint = fab.getBackgroundTintList();
            if (tint != null) {
                return tint.getDefaultColor();
            }
        } catch (Throwable ignored) {
            // Asked below instead.
        }
        try {
            android.util.TypedValue found = new android.util.TypedValue();
            if (fab.getContext().getTheme()
                    .resolveAttribute(android.R.attr.colorAccent, found, true)) {
                return found.data;
            }
        } catch (Throwable ignored) {
            // Fallen back on below.
        }
        return Color.parseColor("#333333");
    }

    private static View separator(Context context, int size, boolean vertical) {
        View line = new View(context);
        int thickness = (int) fromDp(context, SEPARATOR_DP);
        int length = (int) (size * 0.5f);
        line.setLayoutParams(vertical
                ? new LinearLayout.LayoutParams(length, thickness)
                : new LinearLayout.LayoutParams(thickness, length));
        line.setBackgroundColor(Color.argb(60, 255, 255, 255));
        return line;
    }

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

    /** Gives the button back what it draws with, and takes the surface away. */
    private static void giveBack(View fab) {
        try {
            if (itsIcon != null && fab instanceof ImageView) {
                ((ImageView) fab).setImageDrawable(itsIcon);
            }
            if (itsBackground != null) {
                fab.setBackground(itsBackground);
            }
        } catch (Throwable ex) {
            Logger.printDebug(() -> "fab: could not give the button back what it draws: " + ex);
        }
    }

    /** Whether the button is already being watched, so it is only watched once. */
    private static final java.util.Set<View> watched =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<View, Boolean>());

    private static int frames;
    private static int said;

    /**
     * Lays the surface over the button, every frame.
     *
     * <p>Its position already carries whatever is being done to hide it — what slides or scales
     * the button slides or scales where it is — so taking that is what makes the surface go with
     * it rather than pop.
     */
    private static void watch(View fab) {
        if (!watched.add(fab)) {
            return;
        }
        ViewTreeObserver.OnPreDrawListener each = () -> {
            try {
                over(fab);
            } catch (Throwable ex) {
                Logger.printDebug(() -> "fab: could not lay the surface over: " + ex);
            }
            return true;
        };
        fab.getViewTreeObserver().addOnPreDrawListener(each);
        Logger.printInfo(() -> "fab: watching the button");
    }

    private static void over(View fab) {
        ViewGroup parent = fab.getParent() instanceof ViewGroup
                ? (ViewGroup) fab.getParent() : null;
        if (parent == null) {
            return;
        }

        // Settings can change while the feed is up, and nothing else would notice.
        if (++frames % LOOK_AGAIN_EVERY == 0) {
            rebuild(fab);
        }

        View surface = parent.findViewWithTag(TAG);
        if (surface == null || surface.getWidth() == 0 || fab.getWidth() == 0) {
            return;
        }

        // The button keeps deciding and keeps hiding; it simply draws nothing while the
        // surface is standing in for it.
        if (fab.getBackground() != null) {
            itsBackground = fab.getBackground();
            fab.setBackground(null);
        }
        if (fab instanceof ImageView && ((ImageView) fab).getDrawable() != null) {
            itsIcon = ((ImageView) fab).getDrawable();
            ((ImageView) fab).setImageDrawable(null);
            // Sync draws its button after the surface is first laid over it, so the part
            // standing where it stands had nothing in it until now.
            fillIn(surface);
        }

        boolean vertical = SyncUpSettings.number(ORIENTATION, VERTICAL) == VERTICAL;
        float x = vertical
                ? fab.getX() + (fab.getWidth() - surface.getWidth()) / 2f
                : fab.getX() + fab.getWidth() - surface.getWidth();
        float y = vertical
                ? fab.getY() + fab.getHeight() - surface.getHeight()
                : fab.getY() + (fab.getHeight() - surface.getHeight()) / 2f;

        surface.setX(x);
        surface.setY(y);
        // Scaled about the part standing for the button, so what grows and shrinks is the
        // button itself rather than the middle of a row.
        surface.setPivotX(vertical ? surface.getWidth() / 2f : surface.getWidth());
        surface.setPivotY(vertical ? surface.getHeight() : surface.getHeight() / 2f);
        surface.setScaleX(fab.getScaleX());
        surface.setScaleY(fab.getScaleY());
        surface.setAlpha(fab.getAlpha());
        surface.setVisibility(fab.getVisibility());

        if (said < 6) {
            said++;
            Logger.printInfo(() -> "fab: over the button at " + x + "," + y
                    + " scale " + fab.getScaleX() + " alpha " + fab.getAlpha()
                    + " visibility " + fab.getVisibility());
        }
    }

    /** Puts the button's own icon into the part standing for it, once there is one to put. */
    private static void fillIn(View surface) {
        if (mainAction() != NOTHING || itsIcon == null || !(surface instanceof ViewGroup)) {
            return;
        }
        ViewGroup parts = (ViewGroup) surface;
        for (int at = 0; at < parts.getChildCount(); at++) {
            View part = parts.getChildAt(at);
            if (!Boolean.TRUE.equals(part.getTag(ITS_OWN)) || !(part instanceof ImageView)) {
                continue;
            }
            if (((ImageView) part).getDrawable() != null) {
                return;
            }
            ((ImageView) part).setImageDrawable(itsIcon.getConstantState() == null
                    ? itsIcon : itsIcon.getConstantState().newDrawable().mutate());
            Logger.printInfo(() -> "fab: the button's own icon has arrived, drawing it");
            return;
        }
    }

    private static float fromDp(Context context, float dp) {
        return dp * context.getResources().getDisplayMetrics().density;
    }
}
