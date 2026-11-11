package com.jeremykenedy.firetv.ui.nativeapp.providers;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.cards.ArtworkLoader;
import com.jeremykenedy.firetv.ui.nativeapp.cards.ChannelCardView;
import com.jeremykenedy.firetv.ui.nativeapp.cards.CardDisplayPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.cards.CardPreferences;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Emulator-only renderer checks with exact artwork preference restoration. */
public final class ChannelRenderInstrumentation extends Instrumentation {
    private Activity activity;
    private ChannelCardView card;
    private String selected;
    private String longPressed;

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        final SharedPreferences preferences = getTargetContext().getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE);
        final Object originalFit = preferences.getAll().get("featured_artwork_fit");
        final Object originalHiddenTitles = preferences.getAll().get("hide_watch_next_titles");
        final Object originalOutside = preferences.getAll().get("text_outside_cards");
        try {
            check(originalFit == null || originalFit instanceof Boolean, "Artwork setting must have a valid original type.");
            check(originalHiddenTitles == null || originalHiddenTitles instanceof Boolean,
                    "Hidden title setting must have a valid original type.");
            check(originalOutside == null || originalOutside instanceof Boolean,
                    "Outside title setting must have a valid original type.");
            CardPreferences.setArtworkFit(getTargetContext(), true);
            activity = startActivitySync(new Intent().setClassName(getTargetContext(),
                    "com.jeremykenedy.firetv.ui.nativeapp.MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            final String source = getTargetContext().getPackageName();
            final Uri artwork = Uri.parse("android.resource://" + source + "/drawable/icon_laptop");
            final TvRecommendations.Program first = new TvRecommendations.Program("render:first", source,
                    "First title", artwork, "");
            final TvRecommendations.Program second = new TvRecommendations.Program("render:second", source,
                    "Second title", artwork, "");
            final TvRecommendations.Feed feed = new TvRecommendations.Feed("render:feed", "Renderer check", source,
                    Arrays.asList(first, second));
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN);
            waitForIdleSync();
            onMain(new Runnable() {
                @Override public void run() {
                    card = new ChannelCardView(activity, feed, new ChannelCardView.Listener() {
                        @Override public void onSelect(TvRecommendations.Feed value, TvRecommendations.Program item) {
                            selected = item == null ? "empty" : item.key;
                        }
                        @Override public void onLongPress(TvRecommendations.Feed value, TvRecommendations.Program item) {
                            longPressed = item == null ? "empty" : item.key;
                        }
                    });
                    FrameLayout.LayoutParams size = new FrameLayout.LayoutParams(480, 300, Gravity.TOP | Gravity.RIGHT);
                    activity.addContentView(card, size);
                    card.onHostResume();
                    check(card.requestFocus(), "Card must accept D-pad focus.");
                }
            });
            waitForIdleSync();
            waitForArtwork();
            onMain(new Runnable() {
                @Override public void run() {
                    ImageView image = image(card);
                    check(image != null && image.getScaleType() == ImageView.ScaleType.FIT_CENTER,
                            "Artwork must fit with its proportions preserved.");
                    CardPreferences.setArtworkFit(activity, false);
                    card.updateFeed(feed);
                    check(image.getScaleType() == ImageView.ScaleType.CENTER_CROP,
                            "Saved Fill choice crops artwork without replacing its drawable or focus target.");
                    CardPreferences.setArtworkFit(activity, true);
                    card.updateFeed(feed);
                    check(image.getScaleType() == ImageView.ScaleType.FIT_CENTER,
                            "Switching back to Fit restores the entire image.");
                    check(card.hasFocus() && card.findFocus() == card, "Only the stable outer card takes focus.");
                    card.showNextProgram();
                    check(card.currentProgram() == second && card.isFocused(), "Manual advance preserves card focus.");
                    card.performClick();
                    card.performLongClick();
                    check(second.key.equals(selected) && second.key.equals(longPressed),
                            "Actions use the currently displayed item.");
                    card.updateFeed(new TvRecommendations.Feed(feed.item.id, feed.title, source,
                            Arrays.asList(second, first)));
                    check(card.currentProgram() == second && card.isFocused(), "Refresh preserves the current item and focus.");
                    card.setRotationIntervalMillis(40);
                }
            });
            waitForDifferent(second.key);
            final String[] beforeRefresh = new String[1];
            onMain(new Runnable() { @Override public void run() {
                card.setRotationIntervalMillis(1200);
                beforeRefresh[0] = card.currentProgram().key;
            }});
            boolean rotatedDuringRefresh = false;
            long refreshUntil = SystemClock.uptimeMillis() + 3200;
            while (SystemClock.uptimeMillis() < refreshUntil) {
                SystemClock.sleep(200);
                onMain(new Runnable() { @Override public void run() {
                    card.updateFeed(feed);
                    card.setRotationIntervalMillis(1200);
                }});
                if (!beforeRefresh[0].equals(currentKey())) { rotatedDuringRefresh = true; break; }
            }
            check(rotatedDuringRefresh, "Repeated equivalent refreshes must not defer rotation indefinitely.");
            onMain(new Runnable() { @Override public void run() { card.setRotationIntervalMillis(3000); } });
            final String beforeChange = currentKey();
            SystemClock.sleep(100);
            onMain(new Runnable() { @Override public void run() { card.setRotationIntervalMillis(400); } });
            long changedUntil = SystemClock.uptimeMillis() + 1800;
            while (beforeChange.equals(currentKey()) && SystemClock.uptimeMillis() < changedUntil) SystemClock.sleep(50);
            check(!beforeChange.equals(currentKey()), "Changed interval must replace the previous deadline.");
            onMain(new Runnable() { @Override public void run() { card.onHostPause(); } });
            final String paused = currentKey();
            SystemClock.sleep(600);
            check(paused.equals(currentKey()), "Paused cards must not rotate.");
            onMain(new Runnable() {
                @Override public void run() {
                    card.setRotationIntervalMillis(0);
                    card.onHostResume();
                }
            });
            SystemClock.sleep(180);
            check(paused.equals(currentKey()), "Manual mode must not schedule rotation.");
            onMain(new Runnable() {
                @Override public void run() {
                    card.setPrograms(Collections.singletonList(first));
                    check(card.currentProgram() == first && card.isFocused(), "Hide refresh keeps the same focused card.");
                    card.setPrograms(Collections.<TvRecommendations.Program>emptyList());
                    check(card.currentProgram() == null && card.isFocused(), "All hidden items keep an empty stable card.");
                    card.performLongClick();
                    check("empty".equals(longPressed), "Empty cards retain their management action.");
                }
            });
            onMain(new Runnable() {
                @Override public void run() {
                    card.setPrograms(Collections.singletonList(first));
                    int width = Math.round(64 * activity.getResources().getDisplayMetrics().density);
                    int height = Math.round(48 * activity.getResources().getDisplayMetrics().density);
                    card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                    card.layout(0, 0, width, height);
                    ImageView image = image(card);
                    check(image.getMeasuredWidth() > 0 && image.getMeasuredHeight() > 0,
                            "The minimum supported card must retain a real artwork area.");
                    check(image.getScaleType() == ImageView.ScaleType.FIT_CENTER,
                            "Compact artwork must keep Fit rendering.");
                    card.setPrograms(Collections.<TvRecommendations.Program>emptyList());
                    card.performClick();
                    check("empty".equals(selected), "Ordinary Select manages an all-hidden card.");
                }
            });
            checkMissingArtwork(source, first);
            final CountDownLatch rejected = new CountDownLatch(1);
            ArtworkLoader.load(getTargetContext(), Uri.parse("file:///etc/hosts"), new ArtworkLoader.Callback() {
                @Override public void onLoaded(android.graphics.Bitmap bitmap) {
                    if (bitmap == null) rejected.countDown();
                }
            });
            check(rejected.await(2, TimeUnit.SECONDS), "File artwork must be rejected.");
            checkArtworkDeadlines(source);
            result.putString("status", "PASS");
            result.putBoolean("stable_focus_rotation_refresh_hide", true);
            result.putBoolean("manual_and_paused_rotation", true);
            result.putBoolean("rotation_deadline_survives_refresh", true);
            result.putBoolean("changed_interval_replaces_deadline", true);
            result.putBoolean("current_item_actions", true);
            result.putBoolean("native_fit_artwork", true);
            result.putBoolean("missing_artwork_focus_identity", true);
            result.putBoolean("shared_artwork_uses_current_title", true);
            result.putBoolean("minimum_card_artwork_area", true);
            result.putBoolean("stalled_pipe_deadline_and_async_cancel", true);
            result.putString("limits", "Local resources and test content pipes only; HTTPS servers and physical TV visuals remain unverified.");
        } catch (Throwable error) {
            result.putString("status", "FAIL");
            result.putString("reason", error.toString());
        } finally {
            if (activity != null) onMain(new Runnable() {
                @Override public void run() {
                    if (card != null) card.onHostPause();
                    activity.finish();
                }
            });
            if (originalFit == null || originalFit instanceof Boolean) {
                SharedPreferences.Editor restore = preferences.edit();
                if (originalFit == null) restore.remove("featured_artwork_fit");
                else restore.putBoolean("featured_artwork_fit", (Boolean) originalFit);
                if (!restore.commit() || !java.util.Objects.equals(originalFit, preferences.getAll().get("featured_artwork_fit"))) {
                    result.putString("status", "FAIL");
                    result.putString("reason", "Artwork preference restoration failed.");
                }
            }
            if (originalHiddenTitles == null || originalHiddenTitles instanceof Boolean) {
                SharedPreferences.Editor restore = preferences.edit();
                if (originalHiddenTitles == null) restore.remove("hide_watch_next_titles");
                else restore.putBoolean("hide_watch_next_titles", (Boolean) originalHiddenTitles);
                if (!restore.commit() || !java.util.Objects.equals(originalHiddenTitles,
                        preferences.getAll().get("hide_watch_next_titles"))) {
                    result.putString("status", "FAIL");
                    result.putString("reason", "Hidden title preference restoration failed.");
                }
            }
            if (originalOutside == null || originalOutside instanceof Boolean) {
                SharedPreferences.Editor restore = preferences.edit();
                if (originalOutside == null) restore.remove("text_outside_cards");
                else restore.putBoolean("text_outside_cards", (Boolean) originalOutside);
                if (!restore.commit() || !java.util.Objects.equals(originalOutside,
                        preferences.getAll().get("text_outside_cards"))) {
                    result.putString("status", "FAIL");
                    result.putString("reason", "Outside title preference restoration failed.");
                }
            }
        }
        finish(0, result);
    }

    private void checkMissingArtwork(final String source, final TvRecommendations.Program loaded) {
        final Uri stalled = Uri.parse("content://com.jeremykenedy.firetv.ui.cardsproof.artwork/stall");
        final TvRecommendations.Program missing = new TvRecommendations.Program("render:missing", source,
                "Private program title", null, "");
        final TvRecommendations.Program failed = new TvRecommendations.Program("render:failed", source,
                "Failed program title", Uri.parse("file:///not-an-image"), "");
        final TvRecommendations.Program slow = new TvRecommendations.Program("render:slow", source,
                "Slow program title", stalled, "");
        CardDisplayPreferences.setHideTitles(getTargetContext(), true);
        onMain(new Runnable() { @Override public void run() {
            card.updateFeed(new TvRecommendations.Feed(card.feed().item.id, card.feed().title, source,
                    Collections.singletonList(missing)));
            ViewGroup.LayoutParams size = card.getLayoutParams();
            size.height = Math.round(100 * card.getResources().getDisplayMetrics().density);
            card.setLayoutParams(size);
            check(card.isFocused() && image(card).getDrawable() == null,
                    "Missing artwork must retain the same focused card.");
            check(visibleText(card.getChildAt(1), "Renderer check") && !visibleText(card, missing.title),
                    "A compact card without artwork must identify its feed without exposing hidden program titles.");
        }});
        waitForIdleSync();
        onMain(new Runnable() { @Override public void run() {
            check(card.getChildAt(0).getVisibility() == View.GONE,
                    "The missing-artwork card must be compact without a separate feed heading.");
        }});
        onMain(new Runnable() { @Override public void run() {
            card.updateFeed(new TvRecommendations.Feed(card.feed().item.id, card.feed().title, source,
                    Collections.singletonList(failed)));
        }});
        SystemClock.sleep(200);
        onMain(new Runnable() { @Override public void run() {
            check(visibleText(card.getChildAt(1), "Renderer check") && !visibleText(card, failed.title),
                    "Failed artwork must keep a visible feed identity.");
            card.updateFeed(new TvRecommendations.Feed(card.feed().item.id, card.feed().title, source,
                    Arrays.asList(slow, loaded)));
            check(visibleText(card.getChildAt(1), "Renderer check"),
                    "Pending artwork must keep the card identifiable.");
            card.showNextProgram();
        }});
        waitForArtwork();
        SystemClock.sleep(6200);
        onMain(new Runnable() { @Override public void run() {
            check(card.currentProgram() == loaded && card.isFocused()
                            && image(card).getDrawable() instanceof BitmapDrawable
                            && !visibleText(card.getChildAt(1), "Renderer check"),
                    "A stale failed load must not replace the later program artwork or feed fallback state.");
        }});
        final Uri sharedArtwork = Uri.parse("content://com.jeremykenedy.firetv.ui.cardsproof.artwork/slow-image");
        final TvRecommendations.Program earlier = new TvRecommendations.Program("render:shared-earlier", source,
                "Earlier shared title", sharedArtwork, "");
        final TvRecommendations.Program later = new TvRecommendations.Program("render:shared-later", source,
                "Later shared title", sharedArtwork, "");
        CardDisplayPreferences.setHideTitles(getTargetContext(), false);
        CardDisplayPreferences.setTextOutside(getTargetContext(), false);
        onMain(new Runnable() { @Override public void run() {
            card.updateFeed(new TvRecommendations.Feed(card.feed().item.id, card.feed().title, source,
                    Arrays.asList(earlier, later)));
            card.showNextProgram();
            check(card.currentProgram() == later && visibleText(card.getChildAt(1), later.title),
                    "Rotating shared artwork must immediately show the current program title.");
        }});
        SystemClock.sleep(1200);
        onMain(new Runnable() { @Override public void run() {
            check(card.currentProgram() == later && card.isFocused()
                            && image(card).getDrawable() instanceof BitmapDrawable
                            && visibleText(card.getChildAt(1), later.title)
                            && !visibleText(card.getChildAt(1), earlier.title)
                            && ("Renderer check: " + later.title).contentEquals(card.getContentDescription()),
                    "A shared artwork callback must retain the current program title and accessibility label.");
        }});
    }

    private static boolean visibleText(View view, String value) {
        if (view.getVisibility() != View.VISIBLE) return false;
        if (view instanceof TextView && value.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            if (visibleText(((ViewGroup) view).getChildAt(i), value)) return true;
        return false;
    }

    private void checkArtworkDeadlines(String source) throws InterruptedException {
        Uri stalled = Uri.parse("content://com.jeremykenedy.firetv.ui.cardsproof.artwork/stall");
        final AtomicInteger cancelledCallbacks = new AtomicInteger();
        ArtworkLoader.Callback cancelled = new ArtworkLoader.Callback() {
            @Override public void onLoaded(android.graphics.Bitmap bitmap) { cancelledCallbacks.incrementAndGet(); }
        };
        final ArtworkLoader.Request one = ArtworkLoader.load(getTargetContext(), stalled, cancelled);
        final ArtworkLoader.Request two = ArtworkLoader.load(getTargetContext(), stalled, cancelled);
        SystemClock.sleep(120);
        onMain(new Runnable() {
            @Override public void run() {
                long start = SystemClock.elapsedRealtime();
                one.cancel();
                two.cancel();
                check(SystemClock.elapsedRealtime() - start < 250, "Main-thread cancellation must not wait for pipe IO.");
            }
        });
        final CountDownLatch recovered = new CountDownLatch(1);
        ArtworkLoader.load(getTargetContext(), Uri.parse("android.resource://" + source + "/drawable/icon_jbl"),
                new ArtworkLoader.Callback() {
                    @Override public void onLoaded(android.graphics.Bitmap bitmap) {
                        if (bitmap != null) recovered.countDown();
                    }
                });
        check(recovered.await(3, TimeUnit.SECONDS), "Cancelling stalled pipes must release artwork workers.");
        check(cancelledCallbacks.get() == 0, "Cancelled work must not deliver stale artwork.");
        final CountDownLatch expired = new CountDownLatch(1);
        long started = SystemClock.elapsedRealtime();
        ArtworkLoader.load(getTargetContext(), stalled, new ArtworkLoader.Callback() {
            @Override public void onLoaded(android.graphics.Bitmap bitmap) {
                if (bitmap == null) expired.countDown();
            }
        });
        check(expired.await(8, TimeUnit.SECONDS), "Stalled artwork must finish by its total deadline.");
        long elapsed = SystemClock.elapsedRealtime() - started;
        check(elapsed >= 5000 && elapsed < 8000, "The stalled pipe must use the actual bounded deadline.");
    }

    private String currentKey() {
        final String[] value = new String[1];
        onMain(new Runnable() { @Override public void run() { value[0] = card.currentProgram().key; } });
        return value[0];
    }

    private void waitForDifferent(String previous) {
        long end = SystemClock.uptimeMillis() + 2000;
        while (SystemClock.uptimeMillis() < end) {
            if (!previous.equals(currentKey())) return;
            SystemClock.sleep(10);
        }
        throw new AssertionError("A resumed timed card must rotate.");
    }

    private void waitForArtwork() {
        long end = SystemClock.uptimeMillis() + 3000;
        final boolean[] loaded = new boolean[1];
        while (SystemClock.uptimeMillis() < end) {
            onMain(new Runnable() {
                @Override public void run() {
                    ImageView image = image(card);
                    loaded[0] = image != null && image.getDrawable() instanceof BitmapDrawable;
                }
            });
            if (loaded[0]) return;
            SystemClock.sleep(20);
        }
        throw new AssertionError("Native resource artwork must load asynchronously.");
    }

    private static ImageView image(View view) {
        if (view instanceof ImageView) return (ImageView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            ImageView result = image(((ViewGroup) view).getChildAt(i));
            if (result != null) return result;
        }
        return null;
    }

    private void onMain(final Runnable action) {
        final Throwable[] failure = new Throwable[1];
        super.runOnMainSync(new Runnable() {
            @Override public void run() {
                try { action.run(); } catch (Throwable error) { failure[0] = error; }
            }
        });
        if (failure[0] != null) throw new AssertionError("Main-thread fixture action failed: "
                + failure[0].getMessage(), failure[0]);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
