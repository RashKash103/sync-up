package app.morphe.extension.syncforreddit.translate;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.concurrent.TimeUnit;

/**
 * Translating on the device, with what the app already carries.
 *
 * <p>Nothing is sent anywhere and nothing has to be set up, which is why it is what translation
 * starts as. It works from a model for the pair of languages, fetched the first time that pair
 * is asked for, so the first translation into a new language waits on a download of a few
 * megabytes and the rest are quick.
 *
 * <p>Every call here blocks, and none may be made on the main thread.
 */
final class OnDeviceTranslator {
    /** What the identifier answers where it cannot tell what it is reading. */
    static final String UNKNOWN = "und";

    private static final long MODEL_WAIT_SECONDS = 120;
    private static final long TRANSLATE_WAIT_SECONDS = 30;
    private static final long IDENTIFY_WAIT_SECONDS = 10;

    private OnDeviceTranslator() {}

    /**
     * @return The language the text is written in, as a tag, or {@link #UNKNOWN}.
     */
    static String languageOf(String text) throws Exception {
        LanguageIdentifier identifier = LanguageIdentification.a();
        try {
            Object said = Tasks.b(identifier.q0(text), IDENTIFY_WAIT_SECONDS, TimeUnit.SECONDS);
            return said == null ? UNKNOWN : said.toString();
        } finally {
            identifier.close();
        }
    }

    /**
     * @param from A language tag, which must be one the library knows.
     * @param into The language wanted.
     * @return The translated text.
     */
    static String translate(String text, String from, String into) throws Exception {
        String source = TranslateLanguage.a(from);
        String target = TranslateLanguage.a(into);
        if (source == null || target == null) {
            throw new IllegalArgumentException("Cannot translate " + from + " into " + into);
        }

        Translator translator = Translation.a(
                new TranslatorOptions.Builder().b(source).c(target).a());
        try {
            // Nothing is asked of the connection: a model is wanted now, not when there is wifi.
            Tasks.b(translator.G(new DownloadConditions.Builder().a()),
                    MODEL_WAIT_SECONDS, TimeUnit.SECONDS);

            Object translated = Tasks.b(translator.A(text),
                    TRANSLATE_WAIT_SECONDS, TimeUnit.SECONDS);
            if (translated == null) {
                throw new IllegalStateException("Nothing came back");
            }
            return translated.toString();
        } finally {
            translator.close();
        }
    }

    /** @return Whether the library has a name for the language, and so can work with it. */
    static boolean knows(String languageTag) {
        try {
            return languageTag != null && TranslateLanguage.a(languageTag) != null;
        } catch (Exception ex) {
            return false;
        }
    }
}
