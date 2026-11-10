package app.morphe.extension.syncforreddit.redirects;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.syncforreddit.redirects.imgur.ImgurUnblock;
import app.morphe.extension.syncforreddit.redirects.imgur.RimgoInstances;
import app.morphe.extension.syncforreddit.settings.SyncUpSettings;

/**
 * The redirect settings, as screens of Sync's own settings.
 *
 * <p>Every screen Sync has is a fragment of this shape: it says which screen of preferences to
 * load and lets the app do the rest, which is what keeps it looking like the others. Sync finds
 * the fragment for a screen by an id, in a list of its own that nothing outside it can be
 * reached from, so the patch answers that lookup with one of these.
 *
 * <p><b>A screen is a class of its own</b> — the five below — rather than one class told which
 * screen it is. A fragment is rebuilt from its class name when the screen rotates or the
 * activity comes back, and nothing calls the lookup again at that point, so a class that had
 * been handed its id would come back not knowing which screen it was and load the wrong one.
 * Five near-empty subclasses are the price of a screen that survives being turned sideways.
 *
 * <p>There are more screens than the settings would strictly need because the alternative is
 * one screen holding three routes' worth of switches, addresses, instance lists and
 * certificates, in which nothing would be findable. A route gets a screen.
 *
 * @noinspection unused
 */
public abstract class RedirectsScreen extends pa.d {
    // region The screens, and their ids.

    /**
     * Each screen's id. They match the integers the patch adds to the app's resources, and have
     * to be numbers no screen of Sync's own uses.
     */
    public static final int REDIRECTS = 8200;

    public static final int IMGUR = 8210;

    public static final int DUCKDUCKGO = 8211;

    public static final int RIMGO = 8212;

    public static final int PROXY = 8213;

    /** The front screen: the master switch, and the way to each service. */
    public static class Front extends RedirectsScreen {
        @Override
        protected int which() {
            return REDIRECTS;
        }
    }

    public static class Imgur extends RedirectsScreen {
        @Override
        protected int which() {
            return IMGUR;
        }
    }

    public static class DuckDuckGo extends RedirectsScreen {
        @Override
        protected int which() {
            return DUCKDUCKGO;
        }
    }

    public static class Rimgo extends RedirectsScreen {
        @Override
        protected int which() {
            return RIMGO;
        }
    }

    public static class Proxy extends RedirectsScreen {
        @Override
        protected int which() {
            return PROXY;
        }
    }

    /** @return Which screen this one is. */
    protected abstract int which();

    /** What each one loads, by the name the patch writes the file under. */
    private static String xmlFor(int id) {
        switch (id) {
            case REDIRECTS: return "cat_sync_up_redirects";
            case IMGUR: return "cat_sync_up_redirects_imgur";
            case DUCKDUCKGO: return "cat_sync_up_redirects_ddg";
            case RIMGO: return "cat_sync_up_redirects_rimgo";
            case PROXY: return "cat_sync_up_redirects_proxy";
            default: return null;
        }
    }

    /**
     * The name at the top of the screen, looked up the same way and from the same list, which
     * throws for an id it does not know just as the lookup for the fragment does.
     *
     * @return The name where the id is one of ours, and null for every other.
     */
    @Nullable
    public static String titleFor(int id) {
        switch (id) {
            case REDIRECTS: return "Redirects";
            case IMGUR: return "Imgur";
            case DUCKDUCKGO: return "DuckDuckGo proxy";
            case RIMGO: return "Rimgo";
            case PROXY: return "Proxy";
            default: return null;
        }
    }

    /**
     * Called where Sync looks up the fragment for a screen.
     *
     * @return The screen where the id is ours, and null for every other, which leaves Sync to
     *         look the id up as it always did.
     */
    @Nullable
    public static pa.d screenFor(int id) {
        try {
            RedirectsScreen screen;
            switch (id) {
                case REDIRECTS: screen = new Front(); break;
                case IMGUR: screen = new Imgur(); break;
                case DUCKDUCKGO: screen = new DuckDuckGo(); break;
                case RIMGO: screen = new Rimgo(); break;
                case PROXY: screen = new Proxy(); break;
                default: return null;
            }
            // Given the same argument Sync gives its own, which is what a search term is
            // passed in.
            screen.a3(new Bundle());
            return screen;
        } catch (Exception ex) {
            Logger.printInfo(() -> "redirects: could not open screen " + id + ": " + ex);
            return null;
        }
    }

