package app.morphe.extension.syncforreddit.ui.banner;

import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import java.io.IOException;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.requests.PatchedditInterceptor;
import app.morphe.extension.syncforreddit.settings.SyncUpSettings;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * The subreddit's banner, at the top of its feed rather than only on its About page.
 *
 * <p>Two halves: what goes past on the wire is watched for Reddit's answer about a subreddit, so
 * the banner is known without reaching into Sync's own model of it; and the feed is given a strip
 * at the top to draw it in.
 *
 * @noinspection unused
 */
public class SubredditBannerPatch extends PatchedditInterceptor {
    private static final String SHOW_BANNER = "sync_up_subreddit_banner";

    /** What the strip is tagged with, so it is found again rather than added twice. */
    private static final String TAG = "sync-up-subreddit-banner";

    private static final int HEIGHT_DP = 72;

    private static boolean said;

    @Override
    public boolean isPatchIncluded() {
        // Overridden by patch.
        return false;
    }

    // region What goes past on the wire.

    @NonNull
    @Override
    protected Response doIntercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        HttpUrl url = request.url();

        // Borrowed off whatever Sync is asking, so a subreddit never asked about can be.
        if (url.host().endsWith("reddit.com")) {
            SubredditBanners.noticeCredentials(
                    request.header("Authorization"), request.header("User-Agent"));
        }

        Response response = chain.proceed(request);
        String subreddit = aboutWhichSubreddit(url);
        if (subreddit == null) {
            return response;
        }

        try {
            ResponseBody body = response.body();
            if (body == null) {
                return response;
            }
            String text = body.string();
            SubredditBanners.notice(subreddit, text);
            // Read once: the body is a stream, so what was taken has to be put back.
            return response.newBuilder()
                    .body(ResponseBody.create(text, body.contentType()))
                    .build();
        } catch (Exception ex) {
            Logger.printInfo(() -> "banner: could not read the reply about r/" + subreddit
                    + ": " + ex);
            return response;
        }
    }

    /** @return The subreddit a request is asking about, or null where it asks something else. */
    private static String aboutWhichSubreddit(HttpUrl url) {
        java.util.List<String> parts = url.pathSegments();
        for (int at = 0; at + 2 < parts.size(); at++) {
            if ("r".equals(parts.get(at)) && parts.get(at + 2).startsWith("about")) {
                return parts.get(at + 1);
            }
        }
        return null;
    }

    // endregion

    // region The strip at the top of the feed.

    /**
     * Called as the feed's view is made. What the feed is about is not asked of the fragment,
     * whose every accessor is obfuscated, but taken from where Sync hands it to its own button.
     */
    public static void attach(View root) {
        if (!isIncluded()) {
            return;
        }
        try {
            if (!said) {
                said = true;
                SyncUpSettings.report("banner", SHOW_BANNER);
            }
            if (!SyncUpSettings.flag(SHOW_BANNER, true)) {
                Logger.printDebug(() -> "banner: turned off");
                return;
            }

            String subreddit = SubredditBanners.showing();
            Logger.printInfo(() -> "banner: the feed's view was made, showing r/" + subreddit);
            if (subreddit == null) {
                return;
            }
            if (!(root instanceof ViewGroup)) {
                Logger.printInfo(() -> "banner: the feed's view is not one things can go in");
                return;
            }
            draw((ViewGroup) root, subreddit);
        } catch (Throwable ex) {
            // A feed that draws is worth more than the banner above it.
            Logger.printInfo(() -> "banner: could not be added: " + ex);
        }
    }

    /** Reads the patched flag through an instance, since the flag is an instance method. */
    private static boolean isIncluded() {
        return new SubredditBannerPatch().isPatchIncluded();
    }

    private static void draw(ViewGroup root, String subreddit) {
        ImageView strip = root.findViewWithTag(TAG);
        if (strip == null) {
            ViewGroup into = whereItCanGo(root);
            if (into == null) {
                return;
            }
            strip = new ImageView(into.getContext());
            strip.setTag(TAG);
            strip.setScaleType(ImageView.ScaleType.CENTER_CROP);
            int height = (int) (HEIGHT_DP
                    * into.getContext().getResources().getDisplayMetrics().density);
            // Built to the parent's own rules rather than to a guess about which parent it is.
            ViewGroup.LayoutParams params = into.generateLayoutParams(null);
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = height;
            strip.setLayoutParams(params);
            strip.setVisibility(View.GONE);
            into.addView(strip, 0);

            final ViewGroup added = into;
            Logger.printInfo(() -> "banner: added the strip to " + added.getClass().getName()
                    + ", now " + added.getChildCount() + " children");
        }

        final ImageView showing = strip;
        Bitmap picture = SubredditBanners.bannerFor(subreddit,
                () -> showing.post(() -> draw(root, subreddit)));
        if (picture == null) {
            Logger.printDebug(() -> "banner: nothing to draw for r/" + subreddit + " yet");
            return;
        }
        showing.setImageBitmap(picture);
        showing.setVisibility(View.VISIBLE);
        Logger.printInfo(() -> "banner: drawn for r/" + subreddit);
    }

    /**
     * @return The container the strip can be put at the top of, or null where none of them
     *         stacks its children and putting one first would only hide it behind the rest.
     *
     * <p>Which layout Sync builds the feed out of is not something to assume, so what is
     *         actually there is written out the first time in full.
     */
    private static ViewGroup whereItCanGo(ViewGroup root) {
        describe(root, 0);

        if (root instanceof LinearLayout) {
            return root;
        }
        // One level down, since the feed is usually a refresh layout wrapping the list.
        for (int at = 0; at < root.getChildCount(); at++) {
            View child = root.getChildAt(at);
            if (child instanceof LinearLayout) {
                Logger.printInfo(() -> "banner: putting the strip in a child that stacks");
                return (ViewGroup) child;
            }
        }
        Logger.printInfo(() -> "banner: nothing here stacks its children, so the strip would "
                + "sit behind the feed rather than above it; not adding one");
        return null;
    }

    /** Writes out what the feed is built of, so the right place for the strip can be chosen. */
    private static void describe(View view, int depth) {
        StringBuilder indent = new StringBuilder();
        for (int at = 0; at < depth; at++) {
            indent.append("  ");
        }
        Logger.printInfo(() -> "banner: view " + indent + view.getClass().getName()
                + " id=" + view.getId()
                + (view instanceof ViewGroup
                        ? " children=" + ((ViewGroup) view).getChildCount() : ""));
        if (depth >= 2 || !(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) view;
        for (int at = 0; at < group.getChildCount(); at++) {
            describe(group.getChildAt(at), depth + 1);
        }
    }

    // endregion
}
