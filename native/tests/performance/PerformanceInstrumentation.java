package com.jeremykenedy.firetv.ui.nativeapp.providers;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.FrameMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.jeremykenedy.firetv.ui.nativeapp.cards.ArtworkLoader;
import com.jeremykenedy.firetv.ui.nativeapp.cards.ChannelCardView;
import com.jeremykenedy.firetv.ui.nativeapp.focus.FocusTileView;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded emulator comparison workload; creates no app preferences or provider rows. */
public final class PerformanceInstrumentation extends Instrumentation {
    private Activity activity;
    private boolean snapshotOnly;
    private ScrollView overlay;
    private View originalFocus;
    private final List<FocusTileView> apps = new ArrayList<>();
    private final List<FocusTileView> synthetic = new ArrayList<>();
    private final List<ChannelCardView> cards = new ArrayList<>();
    private final List<Long> frames = Collections.synchronizedList(new ArrayList<Long>());
    private final AtomicInteger dropped = new AtomicInteger();
    private final Window.OnFrameMetricsAvailableListener frameListener = new Window.OnFrameMetricsAvailableListener() {
        @Override public void onFrameMetricsAvailable(Window window, FrameMetrics metrics, int missed) {
            dropped.addAndGet(missed);
            frames.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION));
        }
    };

    @Override public void onCreate(Bundle args) { super.onCreate(args); snapshotOnly = "true".equals(args.getString("snapshot_only")); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        if (snapshotOnly) {
            try { result.putString("preferences_digest", preferenceDigest()); }
            catch (Exception failure) { result.putString("reason", failure.toString()); }
            finish(0, result);
            return;
        }
        try {
            activity = startActivitySync(new Intent().setClassName(getTargetContext(),
                    "com.jeremykenedy.firetv.ui.nativeapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            for (int i = 0; i < 60 && apps.size() < 2; i++) {
                onMain(new Runnable() { @Override public void run() {
                    apps.clear(); collect(activity.getWindow().getDecorView());
                }});
                if (apps.size() < 2) SystemClock.sleep(50);
            }
            check(apps.size() >= 2, "Two installed app tiles are required.");
            result.putInt("real_app_tiles", apps.size());
            result.putLong("pss_before_kib", Debug.getPss());
            onMain(new Runnable() { @Override public void run() {
                originalFocus = activity.getCurrentFocus();
                activity.getWindow().addOnFrameMetricsAvailableListener(frameListener, new Handler(Looper.getMainLooper()));
                apps.get(0).requestFocus();
            }});
            long start = SystemClock.elapsedRealtime();
            for (int i = 0; i < 32; i++) {
                final int expected = i % 2 == 0 ? 1 : 0;
                sendKeyDownUpSync(i % 2 == 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT);
                onMain(new Runnable() { @Override public void run() {
                    check(apps.get(expected).isFocused(), "Real D-pad movement must reach the next app immediately.");
                }});
                SystemClock.sleep(20);
            }
            result.putLong("real_32_dpad_workload_ms", SystemClock.elapsedRealtime() - start);
            snapshotFrames(result, "real_focus");
            long created = SystemClock.elapsedRealtime();
            onMain(new Runnable() { @Override public void run() { createInventory(); }});
            waitForIdleSync();
            result.putLong("synthetic_create_and_idle_ms", SystemClock.elapsedRealtime() - created);
            result.putInt("synthetic_tiles", synthetic.size());
            result.putInt("synthetic_cards", cards.size());
            for (int i = 0; i < 32; i++) {
                final int index = i;
                onMain(new Runnable() { @Override public void run() {
                    check(synthetic.get(index).requestFocus(), "Synthetic inventory tile must accept focus.");
                    for (ChannelCardView card : cards) card.showNextProgram();
                }});
                SystemClock.sleep(20);
            }
            SystemClock.sleep(150);
            snapshotFrames(result, "synthetic_inventory");
            cancellation(result);
            result.putLong("pss_loaded_kib", Debug.getPss());
            Bundle phase = new Bundle();
            phase.putString("phase", "workload_done");
            sendStatus(1, phase);
            SystemClock.sleep(5000);
            result.putString("status", "PASS");
            result.putString("limits", "Emulator View-window metrics; no physical remote latency, TV FPS, real network or large installed-package enumeration claim.");
        } catch (Throwable failure) {
            result.putString("status", "FAIL");
            result.putString("reason", failure.toString());
        } finally {
            if (activity != null) onMain(new Runnable() { @Override public void run() {
                activity.getWindow().removeOnFrameMetricsAvailableListener(frameListener);
                for (ChannelCardView card : cards) card.onHostPause();
                if (overlay != null && overlay.getParent() instanceof ViewGroup)
                    ((ViewGroup) overlay.getParent()).removeView(overlay);
                if (originalFocus != null) originalFocus.requestFocus();
            }});
            result.putBoolean("temporary_views_removed", overlay == null || overlay.getParent() == null);
        }
        finish(0, result);
    }

    private String preferenceDigest() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        File[] files = new File(getTargetContext().getDataDir(), "shared_prefs").listFiles();
        if (files != null) {
            Arrays.sort(files);
            for (File file : files) if (file.getName().endsWith(".xml")) {
                digest.update(file.getName().getBytes("UTF-8"));
                try (FileInputStream input = new FileInputStream(file)) {
                    byte[] bytes = new byte[8192];
                    int count;
                    while ((count = input.read(bytes)) != -1) digest.update(bytes, 0, count);
                }
            }
        }
        StringBuilder value = new StringBuilder();
        for (byte part : digest.digest()) value.append(String.format(java.util.Locale.ROOT, "%02x", part & 255));
        return value.toString();
    }

    private void createInventory() {
        overlay = new ScrollView(activity);
        overlay.setBackgroundColor(0xff100d28);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        overlay.addView(content);
        LinearLayout row = new LinearLayout(activity);
        content.addView(row);
        String source = getTargetContext().getPackageName();
        Uri art = Uri.parse("android.resource://" + source + "/drawable/icon_laptop");
        for (int i = 0; i < 8; i++) {
            List<TvRecommendations.Program> programs = new ArrayList<>();
            for (int j = 0; j < 4; j++) programs.add(new TvRecommendations.Program("perf:" + i + ":" + j,
                    source, "Fixture " + j, art, ""));
            ChannelCardView card = new ChannelCardView(activity,
                    new TvRecommendations.Feed("perf:" + i, "Fixture card " + i, source, programs), null);
            row.addView(card, new LinearLayout.LayoutParams(dp(112), dp(120)));
            cards.add(card);
        }
        GridLayout grid = new GridLayout(activity);
        grid.setColumnCount(8);
        content.addView(grid);
        for (int i = 0; i < 64; i++) {
            FocusTileView tile = new FocusTileView(activity);
            tile.setTitle("Fixture " + i);
            tile.setIcon(new ColorDrawable(Color.rgb(i * 3, 100, 160)));
            grid.addView(tile, new ViewGroup.LayoutParams(dp(112), dp(90)));
            synthetic.add(tile);
        }
        activity.addContentView(overlay, new FrameLayout.LayoutParams(-1, -1));
        for (ChannelCardView card : cards) card.onHostResume();
    }

    private void cancellation(Bundle result) throws InterruptedException {
        final AtomicInteger cancelledCallbacks = new AtomicInteger();
        final CountDownLatch completed = new CountDownLatch(1);
        onMain(new Runnable() { @Override public void run() {
            Uri art = Uri.parse("android.resource://" + getTargetContext().getPackageName() + "/drawable/icon_laptop");
            for (int i = 0; i < 16; i++) {
                ArtworkLoader.Request request = ArtworkLoader.load(activity, art, new ArtworkLoader.Callback() {
                    @Override public void onLoaded(android.graphics.Bitmap bitmap) { cancelledCallbacks.incrementAndGet(); }
                });
                request.cancel();
            }
            ArtworkLoader.load(activity, art, new ArtworkLoader.Callback() {
                @Override public void onLoaded(android.graphics.Bitmap bitmap) { if (bitmap != null) completed.countDown(); }
            });
        }});
        check(completed.await(3, TimeUnit.SECONDS), "Valid local artwork must complete after cancellation burst.");
        waitForIdleSync();
        check(cancelledCallbacks.get() == 0, "Cancelled requests must not deliver obsolete artwork.");
        result.putInt("cancelled_requests", 16);
        result.putInt("obsolete_callbacks", cancelledCallbacks.get());
    }

    private void snapshotFrames(Bundle result, String prefix) {
        List<Long> values;
        synchronized (frames) { values = new ArrayList<>(frames); frames.clear(); }
        Collections.sort(values);
        result.putInt(prefix + "_frame_count", values.size());
        if (!values.isEmpty()) {
            result.putDouble(prefix + "_frame_p50_ms", values.get(values.size() / 2) / 1000000d);
            result.putDouble(prefix + "_frame_p95_ms", values.get(Math.min(values.size() - 1,
                    (int) Math.ceil(values.size() * .95) - 1)) / 1000000d);
            result.putDouble(prefix + "_frame_max_ms", values.get(values.size() - 1) / 1000000d);
        }
        result.putInt(prefix + "_dropped_metric_callbacks", dropped.getAndSet(0));
    }

    private void collect(View view) {
        if (view instanceof FocusTileView && view.isFocusable()) apps.add((FocusTileView) view);
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            collect(((ViewGroup) view).getChildAt(i));
    }

    private void onMain(final Runnable work) {
        final Throwable[] error = new Throwable[1];
        runOnMainSync(new Runnable() { @Override public void run() { try { work.run(); } catch (Throwable failure) { error[0] = failure; } }});
        if (error[0] != null) throw new AssertionError(error[0]);
    }

    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