    // endregion

    // region The rows that are not settings of their own, named here and in the patch.

    /**
     * A row that leads to a screen is keyed by that screen's id, as text.
     *
     * <p>Not by anything we could choose: Sync's own row for a screen reads the id out of the
     * XML and calls {@code setKey(Integer.toString(it))} in its constructor, so whatever
     * {@code android:key} the file gives it is thrown away before anything can look it up. A
     * row asked for by a name of ours is simply not found, and turning off a row that was never
     * found turns nothing off — which is why these three went on answering a tap with the whole
     * thing switched off.
     */
    private static String rowFor(int screen) {
        return Integer.toString(screen);
    }

    static final String INSTANCES = "sync_up_redirect_imgur_rimgo_instances";

    static final String TRY_THE_PROXY = "sync_up_redirect_imgur_proxy_try";

    // endregion

    /** How far a typed field is held off the edges of its dialog, before the screen's density. */
    private static final int AN_EDGE = 20;

    /** How tall a field for more than one line starts out. */
    private static final int MANY_LINES = 4;

    /** Legible on either theme, which a darker green and a darker red are not. */
    private static final int WORKING_GREEN = 0xFF43A047;

    private static final int FAILING_RED = 0xFFE53935;

    /**
     * Kept for as long as the screen is: the settings keep only a weak hold on a listener, and
     * one that is not kept anywhere is collected and stops being told anything.
     */
    private final List<SharedPreferences.OnSharedPreferenceChangeListener> watching =
            new ArrayList<>();

    @Override
    public void C3(Bundle state, String rootKey) {
        String xml = xmlFor(which());
        t3(ResourceUtils.getIdentifier(ResourceType.XML, xml));
        super.C3(state, rootKey);

        try {
            switch (which()) {
                case REDIRECTS:
                    theFrontScreen();
                    break;
                case IMGUR:
                    imgur();
                    break;
                case RIMGO:
                    rimgo();
                    break;
                case PROXY:
                    proxy();
                    break;
                default:
                    // The DuckDuckGo screen is a switch and a word of explanation, both of
                    // which the app draws on its own.
                    break;
            }
        } catch (Exception ex) {
            Logger.printInfo(() -> "redirects: could not set up screen " + which() + ": " + ex);
        }
    }

    // region The front screen.

    private void theFrontScreen() {
        follow(RedirectSettings.ON, this::fadeWhatIsOff);
        fadeWhatIsOff();
    }

    private void fadeWhatIsOff() {
        enable(new String[]{rowFor(IMGUR)}, SyncUpSettings.flag(RedirectSettings.ON, true));
    }

    // endregion

    // region Imgur.

    private void imgur() {
        follow(RedirectSettings.IMGUR, this::showWhatApplies);
        showWhatApplies();
        sayWhatAnswered();
    }

    /** The routes are of no use while Imgur itself is not being redirected. */
    private void showWhatApplies() {
        boolean on = SyncUpSettings.flag(RedirectSettings.IMGUR, false);
        enable(new String[]{
                RedirectSettings.IMGUR_WHEN,
                rowFor(DUCKDUCKGO),
                rowFor(RIMGO),
                rowFor(PROXY),
                RedirectSettings.LAST_USED,
        }, on);
    }

    /**
     * Says what answered the last Imgur request. Worth a row: with three routes and a fallback
     * behind them, which one is actually carrying the pictures is otherwise only knowable by
     * reading a log.
     */
    private void sayWhatAnswered() {
        Preference row = y(RedirectSettings.LAST_USED);
        if (row == null) {
            return;
        }
        sayLastUsed(row);
        row.A0(tapped -> {
            sayLastUsed(tapped);
            return true;
        });
    }

    private static void sayLastUsed(Preference row) {
        String last = ImgurUnblock.lastUsed();
        row.D0(last.isEmpty() ? "Nothing has been asked for yet" : last);
    }

    // endregion

    // region Rimgo.

