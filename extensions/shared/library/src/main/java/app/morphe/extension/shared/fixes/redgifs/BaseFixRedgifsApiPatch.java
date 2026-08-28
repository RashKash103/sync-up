package app.morphe.extension.shared.fixes.redgifs;

import androidx.annotation.NonNull;

import org.json.JSONException;

import java.io.IOException;
import java.net.HttpURLConnection;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.requests.PatchedditInterceptor;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;


public abstract class BaseFixRedgifsApiPatch extends PatchedditInterceptor {
    protected static BaseFixRedgifsApiPatch INSTANCE;
    public abstract String getDefaultUserAgent();

    public boolean isPatchIncluded() {
        // Overridden by patch.
        return false;
    }

    @NonNull
    @Override
    public Response doIntercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        if (!request.url().host().equals("api.redgifs.com")) {
            return chain.proceed(request);
        }

        String userAgent = getDefaultUserAgent();
        boolean refused = false;

        if (request.header("Authorization") != null) {
            Response response = chain.proceed(request.newBuilder().header("User-Agent", userAgent).build());
            if (response.isSuccessful()) {
                return response;
            }
            // It's possible that the user agent is being overwritten later down in the interceptor
            // chain, so make sure we grab the new user agent from the request headers.
            userAgent = response.request().header("User-Agent");
            response.close();
            // Whatever token that request carried was turned down, so any token held here for
            // the same user agent is no more likely to be accepted.
            refused = true;
        }

        try {
            RedgifsTokenManager.RedgifsToken token =
                    RedgifsTokenManager.refreshToken(userAgent, refused);

            // Emulate response for old OAuth endpoint
            if (request.url().encodedPath().equals("/v2/oauth/client")) {
                String responseBody = RedgifsTokenManager.getEmulatedOAuthResponseBody(token);
                return new Response.Builder()
                        .message("OK")
                        .code(HttpURLConnection.HTTP_OK)
                        .protocol(Protocol.HTTP_1_1)
                        .request(request)
                        .header("Content-Type", "application/json")
                        .body(ResponseBody.create(
                                responseBody, MediaType.get("application/json")))
                        .build();
            }

            Response response = chain.proceed(authorized(request, token, userAgent));
            if (!isRefusal(response)) {
                return response;
            }

            // The token was refused although it has not expired, which is what happens after
            // moving between networks: Redgifs ties a temporary token to the address it was
            // issued for. Nothing in the token says so, so the only way to find out is to be
            // turned down, and the only way out of it used to be restarting the app.
            Logger.printInfo(() -> "The Redgifs token was refused, asking for another");
            response.close();

            RedgifsTokenManager.RedgifsToken replacement =
                    RedgifsTokenManager.refreshToken(userAgent, true);
            Response retried = chain.proceed(authorized(request, replacement, userAgent));

            // A token that has just been issued and is refused straight away is not a token
            // that went stale: Redgifs is turning down the request itself.
            if (isRefusal(retried)) {
                Logger.printInfo(() -> "Redgifs refused " + request.url() + " with a new token: "
                        + retried.code());
            }
            return retried;
        } catch (JSONException ex) {
            Logger.printException(() -> "Could not parse Redgifs response", ex);
            throw new IOException(ex);
        }
    }

    private static Request authorized(Request request, RedgifsTokenManager.RedgifsToken token,
                                      String userAgent) {
        return request.newBuilder()
                .header("Authorization", "Bearer " + token.getAccessToken())
                .header("User-Agent", userAgent)
                .build();
    }

    /**
     * Redgifs turns down a token it will not accept rather than saying it has expired.
     */
    private static boolean isRefusal(Response response) {
        return response.code() == HttpURLConnection.HTTP_UNAUTHORIZED
                || response.code() == HttpURLConnection.HTTP_FORBIDDEN;
    }
}
