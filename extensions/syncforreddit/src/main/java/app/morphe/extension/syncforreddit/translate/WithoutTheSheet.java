package app.morphe.extension.syncforreddit.translate;

import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.laurencedawson.reddit_sync.provider.RedditProvider;

import java.util.regex.Pattern;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Translating where the app would have opened a sheet to do it in.
 *
 * <p>Sync opens a sheet, works the text out in it, waits there for the translation and closes
 * itself. Where the translation is one already made it is gone again within the same moment,
 * which reads as the screen flinching. Nothing about it is needed: everything the sheet is
 * given is in the arguments it would have been opened with.
 *
 * <p>So the opening is taken over instead. Every way into that sheet goes through the one call
 * that shows it, which is where this is asked first. Answering false lets the sheet open as it
 * would have, and is what happens if anything here cannot be done — the sheet still works.
 *
 * @noinspection unused
 */
public final class WithoutTheSheet {
    /** What Sync puts in the arguments of that sheet. */
    private static final String THE_CONTENT = "translate_post";
    private static final String THE_LANGUAGE = "override_language";

    /** What the app calls a comment, where it says which kind of thing it has. */
    private static final int A_COMMENT = 11;

    /** Where the text of each is kept. */
    private static final String COMMENT_WRITTEN = "body_raw";
    private static final String COMMENT_DRAWN = "body_processed";
    private static final String POST_WRITTEN = "selftext_raw";
    private static final String POST_DRAWN = "selftext_processed";
    private static final String THE_TITLE = "title";

    /** Enough of the text to tell what language it is in, and no more than is needed. */
    private static final int ENOUGH_TO_TELL = 700;

    /** What is not language, and would only mislead something working out what language is. */
    private static final Pattern NOT_WORDS = Pattern.compile(
            "(?:https?://|www\\.)\\S+|`[^`]*`|\\[[^\\]]*\\]\\([^)]*\\)|[*_~>#|^\\\\-]+");

    private static final Handler onTheMainThread = new Handler(Looper.getMainLooper());

    private WithoutTheSheet() {}

    /**
     * Called where Sync would have opened one of its sheets.
     *
     * @param what      Which sheet it is about to open.
     * @param arguments What it would have been opened with.
     * @return Whether this has been dealt with, and the sheet should not open.
     */
    public static boolean instead(Class<?> what, Object unusedManager, Bundle arguments) {
        try {
            if (what != da.d.class || arguments == null) {
                return false;
            }

            Object about = arguments.getSerializable(THE_CONTENT);
            if (!(about instanceof xa.d)) {
                return false;
            }

            translate((xa.d) about, arguments.getString(THE_LANGUAGE));
            return true;
        } catch (Throwable ex) {
            // Let Sync open its sheet and do it the long way.
            Logger.printInfo(() -> "Leaving the translation to the sheet: " + ex);
            return false;
        }
    }

