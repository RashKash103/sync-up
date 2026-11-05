package app.morphe.extension.syncforreddit.api;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.preference.Preference;

import app.morphe.extension.shared.Logger;

/**
 * Lets the four things the app identifies itself with be typed into the settings.
 *
 * <p>Sync's own screens have no row for typing into, so each of these is an ordinary row that
 * opens a dialog when tapped, the same way the translation screen asks for a service key.
 *
 * <p>Sync's settings screens are its own fragments, so unlike the translation screen there is
 * nowhere of ours to run from. This is done as a screen finishes loading its rows, which every
 * screen passes through; a screen without these rows is left alone.
 *
 * @noinspection unused
 */
public final class ClientRows {
    /** How far the typing field is held off the edges of the dialog, in dp. */
    private static final int AN_EDGE = 20;

    private ClientRows() {}

    public static boolean isPatchIncluded() {
        // Overridden by patch.
        return false;
    }

    public static void settle(pa.d screen) {
        if (screen == null || !isPatchIncluded()) {
            return;
        }
        try {
            if (screen.y(ClientSettings.CLIENT_ID) == null) {
                // Some other screen of the settings; nothing here belongs to it.
                return;
            }

            // The two ids say only whether they are set, since what they are is of no use at a
            // glance and they are the one part of this worth not putting on the screen.
            typedInto(screen, ClientSettings.CLIENT_ID, "OAuth client ID",
                    "The id Reddit was asked to issue. Without one the app cannot sign in.", true);
            typedInto(screen, ClientSettings.IMGUR_CLIENT_ID, "Imgur client ID",
                    "What uploading a picture identifies as.", true);
            typedInto(screen, ClientSettings.REDIRECT_URI, "Redirect URI",
                    "Where Reddit sends you back to after signing in.", false);
            typedInto(screen, ClientSettings.USER_AGENT, "User agent",
                    "What the app calls itself when it asks Reddit for anything.", false);
        } catch (Exception ex) {
            Logger.printInfo(() -> "client: could not make the rows something to type into: " + ex);
        }
    }

    /**
     * @param hide Whether the row says only that something is set rather than what it is.
     */
    private static void typedInto(pa.d screen, String key, String title, String describing,
                                  boolean hide) {
        try {
            Preference row = screen.y(key);
            if (row == null) {
                return;
            }

            say(row, key, describing, hide);
            row.A0(tapped -> {
                ask(tapped, key, title, describing, hide);
                return true;
            });
        } catch (Exception ex) {
            Logger.printInfo(() -> "client: could not make " + key + " typeable: " + ex);
        }
    }

    /**
     * What the row reads under its name. Something given while patching is said to be in use
     * rather than called unset: the app is working on it, and a row reading "not set" beside a
     * signed-in account would read as a fault.
     */
    private static void say(Preference row, String key, String describing, boolean hide) {
        String set = ClientSettings.text(key, "");
        if (!set.isEmpty()) {
            row.D0(hide ? "Set here. " + describing : set);
            return;
        }

        String patched = ClientSettings.bakedFor(key);
        if (!patched.isEmpty()) {
            row.D0(hide
                    ? "Using what was given when patching. " + describing
                    : patched + " (given when patching)");
            return;
        }
        row.D0("Not set. " + describing);
    }

    private static void ask(Preference row, String key, String title, String describing,
                            boolean hide) {
        Context context = row.k();
        EditText typing = new EditText(context);
        typing.setText(ClientSettings.text(key, ""));
        typing.setSelection(typing.getText().length());
        typing.setHint(describing);
        typing.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        typing.setSingleLine(true);

        // Held off the edges of the dialog, which a bare field is not.
        FrameLayout around = new FrameLayout(context);
        int edge = Math.round(AN_EDGE * context.getResources().getDisplayMetrics().density);
        around.setPadding(edge, edge / 2, edge, 0);
        around.addView(typing);

        new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(around)
                .setPositiveButton("Save", (dialog, which) -> {
                    try {
                        ClientSettings.store().edit()
                                .putString(key, typing.getText().toString().trim())
                                .apply();
                    } catch (Exception ex) {
                        Logger.printInfo(() -> "client: could not keep " + key + ": " + ex);
                    }
                    say(row, key, describing, hide);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
