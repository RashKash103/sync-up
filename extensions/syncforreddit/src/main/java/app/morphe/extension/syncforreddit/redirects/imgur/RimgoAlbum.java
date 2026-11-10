package app.morphe.extension.syncforreddit.redirects.imgur;

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.syncforreddit.redirects.RedirectSettings;

/**
 * What an album holds, read from a Rimgo instance rather than from Imgur.
 *
 * <p>An album's contents cannot be worked out from its id, so the page has to be read, and in a
 * region Imgur refuses there is no page to read: the album page, the embed of it and Imgur's own
 * API are all refused together. The archive still answers, but only for an album someone
 * captured, which leaves everything recent unopenable.
 *
 * <p>A Rimgo instance serves the album as a page of its own, and writes each item as
 * {@code src="/<id>.<ext>"} — its own media path. So the ids are simply there to be read, and
 * they are Imgur's own ids, which is what the rest of the app wants to be given.
 */
public final class RimgoAlbum {
    private RimgoAlbum() {}

    /** @return Whether there is any point asking: Rimgo has to be on and have an instance. */
    public static boolean available() {
        return RedirectSettings.redirecting() && RedirectSettings.usingRimgo();
    }

    /**
     * @return Each item of the album as {@code <id>.<extension>}, in the order the page lists
     *         them, or an empty list where nothing could be read. Never throws: this is one of
     *         several ways an album is looked for, and the others are still to be tried.
     */
    public static List<String> mediaIn(String albumId) {
        if (!available()) {
            return new ArrayList<>();
        }
        String instance = RimgoInstances.inUse();
        if (instance == null) {
            Logger.printDebug(() -> "rimgo: no instance to ask about album " + albumId);
            return new ArrayList<>();
        }

        String page = instance + "/a/" + albumId;
        try {
            String html = RimgoInstances.fetch(page, "text/html", instance);
            List<String> media = ImgurRoutes.mediaInRimgoAlbum(html);
            if (media.isEmpty()) {
                Logger.printInfo(() -> "rimgo: " + page + " named no media; it is "
                        + html.length() + " characters");
                // A page that is an error rather than an album says nothing about the
                // instance's media route, so it is not counted against it.
                return media;
            }
            Logger.printInfo(() -> "rimgo: album " + albumId + " holds " + media.size()
                    + " item(s), read from " + instance);
            RimgoInstances.worked(instance);
            return media;
        } catch (Exception ex) {
            Logger.printInfo(() -> "rimgo: could not read " + page + ": " + ex);
            RimgoInstances.failed(instance);
            return new ArrayList<>();
        }
    }
}
