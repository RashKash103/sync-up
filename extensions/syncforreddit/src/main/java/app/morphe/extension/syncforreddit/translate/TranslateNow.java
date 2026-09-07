package app.morphe.extension.syncforreddit.translate;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Translating a post or a comment, in place of the app doing it.
 *
 * <p>Sync already works out what to translate, shows something while it happens, writes what
 * comes back where the app reads its text from, and closes itself. Only the translating is
 * taken over, so that it goes to the service that was chosen, into the language that was
 * chosen, and is not asked for twice.
 *
 * @noinspection unused
 */
public final class TranslateNow {
    private static final Handler onTheMainThread = new Handler(Looper.getMainLooper());

    private TranslateNow() {}

    /**
     * Called where Sync would have asked the on-device library for a translation.
     *
     * @param sheet    What is waiting for the answer.
     * @param text     What to translate.
     * @param language What it is written in, as far as Sync could tell.
     */
    public static void instead(da.d sheet, String text, String language) {
        new Thread(() -> {
            try {
                Context context = Utils.getContext();
                String into = TranslationSettings.language(context);
                String service = TranslationSettings.service(context);

                TranslationCache.Translated already =
                        TranslationCache.remembered(context, text, service, into);
                if (already != null) {
                    Logger.printInfo(() -> "Translated this before, from " + already.from);
                    answer(sheet, already.from, already.text);
                    return;
                }

                String from = language == null || language.isEmpty()
                        || OnDeviceTranslator.UNKNOWN.equals(language)
                        ? OnDeviceTranslator.languageOf(text)
                        : language;

                String translated = by(service, text, from, into);
                TranslationCache.remember(context, text, service, into,
                        new TranslationCache.Translated(translated, from));

                answer(sheet, from, translated);
            } catch (Exception ex) {
                Logger.printInfo(() -> "Could not translate: " + ex);
                String said = ex.getMessage() == null
                        ? ex.getClass().getSimpleName() : ex.getMessage();
                onTheMainThread.post(() -> da.d.v4(sheet, "Could not translate: " + said));
            }
        }, "sync-up-translate").start();
    }

    /** @return The text translated by whichever service was chosen. */
    private static String by(String service, String text, String from, String into)
            throws Exception {
        if (TranslationSettings.DEEPL.equals(service)
                || TranslationSettings.GOOGLE.equals(service)) {
            // Their settings are there; what stands behind them is not written yet, so the
            // device answers rather than nothing happening at all.
            Logger.printInfo(() -> service + " cannot translate yet, so this device did");
        }
        return OnDeviceTranslator.translate(text, from, into);
    }

    private static void answer(da.d sheet, String from, String translated) {
        onTheMainThread.post(() -> da.d.x4(sheet, from, translated));
    }
}
