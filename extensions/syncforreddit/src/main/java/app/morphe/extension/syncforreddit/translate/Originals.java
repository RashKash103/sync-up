package app.morphe.extension.syncforreddit.translate;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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
 * <p>What is known about each is kept apart from the text of it: the text in a file, and beside
 * it only enough to recognise it. The line under an author asks about this as it draws, and must
 * not have to hold every translated post in memory to answer.
 *
 * @noinspection unused
 */
public final class Originals {
    private static final String STORE = "sync-up-translated";

    /** What is written in the file: a post has a title as well as a body. */
    private static final String TITLE = "title";
    private static final String BODY = "body";

    /** Between the two, where they are counted as one for telling the text again. */
    private static final char BETWEEN = '\u0000';

    /** What is known about one, beside the text of it. */
    private static final String FROM = "from";
    private static final String HASH = "hash";
    private static final String LENGTH = "length";
    private static final String AT = "at";

    /** Enough for far more threads than are read at a sitting. */
    private static final int KEPT = 300;

    private static final int DROPPED_AT_ONCE = 50;

    /** What each translated thing was written in, and how to tell that text again. */
    private static Map<String, Held> held;

    /** What is known about one thing that was translated. */
    private static final class Held {
        final String from;
        final int hash;
        final int length;

        Held(String from, int hash, int length) {
            this.from = from;
            this.hash = hash;
            this.length = length;
        }

        boolean is(String text) {
            return text != null && text.length() == length && text.hashCode() == hash;
        }
    }

    private Originals() {}

    /** What a post said before it was translated. Either part may be absent. */
    static final class Written {
        final String title;
        final String body;

        Written(String title, String body) {
            this.title = title;
            this.body = body;
        }
    }

    /** @return What was written, or null where this was never translated. */
    @Nullable
    static Written written(String id) {
        if (id == null || id.isEmpty() || !held().containsKey(id)) {
            return null;
        }
        try {
            File kept = fileFor(id);
            if (!kept.exists()) {
                return null;
            }
            JSONObject says = new JSONObject(read(kept));
            return new Written(says.has(TITLE) ? says.getString(TITLE) : null,
                    says.has(BODY) ? says.getString(BODY) : null);
        } catch (Exception ex) {
            Logger.printInfo(() -> "Could not read what was written: " + ex);
            return null;
        }
    }

    /** @return The parts counted as one, which is how the text is told again. */
    static String asOne(String title, String body) {
        return (title == null ? "" : title) + BETWEEN + (body == null ? "" : body);
    }