    /** Does what the sheet would have done, off the thread that draws. */
    private static void translate(xa.d content, String language) {
        new Thread(() -> {
            try {
                Context context = Utils.getContext();
                String id = content.U();
                boolean isAComment = content.Y0() == A_COMMENT;

                // Asking a second time is asking for what was written back.
                Originals.Written wasWritten = Originals.written(id);
                if (wasWritten != null) {
                    Logger.printInfo(() -> "Putting back what was written");
                    Originals.forget(id);
                    store(context, id, isAComment, wasWritten.title, wasWritten.body);
                    return;
                }

                // A post has a title as well as a body, and a great many have only a title.
                String body = isAComment ? content.o() : content.P0();
                String title = isAComment ? null : content.b1();
                boolean hasBody = Markdown.worthTranslating(body);
                boolean hasTitle = Markdown.worthTranslating(title);

                if (!hasBody && !hasTitle) {
                    say("Nothing to translate");
                    return;
                }

                String into = TranslationSettings.language(context);
                String service = TranslationSettings.service(context);

                String from = language == null || language.isEmpty()
                        || OnDeviceTranslator.UNKNOWN.equals(language)
                        ? OnDeviceTranslator.languageOf(justWords(hasBody ? body : title))
                        : language;

                if (OnDeviceTranslator.isTheSameLanguage(from, into)) {
                    // Asking for English to be put into English wastes a download and ends with
                    // the same text and a note saying it was translated.
                    say(already(into));
                    return;
                }

                String saidBody = hasBody ? translated(context, service, body, from, into, true)
                        : null;
                String saidTitle = hasTitle
                        ? translated(context, service, title, from, into, false) : null;

                boolean bodyChanged = saidBody != null && !saidBody.equals(body);
                boolean titleChanged = saidTitle != null && !saidTitle.equals(title);
                if (!bodyChanged && !titleChanged) {
                    // Nothing came of it: either it was already in the language wanted, or the
                    // service gave back what it was given. Either way there is nothing to say
                    // under the author and nothing to put back later.
                    Logger.printInfo(() -> "The translation says what was written");
                    say(already(into));
                    return;
                }

                // Stored first, and only then remembered. What the line under an author says
                // has to follow what the post says, and it cannot lead it.
                store(context, id, isAComment, titleChanged ? saidTitle : null,
                        bodyChanged ? saidBody : null);
                Originals.remember(id, titleChanged ? title : null, bodyChanged ? body : null,
                        from);
            } catch (Exception ex) {
                Logger.printInfo(() -> "Could not translate: " + OnDeviceTranslator.because(ex));
                // A service says what was wrong with a key or an allowance, and that is worth
                // reading; anything with no message of its own is not.
                String said = ex.getMessage();
                say(said == null || said.isEmpty()
                        ? "Could not translate" : "Could not translate: " + said);
            }
        }, "sync-up-translate").start();
    }

    /**
     * @param asWritten Whether this part is markdown, which a title is not.
     * @return One part translated, remembered so that reading it again does not pay for it
     *         again.
     */
    private static String translated(Context context, String service, String text, String from,
                                     String into, boolean asWritten) throws Exception {
        TranslationCache.Translated already =
                TranslationCache.remembered(context, text, service, into);
        if (already != null) {
            Logger.printInfo(() -> "Translated this before, from " + already.from);
            return already.text;
        }

        say("Translating…");
        String said = TranslateNow.by(service, text, from, into, asWritten);
        TranslationCache.remember(context, text, service, into,
                new TranslationCache.Translated(said, from));
        return said;
    }

    /**
     * Writes the text where the app reads it from and says so, which is what makes everything
     * showing this post or comment draw it again.
     */
    private static void store(Context context, String id, boolean isAComment, String title,
                              String body) {
        ContentValues values = new ContentValues();
        if (body != null) {
            values.put(isAComment ? COMMENT_WRITTEN : POST_WRITTEN, body);
            values.put(isAComment ? COMMENT_DRAWN : POST_DRAWN, d7.f.r(null, body));
        }
        if (title != null) {
            values.put(THE_TITLE, title);
        }
        if (values.size() == 0) {
            return;
        }

        context.getContentResolver().update(RedditProvider.q, values, id, null);
        context.getContentResolver().notifyChange(RedditProvider.B, null);
    }

    /**
     * @return As much of the text as is needed to tell what language it is in, with what is not
     *         language taken out of it. An address is written the same way in every language and
     *         only makes the answer worse.
     */
    private static String justWords(String written) {
        String words = NOT_WORDS.matcher(written).replaceAll(" ").trim();
        return words.length() <= ENOUGH_TO_TELL ? words : words.substring(0, ENOUGH_TO_TELL);
    }

    /** @return What to say where there was nothing to do. */
    private static String already(String into) {
        String language = OnDeviceTranslator.nameOf(into);
        return language == null ? "Already in that language" : "Already in " + language;
    }

    private static void say(String what) {
        onTheMainThread.post(() -> {
            try {
                Toast.makeText(Utils.getContext(), what, Toast.LENGTH_SHORT).show();
            } catch (Exception ex) {
                Logger.printInfo(() -> "Could not say so: " + ex);
            }
        });
    }
}
