package app.morphe.extension.syncforreddit.translate;

import android.os.Bundle;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;

/**
 * The translation settings, as a screen of Sync's own settings.
 *
 * <p>Every screen Sync has is a fragment of this shape: it says which screen of preferences to
 * load and lets the app do the rest, which is what keeps it looking like the others. Sync finds
 * the fragment for a screen by an id, in a list of its own that nothing outside it can be
 * reached from, so the patch adds this one to that list.
 *
 * @noinspection unused
 */
public class TranslationScreen extends pa.d {
    /**
     * What the row on the front page of the settings points at. It matches the integer the patch
     * adds to the app's resources, and has to be a number no screen of Sync's own uses.
     */
    public static final int ID = 8100;

    @Override
    public void C3(Bundle state, String rootKey) {
        t3(ResourceUtils.getIdentifier(ResourceType.XML, "cat_translation"));
        super.C3(state, rootKey);
    }

    /**
     * Called where Sync looks up the fragment for a screen.
     *
     * @return This screen where the id is ours, and null for every other, which leaves Sync to
     *         look the id up as it always did.
     */
    /**
     * The name at the top of the screen, looked up the same way and from the same list, which
     * throws for an id it does not know just as the lookup for the fragment does.
     *
     * @return The name where the id is ours, and null for every other.
     */
    public static String titleFor(int id) {
        return id == ID ? "Translation" : null;
    }

    public static pa.d screenFor(int id) {
        try {
            if (id != ID) {
                return null;
            }
            TranslationScreen screen = new TranslationScreen();
            // Given the same argument Sync gives its own, which is what a search term is passed in.
            screen.a3(new Bundle());
            return screen;
        } catch (Exception ex) {
            Logger.printInfo(() -> "Could not open the translation settings: " + ex);
            return null;
        }
    }
}
