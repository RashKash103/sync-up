package app.morphe.extension.syncforreddit.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import androidx.annotation.Nullable;
import android.preference.PreferenceManager;

import java.nio.charset.StandardCharsets;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * What the app identifies itself to Reddit and to Imgur as, answered from the settings where
 * they have been set and from what was given when patching otherwise.
 *
 * <p>These were only ever written into the app while it was being patched, which is fine by
 * hand and awkward for anything building copies on its own: the four of them are the one thing
 * a build cannot be made without. Reading them here lets a copy be patched with none of them
 * and told afterwards.
 *
 * <p>They are kept in the settings everyone shares rather than the per-account ones Sync writes
 * most things to. The client id is what logging in is done with, so it has to be readable
 * before there is an account for it to belong to.
 *
 * @noinspection unused
 */
public final class ClientSettings {
    public static final String CLIENT_ID = "sync_up_client_id";
    public static final String REDIRECT_URI = "sync_up_redirect_uri";
    public static final String USER_AGENT = "sync_up_user_agent";
    public static final String IMGUR_CLIENT_ID = "sync_up_imgur_client_id";

    /** What Reddit is asked for a token with. */
    private static final String BASIC = "Basic ";

    /** How the Client-ID is written in the header Imgur is sent. */
    private static final String CLIENT_ID_HEADER = "Client-ID ";

    private ClientSettings() {}

    // What was given when patching. Each is overridden by the patch; an empty answer means
    // nothing was given, which is allowed now that they can be set here instead.

    public static String bakedClientId() {
        // Overridden by patch.
        return "";
    }

    public static String bakedRedirectUri() {
        // Overridden by patch.
        return "";
    }

    public static String bakedUserAgent() {
        // Overridden by patch.
        return "";
    }

    public static String bakedImgurClientId() {
        // Overridden by patch.
        return "";
    }

    /** The address Reddit is asked to authorize at, as the app holds it. */
    public static String bakedAuthorizationUrl() {
        // Overridden by patch.
        return "";
    }

    /** @return The settings everyone shares, which are readable with no account signed in. */
    @Nullable
    public static SharedPreferences store() {
        Context context = Utils.getContext();
        if (context == null) {
            return null;
        }
        try {
            return PreferenceManager.getDefaultSharedPreferences(context);
        } catch (Exception ex) {
            Logger.printInfo(() -> "client: could not reach the settings: " + ex);
            return null;
        }
    }

    /** @return What is set here, or what was given when patching where nothing is. */
    public static String text(String key, String whenUnset) {
        try {
            SharedPreferences store = store();
            String set = store == null ? null : store.getString(key, null);
            return set == null || set.trim().isEmpty() ? whenUnset : set.trim();
        } catch (Exception ex) {
            Logger.printInfo(() -> "client: could not read " + key + ": " + ex);
            return whenUnset;
        }
    }

    /** @return What was given when patching for this setting, which may be nothing. */
    public static String bakedFor(String key) {
        if (CLIENT_ID.equals(key)) {
            return bakedClientId();
        }
        if (REDIRECT_URI.equals(key)) {
            return bakedRedirectUri();
        }
        if (USER_AGENT.equals(key)) {
            return bakedUserAgent();
        }
        if (IMGUR_CLIENT_ID.equals(key)) {
            return bakedImgurClientId();
        }
        return "";
    }

    public static String clientId() {
        return text(CLIENT_ID, bakedClientId());
    }

    public static String redirectUri() {
        return text(REDIRECT_URI, bakedRedirectUri());
    }

    public static String userAgent() {
        return text(USER_AGENT, bakedUserAgent());
    }

    public static String imgurClientId() {
        return text(IMGUR_CLIENT_ID, bakedImgurClientId());
    }

    /**
     * What Reddit is asked for a token with: the client id as a name with no password beside it.
     *
     * <p>Built here rather than written in while patching, since it is the client id in another
     * form and would otherwise go on saying what the old one was.
     */
    public static String basicAuth() {
        try {
            byte[] named = (clientId() + ":").getBytes(StandardCharsets.UTF_8);
            return BASIC + Base64.encodeToString(named, Base64.NO_WRAP);
        } catch (Exception ex) {
            Logger.printInfo(() -> "client: could not build what asks for a token: " + ex);
            return BASIC;
        }
    }

    /** The authorization address with whichever client id is in use written into it. */
    public static String authorizationUrl() {
        String address = bakedAuthorizationUrl();
        try {
            return address.replaceFirst("client_id=[^&]*", "client_id=" + clientId());
        } catch (Exception ex) {
            Logger.printInfo(() -> "client: could not name the client in the address: " + ex);
            return address;
        }
    }

    /** The header Imgur is sent, naming whichever client id is in use. */
    public static String imgurHeader() {
        return CLIENT_ID_HEADER + imgurClientId();
    }
}
