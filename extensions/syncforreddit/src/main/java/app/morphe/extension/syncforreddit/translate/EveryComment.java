package app.morphe.extension.syncforreddit.translate;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.Logger;

/**
 * Translating every comment read so far, and putting them all back again.
 *
 * <p>Each comment is judged on its own. A thread can hold several languages at once — a reply in
 * one and the comment above it in another — so one answer for the lot would translate some of
 * them out of a language they were never in.
 *
 * <p>A comment whose language cannot be told is left alone rather than guessed at. Asking about
 * that one on its own still offers to choose the language, which is where a choice belongs: it
 * is one comment and one question, rather than a question in the middle of forty.
 */
final class EveryComment {
    /**
     * The thread being read, kept from the last comment drawn.
     *
     * <p>What draws a comment is handed the thread it belongs to, and a thread cannot be
     * translated without having been read, so by the time this is wanted one has been seen.
     * Weakly, since holding a thread open after it is closed would hold everything in it.
     */
    private static WeakReference<ya.c> beingRead = new WeakReference<>(null);

    private EveryComment() {}

    /** Remembers the thread a comment belongs to, as it is drawn. */
    static void beingRead(ya.c thread) {
        if (thread != null && thread != beingRead.get()) {
            beingRead = new WeakReference<>(thread);
        }
    }

    /** @return Every comment the thread is holding, or nothing where none can be reached. */
    static List<xa.d> of() {
        List<xa.d> comments = new ArrayList<>();
        try {
            ya.c thread = beingRead.get();
            va.a held = thread == null ? null : thread.t();
            ArrayList<?> everything = held == null ? null : held.k();
            if (everything == null) {
                return comments;
            }

            for (Object each : everything) {
                if (each instanceof xa.d) {
                    comments.add((xa.d) each);
                }
            }
        } catch (Throwable ex) {
            Logger.printInfo(() -> "Could not reach the comments of the thread: " + ex);
        }
        return comments;
    }

    /** @return How many of them are standing translated. */
    static int translatedAmong(List<xa.d> comments) {
        int translated = 0;
        for (xa.d each : comments) {
            if (Originals.isTranslated(each)) {
                translated++;
            }
        }
        return translated;
    }

    /**
     * Translates every one that is not, or puts every one back that is.
     *
     * @param back Whether to put them back rather than translate them.
     */
    static void translate(List<xa.d> comments, boolean back, Said tell) {
        new Thread(() -> {
            int done = 0;
            int skipped = 0;
            int failed = 0;

            for (xa.d each : comments) {
                try {
                    boolean translated = Originals.isTranslated(each);
                    if (translated != back) {
                        // Already the way it is being asked to be.
                        continue;
                    }

                    switch (WithoutTheSheet.of(each, null, false)) {
                        case TRANSLATED:
                        case PUT_BACK:
                            done++;
                            break;
                        case NO_LANGUAGE:
                            skipped++;
                            break;
                        default:
                            break;
                    }
                } catch (Exception ex) {
                    failed++;
                    Logger.printInfo(() -> "Could not translate one of them: "
                            + OnDeviceTranslator.because(ex));
                }
            }

            tell.heard(said(done, skipped, failed, back));
        }, "sync-up-translate-thread").start();
    }

    /** What is worth saying once about all of them. */
    interface Said {
        void heard(String what);
    }

    /** @return What to say about what came of it, counted rather than listed. */
    private static String said(int done, int skipped, int failed, boolean back) {
        if (done == 0 && skipped == 0 && failed == 0) {
            return back ? "Nothing to put back" : "Nothing to translate";
        }

        StringBuilder saying = new StringBuilder();
        saying.append(done).append(back ? " put back" : done == 1 ? " comment translated"
                : " comments translated");
        if (skipped > 0) {
            saying.append(", ").append(skipped).append(" in no language anyone could tell");
        }
        if (failed > 0) {
            saying.append(", ").append(failed).append(" could not be done");
        }
        return saying.toString();
    }
}
