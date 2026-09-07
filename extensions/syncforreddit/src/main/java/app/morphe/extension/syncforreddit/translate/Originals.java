package app.morphe.extension.syncforreddit.translate;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * What a post or a comment said before it was translated.
 *
 * <p>A translation is put in place of what was written rather than under it, so what was written
 * has to be kept somewhere or it is gone. It is kept here, against the id of the post or the
 * comment, and put back when a translation is asked for a second time.
 *
 * <p>Not in the cache beside the translations: that is a cache, emptied when the system wants
 * room and when the setting says it is old enough. Losing a translation there costs another
 * translation. Losing what was written would leave a post stuck in a language its author did not
 * write it in, with nothing to say so and no way back.
 *
 * @noinspection unused
 */
public final class Originals {
    private static final String STORE = "sync-up-translated";

    /** What is written down against an id. */
    private static final String WRITTEN = "written";
    private static final String FROM = "from";
    private static final String AT = "at";

    /** Enough for far more threads than are read at a sitting, and small enough to hold. */
    private static final int KEPT = 300;

    private static final int DROPPED_AT_ONCE = 50;

    /** The language each translated thing was written in, for the note under its author. */
    private static Map<String, String> languages;

    private Originals() {}

    /** @return What was written, or null where this was never translated. */
    @Nullable
    static String written(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        try {
            String held = store().getString(id, null);
            return held == null ? null : new JSONObject(held).optString(WRITTEN, null);
        } catch (Exception ex) {
            Logger.printInfo(() -> "Could not read what was written: " + ex);
            return null;
        }
    }

    /** Keeps what was written, so that it can be put back. */
    static void remember(String id, String written, String from) {
        if (id == null || id.isEmpty() || written == null || written.isEmpty()) {
            return;
        }
        try {
            JSONObject said = new JSONObject();
            said.put(WRITTEN, written);
            said.put(FROM, from == null ? "" : from);
            said.put(AT, System.currentTimeMillis());

            store().edit().putString(id, said.toString()).apply();
            languages().put(id, from == null ? "" : from);

            makeRoom();
        } catch (Exception ex) {
            Logger.printInfo(() -> "Could not keep what was written: " + ex);
        }
    }

    /** Forgets it, which is what putting it back amounts to. */
    static void forget(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        try {
            store().edit().remove(id).apply();
            languages().remove(id);
        } catch (Exception ex) {
            Logger.printInfo(() -> "Could not forget what was written: " + ex);
        }
    }

    /**
     * @return What the line under the author should say about this one, or null where it was
     *         not translated. Read while a thread draws, so it answers from memory.
     */
    @Nullable
    public static String noteFor(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        try {
            String from = languages().get(id);
            if (from == null) {
                return null;
            }
            String language = named(from);
            return language == null ? "Translated" : "Translated from " + language;
        } catch (Exception ex) {
            // Never at the cost of the thread drawing.
            return null;
        }
    }

    /** @return What that language is called, in the reader's own language. */
    @Nullable
    private static String named(String tag) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        String language = new Locale(tag).getDisplayLanguage();
        return language.isEmpty() || language.equalsIgnoreCase(tag) ? null : language;
    }

    private static SharedPreferences store() {
        return Utils.getContext().getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    /** The ids that have been translated, read once and kept, since a thread asks as it draws. */
    private static synchronized Map<String, String> languages() {
        if (languages == null) {
            languages = new HashMap<>();
            for (Map.Entry<String, ?> each : store().getAll().entrySet()) {
                try {
                    languages.put(each.getKey(),
                            new JSONObject(String.valueOf(each.getValue())).optString(FROM, ""));
                } catch (Exception ex) {
                    languages.put(each.getKey(), "");
                }
            }
        }
        return languages;
    }

    /** Drops the oldest where there are more than are worth keeping. */
    private static void makeRoom() throws Exception {
        Map<String, ?> held = store().getAll();
        if (held.size() <= KEPT) {
            return;
        }

        List<String> oldest = new ArrayList<>(held.keySet());
        oldest.sort((one, other) -> Long.compare(at(held.get(one)), at(held.get(other))));

        SharedPreferences.Editor editing = store().edit();
        for (int dropped = 0; dropped < DROPPED_AT_ONCE && dropped < oldest.size(); dropped++) {
            editing.remove(oldest.get(dropped));
            languages().remove(oldest.get(dropped));
        }
        editing.apply();
    }

    private static long at(Object held) {
        try {
            return new JSONObject(String.valueOf(held)).optLong(AT, 0);
        } catch (Exception ex) {
            return 0;
        }
    }
}
