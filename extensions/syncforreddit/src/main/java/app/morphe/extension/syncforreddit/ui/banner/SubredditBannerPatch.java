package app.morphe.extension.syncforreddit.ui.banner;

import android.graphics.Bitmap;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

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
            // Deferred by a frame: the feed's own view is what arrives here, and the strip goes
            // in whatever holds it, which is not always attached yet.
            root.post(() -> {
                try {
                    draw((ViewGroup) root, subreddit);
                } catch (Throwable ex) {
                    Logger.printInfo(() -> "banner: could not be drawn: " + ex);
                }
            });
        } catch (Throwable ex) {
            // A feed that draws is worth more than the banner above it.
            Logger.printInfo(() -> "banner: could not be added: " + ex);
        }
    }

    /** Reads the patched flag through an instance, since the flag is an instance method. */
    private static boolean isIncluded() {
        return new SubredditBannerPatch().isPatchIncluded();
    }

    private static void draw(ViewGroup feed, String subreddit) {
        ViewGroup holder = feed.getParent() instanceof ViewGroup
                ? (ViewGroup) feed.getParent() : null;
        if (holder == null) {
            Logger.printInfo(() -> "banner: the feed is not in anything the strip can go in");
            return;
        }

        ImageView strip = holder.findViewWithTag(TAG);
        if (strip == null) {
            strip = new ImageView(holder.getContext());
            strip.setTag(TAG);
            strip.setScaleType(ImageView.ScaleType.CENTER_CROP);
            strip.setVisibility(View.GONE);

            int height = height(holder);
            if (holder instanceof FrameLayout) {
                // Stacked on top of the feed, which is what a frame does with its children.
                strip.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, height, Gravity.TOP));
            } else {
                Logger.printInfo(() -> "banner: the feed sits in a "
                        + holder.getClass().getName() + ", which is not a frame; placing the "
                        + "strip by its own rules and hoping it lands at the top");
                strip.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, height));
            }
            // Added last so it is drawn over the posts rather than behind them.
            holder.addView(strip);

            final ViewGroup added = holder;
            Logger.printInfo(() -> "banner: added the strip to " + added.getClass().getName()
                    + ", now " + added.getChildCount() + " children");
        }

        final ImageView showing = strip;
        Bitmap picture = SubredditBanners.bannerFor(subreddit,
                () -> showing.post(() -> {
                    try {
                        draw(feed, subreddit);
                    } catch (Throwable ex) {
                        Logger.printInfo(() -> "banner: could not be drawn: " + ex);
                    }
                }));
        if (picture == null) {
            Logger.printDebug(() -> "banner: nothing to draw for r/" + subreddit + " yet");
            makeRoom(feed, 0);
            showing.setVisibility(View.GONE);
            return;
        }
        showing.setImageBitmap(picture);
        showing.setVisibility(View.VISIBLE);
        makeRoom(feed, height(holder));
        Logger.printInfo(() -> "banner: drawn for r/" + subreddit);
    }

    private static int height(ViewGroup holder) {
        return (int) (HEIGHT_DP
                * holder.getContext().getResources().getDisplayMetrics().density);
    }

    /**
     * Starts the posts below the strip rather than behind it. The list keeps drawing into the
     * space as it scrolls, so the strip stands still and the posts pass under it.
     */
    private static void makeRoom(ViewGroup feed, int height) {
        View list = listIn(feed);
        if (list == null) {
            Logger.printInfo(() -> "banner: no list in the feed to start below the strip");
            return;
        }
        if (list.getPaddingTop() == height) {
            return;
        }
        if (list instanceof ViewGroup) {
            ((ViewGroup) list).setClipToPadding(false);
        }
        list.setPadding(list.getPaddingLeft(), height, list.getPaddingRight(),
                list.getPaddingBottom());
        Logger.printInfo(() -> "banner: the list now starts " + height + " below the top");
    }

    /** @return The posts themselves, found by what they are rather than by an obfuscated id. */
    private static View listIn(ViewGroup feed) {
        for (int at = 0; at < feed.getChildCount(); at++) {
            View child = feed.getChildAt(at);
            if (child.getClass().getName().contains("RecyclerView")) {
                return child;
            }
        }
        return null;
    }

    // endregion
}
