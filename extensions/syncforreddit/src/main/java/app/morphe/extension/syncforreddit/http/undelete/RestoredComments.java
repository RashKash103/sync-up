package app.morphe.extension.syncforreddit.http.undelete;

import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

import app.morphe.extension.shared.Logger;

/**
 * Remembers what was put back into a comment so that the line under its author can say so.
 *
 * <p>Sync builds that line by appending to a shared builder, and the patch calls in at the one
 * point both of its branches meet, just after the flair. Everything the note needs is reached
 * from the comment itself, so nothing has to be threaded through the view.
 *
 * @noinspection unused
 */
public final class RestoredComments {
    /** Bounded: a thread's worth of notes is all that is ever needed at once. */
    private static final int CACHE_SIZE = 512;

    private static final Map<String, String> notes =
            new LinkedHashMap<String, String>(CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > CACHE_SIZE;
                }
            };

    private RestoredComments() {}

    static void remember(String id, String note) {
        if (id == null || id.isEmpty() || note == null || note.isEmpty()) {
            return;
        }
        synchronized (notes) {
            notes.put(id, note);
        }
    }

    @Nullable
    private static String noteFor(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        synchronized (notes) {
            return notes.get(id);
        }
    }

    /**
     * Adds the note to the line under a comment's author, right after the separating space Sync
     * leaves following the flair and before the score and age it goes on to add. Sync's own
     * "(last edited …)" is written further along the same line and is left as it is.
     */
    public static void appendNote(oc.c header, xa.d comment) {
        try {
            if (header == null || comment == null) {
                return;
            }
            String note = noteFor(comment.U());
            if (note != null) {
                header.b("• " + note + " ");
            }
        } catch (Throwable ex) {
            // A comment losing its note is a far better outcome than a thread that will not draw.
            Logger.printException(() -> "Could not add the recovery note", ex);
        }
    }
}
