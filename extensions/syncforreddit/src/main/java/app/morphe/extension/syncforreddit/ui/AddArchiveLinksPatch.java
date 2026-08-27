/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.syncforreddit.ui;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;

import com.laurencedawson.reddit_sync.ui.fragment_dialogs.bottom.material_dialogs.base.AbstractSelectionDialogBottomSheet;
import com.laurencedawson.reddit_sync.ui.views.core.MaterialRow;

import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Adds archive options to the menu behind a post's overflow button, next to Sync's own
 * "Open in browser" row.
 *
 * <p>Patcheddit's Boost patch builds that menu from a list in code, so it appends to the list.
 * Sync's menu is a layout of MaterialRow views dispatched by view id, and adding a row there
 * would mean introducing a new id resource. The rows are added at runtime instead, which keeps
 * this to a bytecode patch and leaves Sync's resources untouched.
 *
 * @noinspection unused
 */
public class AddArchiveLinksPatch {
    private static final String WAYBACK_MACHINE = "https://web.archive.org/web/";
    private static final String ARCHIVE_TODAY = "https://archive.today/newest/";

    /** Sync's id for the "Open in browser" row, which the new rows are placed after. */
    private static final String ANCHOR_ID = "more_external";

    private static final String ICON = "outline_history_24";
    private static final String FALLBACK_ICON = "outline_open_in_browser_24";

    /**
     * What each option this patch added should open. Weak, so an option is forgotten along
     * with the sheet that held it.
     */
    private static final Map<Object, String> optionTargets = new WeakHashMap<>();

    private AddArchiveLinksPatch() {}

    /**
     * Adds the same two entries to Sync's link options sheet. That sheet builds its rows from
     * code rather than from a layout, so an option is registered rather than a view inserted.
     */
    public static void addLinkOptions(AbstractSelectionDialogBottomSheet sheet, String url) {
        try {
            if (sheet == null || url == null || url.isEmpty()) {
                return;
            }

            int icon = iconFor(Utils.getContext());
            addLinkOption(sheet, icon, "Open in Wayback Machine", WAYBACK_MACHINE + url);
            addLinkOption(sheet, icon, "Open in archive.today", ARCHIVE_TODAY + url);
        } catch (Exception ex) {
            Logger.printException(() -> "Could not add the archive link options", ex);
        }
    }

    private static void addLinkOption(AbstractSelectionDialogBottomSheet sheet, int icon,
                                      String title, String target) {
        AbstractSelectionDialogBottomSheet.h option =
                sheet.t4(new AbstractSelectionDialogBottomSheet.h(icon, title));
        optionTargets.put(option, target);
    }

    /**
     * @return Whether this was one of the options added here, in which case Sync should not go
     *         on to handle it.
     */
    public static boolean handleLinkOption(Object option) {
        try {
            String target = optionTargets.get(option);
            if (target == null) {
                return false;
            }
            open(Utils.getContext(), target);
            return true;
        } catch (Exception ex) {
            Logger.printException(() -> "Could not open the archive link", ex);
            return false;
        }
    }

    private static int iconFor(Context context) {
        if (context == null) {
            return 0;
        }
        Resources resources = context.getResources();
        String packageName = context.getPackageName();

        int icon = resources.getIdentifier(ICON, "drawable", packageName);
        return icon != 0 ? icon : resources.getIdentifier(FALLBACK_ICON, "drawable", packageName);
    }

    public static void addArchiveRows(View root, String url) {
        // A menu that loses two rows is a much better outcome than one that crashes the app.
        try {
            if (root == null || url == null || url.isEmpty()) {
                return;
            }

            Context context = root.getContext();
            Resources resources = context.getResources();
            String packageName = context.getPackageName();

            int anchorId = resources.getIdentifier(ANCHOR_ID, "id", packageName);
            if (anchorId == 0) {
                Logger.printDebug(() -> "Could not find the " + ANCHOR_ID + " row");
                return;
            }

            View anchor = root.findViewById(anchorId);
            if (anchor == null || !(anchor.getParent() instanceof ViewGroup)) {
                return;
            }

            ViewGroup parent = (ViewGroup) anchor.getParent();
            int index = parent.indexOfChild(anchor);

            int icon = resources.getIdentifier(ICON, "drawable", packageName);
            if (icon == 0) {
                icon = resources.getIdentifier(FALLBACK_ICON, "drawable", packageName);
            }

            parent.addView(row(context, icon, "Open in Wayback Machine", WAYBACK_MACHINE + url),
                    index + 1);
            parent.addView(row(context, icon, "Open in archive.today", ARCHIVE_TODAY + url),
                    index + 2);
        } catch (Exception ex) {
            Logger.printException(() -> "Could not add the archive rows", ex);
        }
    }

    private static MaterialRow row(Context context, int icon, String title, final String target) {
        MaterialRow row = new MaterialRow(context);
        if (icon != 0) {
            row.d(icon);
        }
        row.k(title);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                open(view.getContext(), target);
            }
        });
        return row;
    }

    private static void open(Context context, String target) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(target));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ex) {
            Logger.printException(() -> "Could not open " + target, ex);
        }
    }
}
