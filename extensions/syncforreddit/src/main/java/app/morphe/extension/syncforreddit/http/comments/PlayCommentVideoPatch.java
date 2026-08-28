package app.morphe.extension.syncforreddit.http.comments;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.requests.PatchedditInterceptor;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Points a video posted in a comment at the video.
 *
 * <p>Reddit writes one into the comment as a link to a player page on its own site, which is a
 * page rather than anything playable: Sync shows the link, and following it opens a browser at
 * an address that names no subreddit and is answered with a banned notice. The video itself sits
 * where every other Reddit video does, under the id the player link already carries, so the link
 * is rewritten to point straight at it.
 *
 * <p>Sync knows how to play that, so the link opens in its own player rather than a browser.
 *
 * @noinspection unused
 */
public class PlayCommentVideoPatch extends PatchedditInterceptor {
    /**
     * The player page Reddit writes into a comment, whose last part but one is the id the video
     * is stored under.
     */
    private static final Pattern PLAYER_LINK = Pattern.compile(
            "https?://(?:www\\.)?reddit\\.com/link/[A-Za-z0-9]+/video/([A-Za-z0-9]+)/player");

    /**
     * Reddit serves its videos as a stream rather than a file, and Sync recognises one by the
     * playlist on the end.
     */
    private static final String VIDEO = "https://v.redd.it/$1/HLSPlaylist.m3u8";

    @Override
    public boolean isPatchIncluded() {
        // Overridden by patch.
        return false;
    }

    @NonNull
    @Override
    protected Response doIntercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        HttpUrl url = request.url();

        if (!url.host().endsWith("reddit.com")) {
            return chain.proceed(request);
        }

        Response response = chain.proceed(request);
        if (!response.isSuccessful() || response.body() == null) {
            return response;
        }

        MediaType contentType = response.body().contentType();
        if (contentType == null || !"json".equals(contentType.subtype())) {
            return response;
        }

        // Reading the body consumes it, so the response has to be rebuilt either way.
        String original = response.body().string();
        response.close();

        Matcher player = PLAYER_LINK.matcher(original);
        if (!player.find()) {
            return rebuilt(response, request, contentType, original);
        }

        // Rewritten in the response rather than in the comment it belongs to: the same link is
        // written the same way wherever a comment is served, and the id is all that is needed.
        String rewritten = player.reset().replaceAll(VIDEO);
        Logger.printInfo(() -> "Pointed a comment's video at where it is kept");
        return rebuilt(response, request, contentType, rewritten);
    }

    private static Response rebuilt(Response response, Request request, MediaType contentType,
                                    String body) {
        return response.newBuilder()
                .request(request)
                .body(ResponseBody.create(body, contentType))
                .build();
    }
}