    /**
     * Keeps what was written, so that it can be put back.
     *
     * <p>Called only once the translation is where it can be seen. Keeping it before that would
     * leave the line under the author saying a post is translated when it is not.
     */
    static void remember(String id, String title, String body, String from) {
        if (id == null || id.isEmpty() || (title == null && body == null)) {
            return;
        }
        try {
            File kept = fileFor(id);
            File folder = kept.getParentFile();
            if (folder != null && !folder.exists() && !folder.mkdirs()) {
                return;
            }

            JSONObject parts = new JSONObject();
            if (title != null) {
                parts.put(TITLE, title);
            }
            if (body != null) {
                parts.put(BODY, body);
            }
            write(kept, parts.toString());

            String asOne = asOne(title, body);
            JSONObject says = new JSONObject();
            says.put(FROM, from == null ? "" : from);
            says.put(HASH, asOne.hashCode());
            says.put(LENGTH, asOne.length());
            says.put(AT, System.currentTimeMillis());
            store().edit().putString(id, says.toString()).apply();

            held().put(id, new Held(from == null ? "" : from, asOne.hashCode(), asOne.length()));

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
        held().remove(id);
        try {
            store().edit().remove(id).apply();
            File kept = fileFor(id);
            if (kept.exists() && !kept.delete()) {
                Logger.printInfo(() -> "Could not remove what was written for " + id);
            }
        } catch (Exception ex) {
            Logger.printInfo(() -> "Could not forget what was written: " + ex);
        }
    }

    /**
     * @param current What the post or the comment says now.
     * @return What the line under the author should say, or null where this is not being read
     *         in translation.
     *
     * <p>The text is checked rather than trusted. A thread read again is written back from
     * Reddit as its author wrote it, which undoes a translation without anything here being
     * told; saying it is translated when it plainly is not is worse than saying nothing.
     */
    @Nullable
    public static String noteFor(String id, String current) {
        try {
            if (id == null || id.isEmpty()) {
                return null;
            }
            Held about = held().get(id);
            if (about == null) {
                return null;
            }

            if (about.is(current)) {
                // What it says is what was written, so it is not translated any more.
                forgetLater(id);
                return null;
            }

            String language = named(about.from);
            return language == null ? "translated" : "translated from " + language;
        } catch (Exception ex) {
            // Never at the cost of the thread drawing.
            return null;
        }
    }

    /** What the app calls a comment, where it says which kind of thing it has. */
    private static final int A_COMMENT = 11;

    /**
     * @return Whether this is standing translated: it was translated, and what it says now is
     *         still not what was written.
     */
    static boolean isTranslated(xa.d content) {
        try {
            return content != null && noteFor(content.U(), saysNow(content)) != null;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * @return What this says now, counted the same way as what was written: a comment is its
     *         text, and a post is its title and its text together, since either may have been
     *         translated.
     */
    @Nullable
    public static String saysNow(xa.d content) {
        try {
            return content.Y0() == A_COMMENT
                    ? asOne(null, content.o())
                    : asOne(content.b1(), content.P0());
        } catch (Exception ex) {
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

    /** Off the thread that draws, since a file is being removed. */
    private static void forgetLater(String id) {
        held().remove(id);
        new Thread(() -> forget(id), "sync-up-forget").start();
    }

    private static SharedPreferences store() {
        return Utils.getContext().getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    private static File folder() {
        return new File(Utils.getContext().getFilesDir(), STORE);
    }

    private static File fileFor(String id) {
        // An id is Reddit's own, which is letters and numbers, so it names a file as it is.
        return new File(folder(), id.replaceAll("[^A-Za-z0-9_]", "_"));
    }

    /** What has been translated, read once and kept, since a thread asks as it draws. */
    private static synchronized Map<String, Held> held() {
        if (held == null) {
            held = new HashMap<>();
            try {
                for (Map.Entry<String, ?> each : store().getAll().entrySet()) {
                    JSONObject says = new JSONObject(String.valueOf(each.getValue()));
                    held.put(each.getKey(), new Held(says.optString(FROM, ""),
                            says.optInt(HASH), says.optInt(LENGTH)));
                }
            } catch (Exception ex) {
                Logger.printInfo(() -> "Could not read what has been translated: " + ex);
            }
        }
        return held;
    }

    /** Drops the oldest where there are more than are worth keeping. */
    private static void makeRoom() {
        try {
            Map<String, ?> all = store().getAll();
            if (all.size() <= KEPT) {
                return;
            }

            List<String> oldest = new ArrayList<>(all.keySet());
            oldest.sort((one, other) -> Long.compare(at(all.get(one)), at(all.get(other))));

            for (int dropped = 0; dropped < DROPPED_AT_ONCE && dropped < oldest.size(); dropped++) {
                forget(oldest.get(dropped));
            }
        } catch (Exception ex) {
            Logger.printInfo(() -> "Could not make room: " + ex);
        }
    }

    private static long at(Object says) {
        try {
            return new JSONObject(String.valueOf(says)).optLong(AT, 0);
        } catch (Exception ex) {
            return 0;
        }
    }

    private static String read(File kept) throws Exception {
        try (FileInputStream from = new FileInputStream(kept)) {
            ByteArrayOutputStream into = new ByteArrayOutputStream();
            byte[] block = new byte[4096];
            int read;
            while ((read = from.read(block)) > 0) {
                into.write(block, 0, read);
            }
            return into.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void write(File kept, String what) throws Exception {
        try (FileOutputStream into = new FileOutputStream(kept)) {
            into.write(what.getBytes(StandardCharsets.UTF_8));
        }
    }
}
