package app.morphe.extension.syncforreddit.http.undelete;

import android.text.style.ForegroundColorSpan;

import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

import app.morphe.extension.shared.Logger;

/**
 * Remembers what was put back, so that the line under an author can say so.
 *
 * <p>Sync builds that line by appending to a shared builder, and the patch calls in at the one
 * point both of its branches meet, just after the flair. Everything the note needs is reached
 * from the comment itself, so nothing has to be threaded through the view.
 *
 * @noinspection unused
 */
public final class RestoredNotes {
    /**
     * A red dark enough to read against a light background and light enough against a dark one,
     * since Sync themes the line the note sits on and the note has to hold up on either.
     */
    private static final int NOTE_COLOUR = 0xFFD1373A;

    /** Bounded: a thread's worth of notes is all that is ever needed at once. */
    private static final int CACHE_SIZE = 512;

    private static final Map<String, String> notes =
            new LinkedHashMap<String, String>(CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > CACHE_SIZE;
                }
            };

    private RestoredNotes() {}

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
     * Adds the note to the line under an author, right after the separating space Sync leaves
     * following the flair and before the score and age it goes on to add. Sync's own
     * "(last edited …)" is written further along the same line and is left as it is.
     */
    public static void appendNote(oc.c header, xa.d comment) {
        append(header, comment, true);
    }

    /**
     * @param leadingBullet Whether a separator is wanted before the note. What Sync leaves ahead
     *                      of it differs: a comment's line has a space there and a post's has a
     *                      separator already, and a second one beside it reads as a gap.
     */
    private static void append(oc.c header, xa.d content, boolean leadingBullet) {
        try {
            if (header == null || content == null) {
                return;
            }
            String note = noteFor(content.U());
            if (note != null) {
                // Only the wording is coloured, so the line keeps the same separators between
                // its parts as it has everywhere else.
                if (leadingBullet) {
                    header.b("• ");
                }
                header.c(note, new Object[]{new ForegroundColorSpan(NOTE_COLOUR)});
                header.b(leadingBullet ? " " : " • ");
            }
        } catch (Throwable ex) {
            // Losing a note is a far better outcome than a thread that will not draw.
            Logger.printException(() -> "Could not add the recovery note", ex);
        }
    }

    /**
     * The same for a post, whose header is built by a method taking what Sync's own flair helper
     * takes, so this is called in its shape rather than needing anything moved about for it.
     */
    public static void appendPostNote(oc.c header, Object unusedView, xa.d post) {
        append(header, post, false);
    }
}
