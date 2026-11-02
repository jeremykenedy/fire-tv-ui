package com.jeremykenedy.firetv.ui.nativeapp;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.view.accessibility.AccessibilityNodeInfo;
import com.jeremykenedy.firetv.ui.nativeapp.stateguard.GuardStore;
import java.util.Arrays;
import java.util.LinkedHashSet;

/** Exercises the real Home, settings chooser and page navigation on a disposable preview. */
public final class MenuTransitionInstrumentation extends Instrumentation {
    private static final String KEY = "menu_transition_style";
    private static final String MAIN = "com.jeremykenedy.firetv.ui.nativeapp.MainActivity";
    private static final String SETTINGS = "com.jeremykenedy.firetv.ui.nativeapp.SettingsActivity";
    private static final String FOCUS = "com.jeremykenedy.firetv.ui.nativeapp.focus.FocusSettingsActivity";
    private Bundle arguments;
    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments = args; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); Activity main = null, settings = null, page = null;
        try {
            check("com.jeremykenedy.firetv.ui.preview".equals(getTargetContext().getPackageName()), "Preview package required.");
            String id = arguments == null ? null : arguments.getString("state_guard_case_id");
            String hash = arguments == null ? null : arguments.getString("state_guard_manifest_sha256");
            check(id != null && hash != null && new GuardStore(getTargetContext().getDataDir()).verify(id,
                    new LinkedHashSet<String>(Arrays.asList("shared_prefs", "files", "no_backup")), hash).matches,
                    "Verified durable state guard required before fixture writes.");
            result.putBoolean("state_guard_verified_before_write", true);
        } catch (Throwable failure) {
            result.putString("status", "FAIL"); result.putString("step", "state guard"); result.putString("reason", failure.toString());
            finish(0, result); return;
        }
        SharedPreferences prefs = getTargetContext().getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE);
        boolean existed = prefs.contains(KEY); Object old = prefs.getAll().get(KEY);
        String step = "guard";
        try {
            check(!existed || old instanceof String, "Use a fresh disposable target or an existing string value.");
            check(prefs.edit().putString(KEY, "default").commit(), "Explicit default fixture could not be saved.");
            step = "Home to Settings";
            main = startActivitySync(new Intent().setClassName(getTargetContext(), MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            awaitWindow(main); sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); waitForIdleSync();
            ActivityMonitor settingsMonitor = addMonitor(SETTINGS, null, false);
            focusAndSelect(main, "Settings");
            settings = waitForMonitorWithTimeout(settingsMonitor, 5000); removeMonitor(settingsMonitor);
            check(settings != null, "Settings did not open from Home."); awaitWindow(settings);
            check("default".equals(prefs.getAll().get(KEY)), "Opening Settings changed explicit default.");
            String[] values = {"none", "fade", "slide", "default"};
            int[] choices = {1, 2, 3, 0};
            for (int i = 0; i < values.length; i++) {
                step = "choose " + values[i];
                focusAndSelect(settings, "Settings transitions:");
                awaitChooser();
                for (int j = 0; j < 4; j++) sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);
                for (int j = 0; j < choices[i]; j++) sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
                sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER); waitForIdleSync();
                check(choices[i] == 0 ? !prefs.contains(KEY) : values[i].equals(prefs.getAll().get(KEY)),
                        "Chooser did not persist " + values[i]);
                step = "entry and Back " + values[i];
                Log.i("MenuTransitionProof", "before-entry " + values[i]); SystemClock.sleep(600);
                ActivityMonitor focusMonitor = addMonitor(FOCUS, null, false);
                focusAndSelect(settings, "Icons and focus");
                page = waitForMonitorWithTimeout(focusMonitor, 5000); removeMonitor(focusMonitor);
                check(page != null, "Focus page did not open."); awaitWindow(page);
                Log.i("MenuTransitionProof", "after-entry " + values[i]); SystemClock.sleep(600);
                Log.i("MenuTransitionProof", "before-back " + values[i]); sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
                awaitWindow(settings); page = null;
                Log.i("MenuTransitionProof", "after-back " + values[i]); SystemClock.sleep(600);
                check(choices[i] == 0 ? !prefs.contains(KEY) : values[i].equals(prefs.getAll().get(KEY)),
                        "Page round trip changed " + values[i]);
            }
            result.putString("status", "PASS"); result.putBoolean("chooser_and_home_settings_route", true);
            result.putBoolean("all_choices_retain_after_page_back", true); result.putBoolean("explicit_default_preserved_on_open", true);
            result.putString("limits", "Functional navigation and preferences only. Inspect screen recording for visible motion and direction.");
        } catch (Throwable failure) { result.putString("status", "FAIL"); result.putString("step", step); result.putString("reason", failure.toString()); }
        finally {
            try {
                if (page != null) finishActivity(page);
                if (settings != null) finishActivity(settings);
                if (main != null) finishActivity(main);
                waitForIdleSync();
            } catch (Throwable failure) { result.putString("activity_cleanup_error", failure.toString()); }
            try {
                SharedPreferences.Editor edit = prefs.edit();
                if (existed && old instanceof String) edit.putString(KEY, (String) old); else if (!existed) edit.remove(KEY);
                boolean restored = edit.commit() && prefs.contains(KEY) == existed
                        && (!existed || old.equals(prefs.getAll().get(KEY)));
                result.putBoolean("transition_preference_restored", restored);
            } catch (Throwable failure) { result.putBoolean("transition_preference_restored", false); result.putString("restore_error", failure.toString()); }
            if (!result.getBoolean("transition_preference_restored") || result.containsKey("activity_cleanup_error")) result.putString("status", "FAIL");
        }
        finish(0, result);
    }
    private void awaitChooser() {
        long until = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < until) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                boolean ready = !root.findAccessibilityNodeInfosByText("Short slide").isEmpty()
                        && !root.findAccessibilityNodeInfosByText("Settings transitions").isEmpty();
                root.recycle();
                if (ready) { Log.i("MenuTransitionProof", "chooser-window-ready"); return; }
            }
            SystemClock.sleep(50);
        }
        throw new AssertionError("Transition chooser window did not settle.");
    }
    private void focusAndSelect(final Activity activity, final String text) {
        final boolean[] found = {false};
        onMain(new Runnable() { @Override public void run() {
            Button button = find(activity.getWindow().getDecorView(), text);
            found[0] = button != null && button.isAttachedToWindow() && button.hasWindowFocus() && button.requestFocus();
        } });
        check(found[0], "Could not focus " + text); sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER); waitForIdleSync();
    }
    private void awaitWindow(final Activity activity) {
        long until = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < until) {
            final boolean[] ready = {false};
            onMain(new Runnable() { @Override public void run() { ready[0] = activity.getWindow().getDecorView().hasWindowFocus(); } });
            if (ready[0]) return; SystemClock.sleep(50);
        }
        throw new AssertionError("Window did not gain focus.");
    }
    private void finishActivity(final Activity activity) { onMain(new Runnable() { @Override public void run() { activity.finish(); } }); }
    private void onMain(final Runnable action) {
        final Throwable[] failure = {null};
        runOnMainSync(new Runnable() { @Override public void run() { try { action.run(); } catch (Throwable error) { failure[0] = error; } } });
        if (failure[0] != null) throw new AssertionError("Main-thread action failed", failure[0]);
    }
    private static Button find(View view, String text) {
        if (view instanceof Button && ((Button) view).getText().toString().startsWith(text)) return (Button) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            Button found = find(((ViewGroup) view).getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
