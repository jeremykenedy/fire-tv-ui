package com.jeremykenedy.firetv.ui.nativeapp.appearance;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.VideoView;
import java.util.Map;

public final class WallpaperInstrumentation extends Instrumentation {
    private Activity editor;
    private static final String AUTHORITY = "content://com.jeremykenedy.firetv.ui.appearanceproof.media/";
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences prefs = AppearancePreferences.file(getTargetContext());
        Map<String, ?> before = prefs.getAll();
        try {
            check(prefs.edit().clear().putString("base", "off").putString("overlay", "snow")
                    .putString("wallpaper_mode", "image").putString("wallpaper_image_uri", AUTHORITY + "image.png")
                    .putString("wallpaper_video_uri", AUTHORITY + "motion.mp4").commit(), "Prepare synthetic media");
            editor = startActivitySync(new Intent().setClassName(getTargetContext(),
                    "com.jeremykenedy.firetv.ui.nativeapp.appearance.BackgroundSettingsActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            waitForIdleSync();
            waitState(BackgroundLayer.MediaState.READY);
            onMain(new Runnable() { @Override public void run() {
                ImageView image = (ImageView) editor.getWindow().getDecorView().findViewWithTag("appearance:wallpaper");
                check(image.getDrawable() instanceof BitmapDrawable, "Actual content-provider image decoded");
                BitmapDrawable pixels = (BitmapDrawable) image.getDrawable();
                check(pixels.getBitmap().getWidth() == 320 && pixels.getBitmap().getHeight() == 180, "Image proportions retained");
                click("wallpaper_mode");
            } });
            long deadline = SystemClock.uptimeMillis() + 5000;
            boolean focused = false;
            while (SystemClock.uptimeMillis() < deadline) {
                android.view.accessibility.AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
                if (root != null) {
                    android.view.accessibility.AccessibilityNodeInfo focus = root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT);
                    focused = !root.findAccessibilityNodeInfosByText("Saved silent video").isEmpty() && focus != null
                            && "android.widget.ListView".contentEquals(focus.getClassName());
                    if (focus != null) focus.recycle();
                    root.recycle();
                }
                if (focused) break;
                SystemClock.sleep(40);
            }
            check(focused, "Wallpaper chooser receives remote focus");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            waitForIdleSync();
            check(AppearancePreferences.wallpaperMode(getTargetContext()).equals("video"), "Source chooser reuses saved video");
            waitState(BackgroundLayer.MediaState.READY);
            SystemClock.sleep(400);
            final int[] position = new int[1];
            onMain(new Runnable() { @Override public void run() {
                VideoView video = video();
                check(video.isPlaying() && video.getDuration() >= 1900, "Actual synthetic video plays");
                click("wallpaper_video_frozen");
                check(!video.isPlaying(), "Independent still-video control pauses playback");
                position[0] = video.getCurrentPosition();
            } });
            SystemClock.sleep(350);
            onMain(new Runnable() { @Override public void run() {
                check(Math.abs(video().getCurrentPosition() - position[0]) < 100, "Still video retains its frame");
                AppearanceView overlay = (AppearanceView) editor.getWindow().getDecorView().findViewWithTag("appearance:overlay");
                check(overlay.isFrameScheduled(), "Overlay keeps moving over a still video");
            } });
            check(AppearancePreferences.file(getTargetContext()).edit().putString("wallpaper_image_uri", AUTHORITY + "missing.png")
                    .putString("wallpaper_mode", "image").commit(), "Prepare unavailable image");
            onMain(new Runnable() { @Override public void run() { layer().refresh(); } });
            waitState(BackgroundLayer.MediaState.UNAVAILABLE);
            check(AppearancePreferences.load(getTargetContext()).imageUri.endsWith("missing.png"), "Unavailable media reference retained");
            check(AppearancePreferences.file(getTargetContext()).edit().putString("wallpaper_image_uri", AUTHORITY + "image.png").commit(), "Restore available fixture");
            onMain(new Runnable() { @Override public void run() { click("retry_media"); } });
            waitState(BackgroundLayer.MediaState.READY);
            result.putString("result", "PASS: actual image/video provider, source chooser, independent video freeze/overlay, unavailable media retention and retry");
        } catch (Throwable failure) {
            result.putString("failure", failure.toString());
        } finally {
            onMain(new Runnable() { @Override public void run() { if (editor != null) editor.finish(); } });
            SharedPreferences.Editor restore = prefs.edit().clear();
            for (Map.Entry<String, ?> entry : before.entrySet()) {
                Object value = entry.getValue(); String key = entry.getKey();
                if (value instanceof String) restore.putString(key, (String) value);
                else if (value instanceof Boolean) restore.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) restore.putInt(key, (Integer) value);
                else if (value instanceof Long) restore.putLong(key, (Long) value);
                else if (value instanceof Float) restore.putFloat(key, (Float) value);
                else if (value instanceof java.util.Set) {
                    @SuppressWarnings("unchecked") java.util.Set<String> values = (java.util.Set<String>) value;
                    restore.putStringSet(key, values);
                }
            }
            check(restore.commit() && prefs.getAll().equals(before), "Restore appearance preferences exactly");
        }
        finish(result.containsKey("failure") ? 1 : -1, result);
    }

    private BackgroundLayer layer() { return (BackgroundLayer) editor.getWindow().getDecorView().findViewWithTag("appearance:layers"); }
    private VideoView video() { return (VideoView) editor.getWindow().getDecorView().findViewWithTag("appearance:video"); }
    private void click(String key) {
        View action = editor.getWindow().getDecorView().findViewWithTag("appearance:" + key);
        check(action != null && action.isEnabled(), "Reachable action " + key);
        action.performClick();
    }
    private void waitState(final BackgroundLayer.MediaState state) {
        long deadline = SystemClock.uptimeMillis() + 10000;
        final boolean[] ready = new boolean[1];
        while (SystemClock.uptimeMillis() < deadline) {
            onMain(new Runnable() { @Override public void run() { ready[0] = layer().mediaState() == state; } });
            if (ready[0]) return;
            SystemClock.sleep(25);
        }
        throw new AssertionError("Media state did not reach " + state);
    }
    private void onMain(final Runnable action) {
        final Throwable[] error = new Throwable[1];
        runOnMainSync(new Runnable() { @Override public void run() { try { action.run(); } catch (Throwable failure) { error[0] = failure; } } });
        if (error[0] != null) throw new AssertionError(error[0]);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
