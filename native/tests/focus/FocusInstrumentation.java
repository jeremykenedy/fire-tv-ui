package com.jeremykenedy.firetv.ui.nativeapp.focus;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class FocusInstrumentation extends Instrumentation {
    private Activity activity;
    private final List<FocusTileView> tiles = new ArrayList<>();

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences prefs = getTargetContext().getSharedPreferences("native_focus", Context.MODE_PRIVATE);
        Map<String, ?> original = prefs.getAll();
        try {
            activity = startActivitySync(new Intent().setClassName(getTargetContext(),
                    "com.jeremykenedy.firetv.ui.nativeapp.MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            waitForIdleSync();
            for (int attempt = 0; attempt < 40 && tiles.size() < 2; attempt++) {
                SystemClock.sleep(100);
                onMain(new Runnable() {
                    @Override public void run() {
                        tiles.clear();
                        collect(activity.getWindow().getDecorView());
                    }
                });
            }
            check(tiles.size() >= 2, "Two installed sample apps are needed.");
            check(FocusPreferences.load(activity).durationMillis == 100, "Fresh defaults must be Fast.");
            configure(new FocusOptions(100, 10, true, 0xffffff, 100));
            onMain(new Runnable() {
                @Override public void run() { tiles.get(0).requestFocus(); }
            });
            long started = SystemClock.uptimeMillis();
            for (int i = 0; i < 32; i++) {
                final int expected = i % 2 == 0 ? 1 : 0;
                sendKeyDownUpSync(i % 2 == 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT);
                onMain(new Runnable() {
                    @Override public void run() {
                        check(tiles.get(expected).isFocused(), "Remote focus must move immediately, without waiting for animation.");
                    }
                });
            }
            result.putLong("rapid_32_dpad_ms", SystemClock.uptimeMillis() - started);
            SystemClock.sleep(180);
            onMain(new Runnable() {
                @Override public void run() {
                    for (FocusTileView tile : tiles) {
                        View surface = tile.getChildAt(0);
                        float expected = tile.isFocused() ? 1.1f : 1f;
                        check(Math.abs(surface.getScaleX() - expected) < 0.002f,
                                "Cancelled transitions must not leave queued growth.");
                        check(surface.getWidth() * surface.getScaleX() <= tile.getWidth() + 1,
                                "The grown surface must fit its allocation.");
                        check(surface.getHeight() * surface.getScaleY() <= tile.getHeight() + 1,
                                "The grown surface must fit vertically.");
                    }
                }
            });
            configure(new FocusOptions(0, 0, false, 0x25a6e7, 43));
            onMain(new Runnable() {
                @Override public void run() {
                    for (FocusTileView tile : tiles) check(tile.getChildAt(0).getScaleX() == 1f,
                            "None growth must remain unscaled.");
                }
            });
            final int[] pixels = new int[3];
            for (int i = 0; i < 3; i++) {
                configure(new FocusOptions(0, 0, true, 0xffffff, i * 50));
                final int index = i;
                onMain(new Runnable() {
                    @Override public void run() {
                        View surface = tiles.get(0).getChildAt(0);
                        Bitmap image = Bitmap.createBitmap(surface.getWidth(), surface.getHeight(), Bitmap.Config.ARGB_8888);
                        surface.getBackground().draw(new Canvas(image));
                        int y = Math.round(1.5f * activity.getResources().getDisplayMetrics().density);
                        pixels[index] = image.getPixel(image.getWidth() / 2, y);
                        image.recycle();
                    }
                });
            }
            check(Color.red(pixels[0]) < Color.red(pixels[1]) && Color.red(pixels[1]) < Color.red(pixels[2]),
                    "Rendered 50-percent border must differ from both transparent and opaque borders.");
            FocusPreferences.save(activity, new FocusOptions(180, 3, false, 0x345678, 27));
            FocusOptions read = FocusPreferences.load(getTargetContext());
            check(read.durationMillis == 180 && read.growthPercent == 3 && !read.borderEnabled
                    && read.borderRgb == 0x345678 && read.borderOpacity == 27,
                    "Native preferences must preserve every independent control.");
            result.putString("status", "PASS");
            result.putInt("tiles", tiles.size());
            result.putString("border_samples", java.util.Arrays.toString(pixels));
            result.putBoolean("no_queued_animation", true);
            result.putBoolean("growth_contained", true);
            result.putBoolean("independent_border_alpha", true);
        } catch (Throwable error) {
            result.putString("status", "FAIL");
            result.putString("reason", error.toString());
        } finally {
            SharedPreferences.Editor restore = prefs.edit().clear();
            for (Map.Entry<String, ?> entry : original.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Integer) restore.putInt(entry.getKey(), (Integer) value);
                else if (value instanceof Boolean) restore.putBoolean(entry.getKey(), (Boolean) value);
                else if (value instanceof String) restore.putString(entry.getKey(), (String) value);
            }
            result.putBoolean("review_preferences_restored", restore.commit());
        }
        finish(0, result);
    }

    private void onMain(final Runnable action) {
        final Throwable[] failure = new Throwable[1];
        runOnMainSync(new Runnable() {
            @Override public void run() {
                try { action.run(); } catch (Throwable error) { failure[0] = error; }
            }
        });
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }

    private void configure(final FocusOptions options) {
        onMain(new Runnable() {
            @Override public void run() {
                FocusPreferences.save(activity, options);
                for (FocusTileView tile : tiles) tile.refreshPreferences();
            }
        });
    }

    private void collect(View view) {
        if (view instanceof FocusTileView) tiles.add((FocusTileView) view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i));
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
