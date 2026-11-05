package app.morphe.extension.syncforreddit.ui.lock;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * A PIN asked for before an account is opened, beside or instead of the fingerprint Sync asks
 * for.
 *
 * <p>Sync locks an account with a fingerprint and nothing else, which is of no use where someone
 * else's finger opens the same phone. This asks for a PIN at the two places Sync asks for a
 * fingerprint: choosing an account, and acting on one.
 *
 * <p>It is another thing to pass rather than a replacement, so the two settings together say
 * what is asked for: neither set asks nothing, as before; only Sync's asks for a fingerprint, as
 * before; only a PIN asks for the PIN; both ask for the fingerprint and then the PIN.
 *
 * <p>A PIN is kept as a hash with a salt beside it rather than as itself. Four digits do not
 * survive someone determined who already has the file, but they should not be sitting there in
 * the open either, and this costs nothing.
 *
 * @noinspection unused
 */
public final class AccountPin {
    /** The hash of the PIN for an account, where one is set. */
    public static final String PIN = "sync_up_pin";

    /** What the hash was salted with. */
    private static final String SALT = "sync_up_pin_salt";

    /** The name this gives when it asks Sync for an account's settings. */
    private static final String ASKING = "SyncUp";

    /** The fewest digits a PIN may be. */
    private static final int FEWEST = 4;

    /** How far the typing field is held off the edges of the dialog, in dp. */
    private static final int AN_EDGE = 20;

    private AccountPin() {}

    public static boolean isPatchIncluded() {
        // Overridden by patch.
        return false;
    }

    /** @return The settings of one account, which is where Sync keeps its own lock setting. */
    @Nullable
    private static SharedPreferences of(Context context, @Nullable String account) {
        try {
            if (context == null) {
                return null;
            }
            String named = account == null || account.isEmpty() ? t7.z.a() : account;
            return t7.z.h(context, named, ASKING);
        } catch (Throwable ex) {
            Logger.printInfo(() -> "pin: could not reach the settings of an account: " + ex);
            return null;
        }
    }

    /** @return Whether a PIN has been set for this account. */
    public static boolean isSet(Context context, @Nullable String account) {
        SharedPreferences store = of(context, account);
        String held = store == null ? null : store.getString(PIN, null);
        return held != null && !held.isEmpty();
    }

    /**
     * What to run in place of what Sync was going to run, so that the PIN is asked for first.
     *
     * <p>Answering with what was handed over, where no PIN is set, is what leaves an account
     * without one behaving exactly as it did.
     */
    public static Runnable guard(Context context, @Nullable String account, Runnable then) {
        if (then == null || !isPatchIncluded() || !isSet(context, account)) {
            return then;
        }
        return () -> ask(context, account, then);
    }

    /** Asks for the PIN, and runs what was waiting only once it is right. */
    private static void ask(Context context, @Nullable String account, Runnable then) {
        try {
            EditText typing = digits(context, "PIN");

            new AlertDialog.Builder(context)
                    .setTitle("Enter PIN")
                    .setView(around(context, typing))
                    .setPositiveButton("Unlock", (dialog, which) -> {
                        if (matches(context, account, typing.getText().toString())) {
                            then.run();
                        } else {
                            Utils.showToastShort("Wrong PIN");
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Exception ex) {
            // A lock that cannot be drawn must not become a door that cannot be opened.
            Logger.printInfo(() -> "pin: could not ask for the PIN: " + ex);
            then.run();
        }
    }

    private static boolean matches(Context context, @Nullable String account, String typed) {
        try {
            SharedPreferences store = of(context, account);
            if (store == null) {
                return true;
            }
            String held = store.getString(PIN, null);
            String salt = store.getString(SALT, "");
            return held != null && held.equals(hash(typed, salt));
        } catch (Exception ex) {
            Logger.printInfo(() -> "pin: could not check the PIN: " + ex);
            return false;
        }
    }

    /**
     * Wires the row on Sync's Security screen. Its screens are its own fragments, so this is
     * done as a screen finishes loading its rows; a screen without this row is left alone.
     */
    public static void settle(pa.d screen) {
        if (screen == null || !isPatchIncluded()) {
            return;
        }
        try {
            Preference row = screen.y(PIN);
            if (row == null) {
                return;
            }
            say(row);
            row.A0(tapped -> {
                choose(tapped.k(), () -> say(tapped));
                return true;
            });
        } catch (Exception ex) {
            Logger.printInfo(() -> "pin: could not wire the row: " + ex);
        }
    }

    private static void say(Preference row) {
        boolean already = isSet(row.k(), null);
        row.D0(already
                ? "Set. Asked for when this account is opened"
                : "Not set. Asked for when this account is opened, beside any fingerprint");
    }

    /** Sets, changes or clears the PIN of the account whose settings are being looked at. */
    public static void choose(Context context, Runnable afterwards) {
        try {
            boolean already = isSet(context, null);
            EditText typing = digits(context, already ? "New PIN" : "PIN");

            AlertDialog.Builder asking = new AlertDialog.Builder(context)
                    .setTitle(already ? "Change PIN" : "Set PIN")
                    .setView(around(context, typing))
                    .setPositiveButton("Save", (dialog, which) -> {
                        String typed = typing.getText().toString().trim();
                        if (typed.length() < FEWEST) {
                            Utils.showToastShort("A PIN is at least " + FEWEST + " digits");
                            return;
                        }
                        keep(context, typed);
                        afterwards.run();
                    })
                    .setNegativeButton("Cancel", null);

            if (already) {
                asking.setNeutralButton("Remove", (dialog, which) -> {
                    clear(context);
                    afterwards.run();
                });
            }
            asking.show();
        } catch (Exception ex) {
            Logger.printInfo(() -> "pin: could not offer to set a PIN: " + ex);
        }
    }

    private static void keep(Context context, String pin) {
        try {
            SharedPreferences store = of(context, null);
            if (store == null) {
                return;
            }
            byte[] grain = new byte[16];
            new SecureRandom().nextBytes(grain);
            String salt = toHex(grain);
            store.edit().putString(SALT, salt).putString(PIN, hash(pin, salt)).apply();
            Utils.showToastShort("PIN set");
        } catch (Exception ex) {
            Logger.printInfo(() -> "pin: could not keep the PIN: " + ex);
        }
    }

    private static void clear(Context context) {
        try {
            SharedPreferences store = of(context, null);
            if (store != null) {
                store.edit().remove(PIN).remove(SALT).apply();
                Utils.showToastShort("PIN removed");
            }
        } catch (Exception ex) {
            Logger.printInfo(() -> "pin: could not remove the PIN: " + ex);
        }
    }

    private static String hash(String pin, String salt) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return toHex(digest.digest((salt + pin).getBytes(StandardCharsets.UTF_8)));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder written = new StringBuilder(bytes.length * 2);
        for (byte each : bytes) {
            written.append(Character.forDigit((each >> 4) & 0xF, 16));
            written.append(Character.forDigit(each & 0xF, 16));
        }
        return written.toString();
    }

    private static EditText digits(Context context, String hint) {
        EditText typing = new EditText(context);
        typing.setHint(hint);
        typing.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        typing.setSingleLine(true);
        return typing;
    }

    private static FrameLayout around(Context context, EditText typing) {
        FrameLayout holding = new FrameLayout(context);
        int edge = Math.round(AN_EDGE * context.getResources().getDisplayMetrics().density);
        holding.setPadding(edge, edge / 2, edge, 0);
        holding.addView(typing);
        return holding;
    }
}
