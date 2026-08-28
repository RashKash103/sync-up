package app.morphe.extension.syncforreddit.http;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import app.morphe.extension.shared.requests.BaseOkHttpRequestHook;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.syncforreddit.RedirectGfycatPatch;
import app.morphe.extension.syncforreddit.http.imgur.FixImgurProxyPatch;
import app.morphe.extension.syncforreddit.http.imgur.RecoverThumbnailsPatch;
import app.morphe.extension.syncforreddit.http.imgur.UndeleteImgurPatch;
import app.morphe.extension.syncforreddit.http.undelete.UndeleteRedditPatch;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;

/**
 * Sync bundles a modified Volley whose BasicNetwork issues okhttp3 calls directly, so a single
 * interceptor placed on the client it uses sees every Reddit API request.
 *
 * <p>Each interceptor here belongs to a patch of its own and passes requests straight through
 * unless that patch was included, so registering them all is harmless.
 *
 * @noinspection unused
 */
public class OkHttpRequestHook extends BaseOkHttpRequestHook {
    // Held at its own type: the getters below are protected, and Java only allows a subclass
    // to reach those through a reference of its own type, not through the base class field.
    private static final OkHttpRequestHook HOOK = new OkHttpRequestHook();

    // Keyed by source client, because this is installed at more than one site and Sync's
    // Volley and Glide each have a client of their own. A single slot would rebuild the
    // derived client on every call as the two sites took turns.
    private static final Map<OkHttpClient, OkHttpClient> instrumented = new IdentityHashMap<>();

    private OkHttpRequestHook() {}

    public static void init() {
        if (INSTANCE == null) {
            INSTANCE = HOOK;
        }
    }

    /**
     * Derives a client carrying the interceptors. Called with the client Volley makes every
     * call with, so the result is cached rather than rebuilt per request.
     */
    public static synchronized OkHttpClient install(OkHttpClient client) {
        if (client == null) {
            // Sync hands out its clients through getters that read a field, and a field can be
            // read before it has been filled in.
            return null;
        }
        init();

        OkHttpClient existing = instrumented.get(client);
        if (existing != null) {
            return existing;
        }

        OkHttpClient.Builder builder = client.newBuilder();
        for (Interceptor interceptor : HOOK.getInterceptors()) {
            builder.addInterceptor(interceptor);
        }
        for (Interceptor interceptor : HOOK.getNetworkInterceptors()) {
            builder.addNetworkInterceptor(interceptor);
        }

        OkHttpClient derived = builder.build();
        Logger.printInfo(() -> "Instrumented a client, " + instrumented.size() / 2 + " so far");
        instrumented.put(client, derived);
        // The derived client is handed back to callers, so it can come back in as the source.
        instrumented.put(derived, derived);
        return derived;
    }

    @Override
    protected List<Interceptor> getInterceptors() {
        List<Interceptor> interceptors = new ArrayList<>();
        interceptors.add(MediaRequestLog.INSTANCE);
        interceptors.add(new UndeleteRedditPatch());
        // Ahead of the undelete: both of these answer by reissuing the request against an
        // ordinary Imgur address, and a request reissued from here carries on down the chain
        // rather than starting again at the top. An image linked in a comment goes through the
        // proxy, which is why one has never been recovered.
        interceptors.add(new FixImgurProxyPatch());
        interceptors.add(new RecoverThumbnailsPatch());
        interceptors.add(new UndeleteImgurPatch());
        interceptors.add(RedirectGfycatPatch.get());
        return interceptors;
    }

    @Override
    protected List<Interceptor> getNetworkInterceptors() {
        return new ArrayList<>();
    }
}