    private void rimgo() {
        follow(RedirectSettings.RIMGO, this::showRimgoRows);
        showRimgoRows();

        Preference instances = y(INSTANCES);
        if (instances != null) {
            sayHowManyInstances(instances);
            instances.A0(tapped -> {
                chooseInstances(tapped);
                return true;
            });
        }

        Preference published = y(RedirectSettings.RIMGO_PUBLISHED);
        if (published != null) {
            sayAboutTheList(published);
            published.A0(tapped -> {
                tapped.D0("Fetching…");
                RimgoInstances.refresh(() -> onTheScreen(() -> {
                    sayAboutTheList(tapped);
                    Preference count = y(INSTANCES);
                    if (count != null) {
                        sayHowManyInstances(count);
                    }
                }));
                return true;
            });
        }

        // The list is what everything else here is chosen from, so it is fetched the first time
        // this screen is opened rather than waiting to be asked for. Fetching it chooses
        // nothing: an instance is asked nothing until it has been ticked.
        if (RimgoInstances.listIsStale()) {
            RimgoInstances.refresh(() -> onTheScreen(() -> {
                Preference list = y(RedirectSettings.RIMGO_PUBLISHED);
                if (list != null) {
                    sayAboutTheList(list);
                }
                Preference count = y(INSTANCES);
                if (count != null) {
                    sayHowManyInstances(count);
                }
            }));
        }
    }

    private void showRimgoRows() {
        boolean on = SyncUpSettings.flag(RedirectSettings.RIMGO, false);
        enable(new String[]{
                INSTANCES,
                RedirectSettings.RIMGO_PUBLISHED,
                RedirectSettings.RIMGO_CHECK,
        }, on);
    }

    private void sayAboutTheList(Preference row) {
        int known = RimgoInstances.published().size();
        if (known == 0) {
            row.D0("Not fetched yet");
            return;
        }
        row.D0(known + " published, " + howLongAgo(RimgoInstances.publishedAt()));
    }

    private void sayHowManyInstances(Preference row) {
        int chosen = RimgoInstances.chosen().size();
        int known = RimgoInstances.all().size();
        if (chosen == 0) {
            row.D0(known == 0
                    ? "None chosen; fetch the published list first"
                    : "None chosen, of " + known + " known");
            return;
        }
        row.D0(chosen + " chosen of " + known + ", " + RimgoInstances.workingCount()
                + " working");
    }

    /**
     * The instances, as a list to tick rather than as rows of their own.
     *
     * <p>Rows would have been the obvious thing and are not available: a screen here is built
     * from an XML file at the moment it is loaded, and how many instances there are is only
     * known afterwards. A dialog of our own also means the colour and the mark beside each one
     * are ours to draw, rather than something to talk a settings row into.
     */
    private void chooseInstances(Preference row) {
        Context context = row.k();
        List<String> known = RimgoInstances.all();

        LinearLayout listed = new LinearLayout(context);
        listed.setOrientation(LinearLayout.VERTICAL);
        int edge = Math.round(AN_EDGE * context.getResources().getDisplayMetrics().density);
        listed.setPadding(edge, edge / 2, edge, 0);

        TextView how = new TextView(context);
        how.setText("Tap to choose. Long-press one for a client certificate.");
        how.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        how.setTextColor(Color.GRAY);
        listed.addView(how);

        List<Runnable> redraw = new ArrayList<>();
        if (known.isEmpty()) {
            TextView nothing = new TextView(context);
            nothing.setText("No instances are known yet. Fetch the published list, or add one.");
            listed.addView(nothing);
        }
        for (String instance : known) {
            redraw.add(addInstanceRow(context, listed, instance));
        }

        ScrollView scrolling = new ScrollView(context);
        scrolling.addView(listed);

        AlertDialog showing = new AlertDialog.Builder(context)
                .setTitle("Rimgo instances")
                .setView(scrolling)
                .setPositiveButton("Done", (dialog, tapped) -> sayHowManyInstances(row))
                .setNeutralButton("Check all", null)
                .setNegativeButton("Add…", null)
                .create();
        showing.show();

        Runnable drawEveryRow = () -> onTheScreen(() -> {
            for (Runnable each : redraw) {
                each.run();
            }
            sayHowManyInstances(row);
        });

        // Taken over after showing, so that neither of these closes the dialog: checking leaves
        // the answers to be read, and adding comes straight back to the list.
        showing.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(tapped -> RimgoInstances.checkAll(drawEveryRow));
        showing.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(tapped -> {
            showing.dismiss();
            addAnInstance(row);
        });

        // Asked as it opens, where that is wanted, so that the colours mean something by the
        // time anyone has finished reading the list. Only the ones nothing is known about yet,
        // so that opening the list again does not ask every server a second time.
        if (SyncUpSettings.flag(RedirectSettings.RIMGO_CHECK, true)) {
            RimgoInstances.checkWhatIsUnknown(drawEveryRow);
        }
    }

