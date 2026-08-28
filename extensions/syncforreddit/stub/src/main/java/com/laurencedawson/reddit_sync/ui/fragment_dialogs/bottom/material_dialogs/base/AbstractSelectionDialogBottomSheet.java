package com.laurencedawson.reddit_sync.ui.fragment_dialogs.bottom.material_dialogs.base;

/**
 * The base of Sync's list style bottom sheets. Unlike the post menu, these build their rows
 * from code, so an extension can add one by registering another option.
 *
 * <p>Only the members the extension needs are declared. The real class extends a fragment,
 * which is irrelevant here because instances are only ever passed in, never constructed.
 */
public abstract class AbstractSelectionDialogBottomSheet {
    /** One row of the sheet. */
    public static class h {
        public h(int iconResource, String title) {
            throw new UnsupportedOperationException("Stub");
        }
    }

    /** Registers an option and returns it. */
    public final h t4(h option) {
        throw new UnsupportedOperationException("Stub");
    }
}
