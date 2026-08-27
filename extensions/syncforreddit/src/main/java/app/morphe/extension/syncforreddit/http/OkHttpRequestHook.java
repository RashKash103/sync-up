package app.morphe.extension.syncforreddit.http;

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.requests.BaseOkHttpRequestHook;
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

    private static OkHttpClient instrumentedSource;
    private static OkHttpClient instrumentedClient;

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
        init();

        if (instrumentedClient == null || instrumentedSource != client) {
            instrumentedSource = client;

            OkHttpClient.Builder builder = client.newBuilder();
            for (Interceptor interceptor : HOOK.getInterceptors()) {
                builder.addInterceptor(interceptor);
            }
            for (Interceptor interceptor : HOOK.getNetworkInterceptors()) {
                builder.addNetworkInterceptor(interceptor);
            }
            instrumentedClient = builder.build();
        }
        return instrumentedClient;
    }

    @Override
    protected List<Interceptor> getInterceptors() {
        // Contributed by the patches that need them.
        return new ArrayList<>();
    }

    @Override
    protected List<Interceptor> getNetworkInterceptors() {
        return new ArrayList<>();
    }
}
