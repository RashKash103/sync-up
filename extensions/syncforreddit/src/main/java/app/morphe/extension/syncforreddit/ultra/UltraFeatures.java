package app.morphe.extension.syncforreddit.ultra;

import android.content.SharedPreferences;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.syncforreddit.translate.TranslationSettings;

/**
 * What Sync keeps behind its subscription, and what of it can be had without one.
 *
 * <p>Some of it cannot: paints and tags and settings are kept on Sync's own servers, which do
 * not answer any more, and a screen offering to back up to them would only fail. What is left is
 * done entirely on the device, and is asked for the same three questions before it will run —
 * whether the copy is paid for, whether a flag was turned on from afar, and whether the account
 * reading it is one of the developer's own. None of the three has anything to say about whether
 * the feature works.
 *
 * @noinspection unused
 */
public final class UltraFeatures {
    /** What Sync calls the setting for showing a preview of a website. */
    private static final String WEBSITE_PREVIEWS = "ultra_website_previews";

    private UltraFeatures() {}

    /** Says what picture the app decided to ask for, which is where a preview goes wrong. */
    public static void asksFor(String picture) {
        Logger.printInfo(() -> "Preview picture wanted: " + picture);
    }

    /**
     * @return Always yes. Put where one of those three questions was asked, so that what is
     *         asked afterwards is the setting for the feature itself.
     */
    public static boolean yes() {
        return true;
    }

    /**
     * @return Whether to show a preview of this address, which is the setting for it and
     *         whether there is a preview to show. Sync asks a great deal more first, none of it
     *         about the address.
     */
    public static boolean websitePreviewFor(String url) {
        try {
            SharedPreferences settings =
                    TranslationSettings.store(Utils.getContext());
            boolean wanted = settings != null && settings.getBoolean(WEBSITE_PREVIEWS, false);
            boolean canBe = wanted && wc.q.c(url);
            Logger.printInfo(() -> "Preview? " + url + " setting=" + wanted + " suitable=" + canBe);
            return canBe;
        } catch (Throwable ex) {
            Logger.printInfo(() -> "Could not tell whether to preview " + url + ": " + ex);
            return false;
        }
    }
}