    /**
     * One instance: a box to tick, its address, and what it was last found to be.
     *
     * @return What to run to draw its state again, once something has been found out about it.
     */
    private Runnable addInstanceRow(Context context, LinearLayout into, String instance) {
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);

        CheckBox ticked = new CheckBox(context);
        ticked.setChecked(RimgoInstances.isChosen(instance));

        TextView named = new TextView(context);
        named.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        named.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView state = new TextView(context);
        state.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);

        line.addView(ticked);
        line.addView(named);
        line.addView(state);
        into.addView(line);

        Runnable draw = () -> {
            named.setText(RedirectSettings.hasCertificate(instance)
                    ? hostOf(instance) + "  (certificate)" : hostOf(instance));
            switch (RimgoInstances.healthOf(instance)) {
                case WORKING:
                    state.setText("● working");
                    state.setTextColor(WORKING_GREEN);
                    break;
                case FAILING:
                    state.setText("● not working");
                    state.setTextColor(FAILING_RED);
                    break;
                case CHECKING:
                    state.setText("checking…");
                    state.setTextColor(Color.GRAY);
                    break;
                default:
                    state.setText("");
                    break;
            }
        };
        draw.run();

        ticked.setOnCheckedChangeListener((button, checked) ->
                RimgoInstances.choose(instance, checked));
        // Tapping the line is easier than finding the box.
        named.setOnClickListener(tapped -> ticked.setChecked(!ticked.isChecked()));
        named.setOnLongClickListener(tapped -> {
            whatCanBeDoneTo(context, instance, line, draw);
            return true;
        });
        return draw;
    }

    /**
     * What can be done to one instance. A certificate can be attached to any of them; only one
     * typed in here can be taken out again, since a published one would come back the next time
     * the list is fetched and is left to be unticked instead.
     */
    private void whatCanBeDoneTo(Context context, String instance, View line, Runnable draw) {
        boolean has = RedirectSettings.hasCertificate(instance);
        boolean own = RimgoInstances.own().contains(instance);

        List<String> choices = new ArrayList<>();
        choices.add(has ? "Replace the client certificate" : "Attach a client certificate");
        if (has) {
            choices.add("Clear the client certificate");
        }
        if (own) {
            choices.add("Remove this instance");
        }

        new AlertDialog.Builder(context)
                .setTitle(hostOf(instance))
                .setItems(choices.toArray(new String[0]), (dialog, which) -> {
                    String chosen = choices.get(which);
                    if (chosen.startsWith("Remove")) {
                        RimgoInstances.remove(instance);
                        RedirectSettings.clearCertificateFor(instance);
                        line.setVisibility(View.GONE);
                        return;
                    }
                    if (chosen.startsWith("Clear")) {
                        RedirectSettings.clearCertificateFor(instance);
                        draw.run();
                        return;
                    }
                    askForACertificate(context, instance, draw);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * The certificate for one instance, pasted in.
     *
     * <p>Said plainly on the dialog, because it is the whole reason this is per instance: a
     * server decides when to ask for a client certificate, and the certificate names whoever
     * holds it. One kept for Rimgo in general would be handed to whichever instance asked.
     */
    private void askForACertificate(Context context, String instance, Runnable draw) {
        int edge = Math.round(AN_EDGE * context.getResources().getDisplayMetrics().density);

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(edge, edge / 2, edge, 0);

        TextView why = new TextView(context);
        why.setText("Offered to " + hostOf(instance) + " and to no other instance.");
        why.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        why.setTextColor(Color.GRAY);

        EditText pasted = new EditText(context);
        pasted.setHint("A PKCS#12 as base64, or the PEM blocks");
        pasted.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        pasted.setMinLines(MANY_LINES);

        EditText password = new EditText(context);
        password.setHint("Certificate password, if it has one");
        password.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setSingleLine(true);

        box.addView(why);
        box.addView(pasted);
        box.addView(password);

        ScrollView scrolling = new ScrollView(context);
        scrolling.addView(box);

        AlertDialog.Builder asking = new AlertDialog.Builder(context)
                .setTitle("Client certificate")
                .setView(scrolling)
                .setPositiveButton("Save", (dialog, which) -> {
                    RedirectSettings.setCertificateFor(instance,
                            pasted.getText().toString().trim(),
                            password.getText().toString());
                    draw.run();
                })
                .setNegativeButton("Cancel", null);
        if (RedirectSettings.hasCertificate(instance)) {
            asking.setNeutralButton("Clear", (dialog, which) -> {
                RedirectSettings.clearCertificateFor(instance);
                draw.run();
            });
        }
        asking.show();
    }

    private void addAnInstance(Preference row) {
        Context context = row.k();
        EditText typing = new EditText(context);
        typing.setHint("rimgo.example.com");
        typing.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        typing.setSingleLine(true);

        new AlertDialog.Builder(context)
                .setTitle("Add a Rimgo instance")
                .setMessage("The address it is at. It is added to the list and ticked.")
                .setView(heldOffTheEdges(context, typing))
                .setPositiveButton("Add", (dialog, which) -> {
                    String added = RimgoInstances.add(typing.getText().toString());
                    if (added == null) {
                        new AlertDialog.Builder(context)
                                .setMessage("That is not an address an instance could be at.")
                                .setPositiveButton("Close", null)
                                .show();
                        return;
                    }
                    sayHowManyInstances(row);
                    chooseInstances(row);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // endregion

    // region The proxy.

    private void proxy() {
        follow(RedirectSettings.PROXY, this::showProxyRows);
        follow(RedirectSettings.PROXY_MTLS, this::showProxyRows);
        showProxyRows();

        typedInto(RedirectSettings.PROXY_ADDRESS, "Proxy address", "host:port", false, false);
        typedInto(RedirectSettings.PROXY_USER, "Username", "Leave empty if there is none",
                false, false);
        typedInto(RedirectSettings.PROXY_PASSWORD, "Password", "Leave empty if there is none",
                false, true);
        typedInto(RedirectSettings.PROXY_CERTIFICATE, "Client certificate",
                "A PKCS#12 as base64, or the PEM blocks", true, false);
        typedInto(RedirectSettings.PROXY_CERTIFICATE_PASSWORD, "Certificate password",
                "What the certificate was written with", false, true);

        Preference tryIt = y(TRY_THE_PROXY);
        if (tryIt != null) {
            tryIt.A0(tapped -> {
                tapped.D0("Trying…");
                // Off the drawing thread: this opens a connection, and asking a proxy that is
                // not there to answer takes as long as the timeout.
                new Thread(() -> {
                    String said = ProxyRoute.tryIt();
                    onTheScreen(() -> tapped.D0(said));
                }, "sync-up-proxy-check").start();
                return true;
            });
        }
    }

    private void showProxyRows() {
        boolean on = SyncUpSettings.flag(RedirectSettings.PROXY, false);
        enable(new String[]{
                RedirectSettings.PROXY_KIND,
                RedirectSettings.PROXY_ADDRESS,
                RedirectSettings.PROXY_USER,
                RedirectSettings.PROXY_PASSWORD,
                RedirectSettings.PROXY_MTLS,
                TRY_THE_PROXY,
        }, on);
        enable(new String[]{
                RedirectSettings.PROXY_CERTIFICATE,
                RedirectSettings.PROXY_CERTIFICATE_PASSWORD,
        }, on && RedirectSettings.sendingACertificate());
    }

    // endregion

    // region Rows that are typed into.

    /**
     * Sync's own screens have no row for typing into, so the row asks for it: tapped, it opens
     * the same kind of dialog the app uses elsewhere. What is typed goes into a file of ours
     * rather than into Sync's settings, which is what keeps it out of Sync's Backup.
     *
     * @param overLines Whether what is typed runs to many lines, as a certificate does.
     * @param secret    Whether it is never shown again once set, only said to be set.
     */
    private void typedInto(String key, String title, String hint, boolean overLines,
                           boolean secret) {
        try {
            Preference row = y(key);
            if (row == null) {
                return;
            }
            sayWhetherItIsSet(row, key, secret);
            row.A0(tapped -> {
                ask(tapped, key, title, hint, overLines, secret);
                return true;
            });
        } catch (Exception ex) {
            Logger.printInfo(() -> "redirects: could not make " + key
                    + " something to type into: " + ex);
        }
    }

    private void ask(Preference row, String key, String title, String hint, boolean overLines,
                     boolean secret) {
        Context context = row.k();
        EditText typing = new EditText(context);
        typing.setHint(hint);
        // A secret is not filled in with what is there already: it is write-only, and putting
        // it back on the screen is the one thing keeping it out of a backup was meant to avoid.
        // Everything else is, so that it can be corrected rather than retyped.
        if (!secret) {
            typing.setText(RedirectSettings.privately(key));
            typing.setSelection(typing.getText().length());
        }

        if (overLines) {
            typing.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            typing.setMinLines(MANY_LINES);
        } else if (secret) {
            typing.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            typing.setSingleLine(true);
        } else {
            typing.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            typing.setSingleLine(true);
        }

        AlertDialog.Builder asking = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(heldOffTheEdges(context, typing))
                .setPositiveButton("Save", (dialog, which) -> {
                    RedirectSettings.putPrivately(key, typing.getText().toString().trim());
                    sayWhetherItIsSet(row, key, secret);
                })
                .setNegativeButton("Cancel", null);
        if (RedirectSettings.isSet(key)) {
            asking.setNeutralButton("Clear", (dialog, which) -> {
                RedirectSettings.putPrivately(key, "");
                sayWhetherItIsSet(row, key, secret);
            });
        }
        asking.show();
    }

    /** Says whether there is something there; for a secret, never what it is. */
    private void sayWhetherItIsSet(Preference row, String key, boolean secret) {
        String held = RedirectSettings.privately(key).trim();
        if (held.isEmpty()) {
            row.D0("Not set");
            return;
        }
        if (secret) {
            row.D0("Set");
            return;
        }
        row.D0(held.length() > 48 ? "Set, " + held.length() + " characters" : held);
    }

    // endregion

    // region Small things.

    private View heldOffTheEdges(Context context, View around) {
        FrameLayout holding = new FrameLayout(context);
        int edge = Math.round(AN_EDGE * context.getResources().getDisplayMetrics().density);
        holding.setPadding(edge, edge / 2, edge, 0);
        holding.addView(around);
        return holding;
    }

    private void enable(String[] keys, boolean enabled) {
        for (String key : keys) {
            Preference row = y(key);
            if (row != null) {
                row.q0(enabled);
            }
        }
    }

    /**
     * Runs something on the thread that draws, which is where a row may be changed from.
     *
     * <p>Posted to the main looper rather than to the fragment's own view: what a fragment
     * calls that is renamed by the app's minifier, and the looper is the platform's.
     */
    private void onTheScreen(Runnable what) {
        try {
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    what.run();
                } catch (Exception ex) {
                    // The screen may have been left by the time an answer arrives.
                    Logger.printDebug(() -> "redirects: nothing to draw on any more: " + ex);
                }
            });
        } catch (Exception ex) {
            Logger.printInfo(() -> "redirects: could not reach the drawing thread: " + ex);
        }
    }

    /** Watches one setting, so that turning it on shows what it affects there and then. */
    private void follow(String key, Runnable what) {
        try {
            SharedPreferences settings = SyncUpSettings.store();
            if (settings == null) {
                return;
            }
            SharedPreferences.OnSharedPreferenceChangeListener told = (changed, changedKey) -> {
                if (key.equals(changedKey)) {
                    what.run();
                }
            };
            watching.add(told);
            settings.registerOnSharedPreferenceChangeListener(told);
        } catch (Exception ex) {
            Logger.printInfo(() -> "redirects: could not follow " + key + ": " + ex);
        }
    }

    private static String hostOf(String instance) {
        int scheme = instance.indexOf("://");
        return scheme < 0 ? instance : instance.substring(scheme + 3);
    }

    private static String howLongAgo(long at) {
        if (at <= 0) {
            return "never fetched";
        }
        long minutes = (System.currentTimeMillis() - at) / 60000L;
        if (minutes < 1) {
            return "fetched just now";
        }
        if (minutes < 60) {
            return "fetched " + minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        }
        long hours = minutes / 60;
        if (hours < 48) {
            return "fetched " + hours + (hours == 1 ? " hour ago" : " hours ago");
        }
        return "fetched " + (hours / 24) + " days ago";
    }

    // endregion
}
