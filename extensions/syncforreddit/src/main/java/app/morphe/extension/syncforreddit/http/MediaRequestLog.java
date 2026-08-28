package app.morphe.extension.syncforreddit.http;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.Locale;

import app.morphe.extension.shared.Logger;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Reports every request for something that plays, whatever it is for and wherever it is going.
 *
 * <p>The video player reads through a client this hook is demonstrably attached to, and yet its
 * request has never appeared in a log while the thumbnail beside the same video always does.
 * The interceptors that would have reported it only look at the hosts they can do something
 * about, so a request going anywhere else passes through them in silence. This one does not
 * care where it is going.
 */
final class MediaRequestLog implements Interceptor {
    static final MediaRequestLog INSTANCE = new MediaRequestLog();

    private static final String[] EXTENSIONS =
            {".mp4", ".gifv", ".webm", ".m3u8", ".mpd", ".mkv"};

    private MediaRequestLog() {}

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        String path = request.url().encodedPath().toLowerCase(Locale.ROOT);

        boolean plays = false;
        for (String extension : EXTENSIONS) {
            if (path.endsWith(extension)) {
                plays = true;
                break;
            }
        }

        if (!plays) {
            return chain.proceed(request);
        }

        Logger.printInfo(() -> "Media request " + request.url());
        Response response = chain.proceed(request);
        Logger.printInfo(() -> "Media request " + request.url() + " answered " + response.code()
                + " " + response.header("Content-Type", "?"));
        return response;
    }
}
