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
     * Where our actions begin in that setting. The three Sync already meant keep the numbers
     * they had, and ours follow straight on: Sync looks the number up in its own list of names
     * by position, so anything past the end of it is a crash rather than a number it ignores.
     */
    private static final int OURS_START_AT = 3;

    /**
     * The actions offered for the button itself, in the order they are offered in, which is what
     * turns the number stored into the number the action is known by.
     *
     * <p>Two are left out: Hide read, which the button already does by itself, and Sort, which
     * has a name and an icon but is never dispatched, so choosing it did nothing. This has to
     * match the list the settings are built from.
     */
    private static final int[] OFFERED = {
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24,
    };

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

    /**
     * What is remembered about one button.
     *
     * <p>Held against the button rather than in one place: a second window has a second button,
     * and one set of these between them left each answering for the other — a change made with
     * one window up did nothing to the other, and what one button was drawn with was put on the
     * other.
     */
    private static final class Its {
        String built;
        Drawable icon;
        Drawable background;
        boolean listening;
    }

    private static final java.util.Map<View, Its> theirs =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<View, Its>());

    private static Its of(View fab) {
        synchronized (theirs) {
            Its its = theirs.get(fab);
            if (its == null) {
                its = new Its();
                theirs.put(fab, its);
            }
            return its;
        }
    }

    /** Rebuilds the moment a setting changes, rather than when the feed next draws. */
    private static void listen(View fab) {
        Its its = of(fab);
        if (its.listening) {
            return;
        }
        its.listening = true;
        Runnable rebuild = () -> fab.post(() -> {
            try {
                // Forgotten first, so the answer is built again rather than recognised as the
                // one already standing there.
                of(fab).built = null;
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
        Its its = of(fab);
        if (now.equals(its.built) && parent.findViewWithTag(TAG) != null) {
            return;
        }
        its.built = now;
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
        int at = held - OURS_START_AT;
        if (at < 0 || at >= OFFERED.length) {
            // One of Sync's own three, or nothing set: the button keeps doing what it did.
            return NOTHING;
        }
        return OFFERED[at];
    }

    /**
     * @return The actions standing beside the button, furthest first.
     *
     * <p>The button's own action is drawn last, at the end nearest where the button was, so the
     * first slot has to be drawn immediately before it and the rest further out again. That is
     * the reverse of the order they are set in.
     */
    private static List<Integer> chosen() {
        List<Integer> actions = new ArrayList<>();
        for (int slot = SLOTS; slot >= 1; slot--) {
            int action = SyncUpSettings.number(SLOT + slot, NOTHING);
            if (action == NOTHING || actions.contains(action)) {
                continue;
            }
            actions.add(action);
        }
        return actions;
    }

    private static LinearLayout build(View fab, List<Integer> actions) {
        Context context = fab.getContext();
        boolean vertical = SyncUpSettings.number(ORIENTATION, VERTICAL) == VERTICAL;
        boolean separators = SyncUpSettings.flag(SEPARATORS, true);

        Its its = of(fab);
        if (its.icon == null && fab instanceof ImageView) {
            its.icon = ((ImageView) fab).getDrawable();
        }
        if (its.background == null) {
            its.background = fab.getBackground();
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
        Its its = of(fab);
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
                + (its.icon == null ? "nothing yet" : "its own icon"));
        ImageView part = part(context, size);
        part.setTag(ITS_OWN, Boolean.TRUE);
        if (its.icon != null) {
            part.setImageDrawable(its.icon.getConstantState() == null
                    ? its.icon : its.icon.getConstantState().newDrawable().mutate());
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
        float radius = cornerOf(fab, size);
        Logger.printInfo(() -> "fab: rounding the surface by " + radius + " at every corner");
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(radius);
        shape.setColor(colourOfTheButton(fab));
        return shape;
    }

    /**
     * @return The corner the button itself is drawn with.
     *
     * <p>Asked of the button's own outline rather than assumed to be half its height. A button
     * drawn as a rounded square next to an end rounded to a semicircle is what made the two
     * ends of the surface look nothing like each other.
     */
    private static float cornerOf(View fab, int size) {
        try {
            Drawable its = of(fab).background;
            if (its != null) {
                android.graphics.Outline outline = new android.graphics.Outline();
                its.setBounds(0, 0, size, size);
                its.getOutline(outline);
                float radius = outline.getRadius();
                if (radius > 0 && radius <= size) {
                    Logger.printInfo(() -> "fab: the button is drawn with a corner of " + radius);
                    return radius;
                }
            }
        } catch (Throwable ex) {
            Logger.printDebug(() -> "fab: the button will not say what corner it has: " + ex);
        }
        Logger.printInfo(() -> "fab: the button says nothing about its corner, rounding fully");
        return size / 2f;
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
            Its its = of(fab);
            fab.setAlpha(1f);
            if (its.icon != null && fab instanceof ImageView) {
                ((ImageView) fab).setImageDrawable(its.icon);
            }
            if (its.background != null) {
                fab.setBackground(its.background);
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
        // surface is standing in for it. Made see-through as well as emptied: taking its
        // drawing away a frame at a time left it showing through in between, which is the
        // blank circle over the surface and the flicker beside it.
        Its its = of(fab);
        if (fab.getAlpha() != 0f) {
            fab.setAlpha(0f);
        }
        if (fab.getBackground() != null) {
            its.background = fab.getBackground();
            fab.setBackground(null);
        }
        if (fab instanceof ImageView && ((ImageView) fab).getDrawable() != null) {
            its.icon = ((ImageView) fab).getDrawable();
            ((ImageView) fab).setImageDrawable(null);
            // Sync draws its button after the surface is first laid over it, so the part
            // standing where it stands had nothing in it until now.
            fillIn(fab, surface);
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
        // Not the button's own fade: it is held at nothing so that it never shows through, so
        // what is left to follow is how big it is and whether it is there at all.
        surface.setAlpha(Math.min(1f, Math.abs(fab.getScaleX())));
        surface.setVisibility(fab.getVisibility());

        if (said < 6) {
            said++;
            Logger.printInfo(() -> "fab: over the button at " + x + "," + y
                    + " scale " + fab.getScaleX() + " alpha " + fab.getAlpha()
                    + " visibility " + fab.getVisibility());
        }
    }

    /** Puts the button's own icon into the part standing for it, once there is one to put. */
    private static void fillIn(View fab, View surface) {
        Its its = of(fab);
        if (mainAction() != NOTHING || its.icon == null || !(surface instanceof ViewGroup)) {
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
            ((ImageView) part).setImageDrawable(its.icon.getConstantState() == null
                    ? its.icon : its.icon.getConstantState().newDrawable().mutate());
            Logger.printInfo(() -> "fab: the button's own icon has arrived, drawing it");
            return;
        }
    }

    private static float fromDp(Context context, float dp) {
        return dp * context.getResources().getDisplayMetrics().density;
    }
}
