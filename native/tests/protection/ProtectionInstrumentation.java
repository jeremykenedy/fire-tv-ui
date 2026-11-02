package com.jeremykenedy.firetv.ui.nativeapp.device.protection;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.view.accessibility.AccessibilityNodeInfo;

/** Routed Home-only opt-in and cancellation checks; never changes a system sleep setting. */
public final class ProtectionInstrumentation extends Instrumentation {
    private static final String ROOT = "com.jeremykenedy.firetv.ui.nativeapp.";
    private Activity home, settings, protection;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences prefs = getTargetContext().getSharedPreferences("native_device_protection", 0);
        final boolean existed = prefs.contains("keep_launcher_awake");
        final boolean before = prefs.getBoolean("keep_launcher_awake", false);
        String sleepBefore = Settings.Secure.getString(getTargetContext().getContentResolver(), "sleep_timeout");
        try {
            getUiAutomation();
            check(prefs.edit().remove("keep_launcher_awake").commit(), "Fixture preference must save");
            home = startActivitySync(new Intent().setClassName(getTargetContext(), ROOT + "MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            settle();
            check(!hasKeepScreenOn(home.getWindow().getDecorView()), "Default must follow system behavior");
            openSettings();
            openProtection();
            check(!prefs.contains("keep_launcher_awake"), "Opening protection must preserve absent choice");
            focus(protection, "protection:awake");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            settle();
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            settle();
            check(!prefs.contains("keep_launcher_awake"), "Cancel must preserve absent choice");
            focus(protection, "protection:awake");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            settle();
            AccessibilityNodeInfo confirm = awaitConfirmation();
            check(confirm.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Explicit confirmation must be clickable");
            confirm.recycle();
            settle();
            check(prefs.getBoolean("keep_launcher_awake", false), "Explicit enable must persist");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            settle();
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            settle();
            check(hasKeepScreenOn(home.getWindow().getDecorView()), "Visible Home must request keep-screen-on after opt-in");
            openSettings();
            check(!hasKeepScreenOn(home.getWindow().getDecorView()), "Leaving Home must release its request");
            openProtection();
            focus(protection, "protection:awake");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            settle();
            check(!prefs.getBoolean("keep_launcher_awake", true), "Off must persist without another confirmation");
            String sleepAfter = Settings.Secure.getString(getTargetContext().getContentResolver(), "sleep_timeout");
            check(sleepBefore == null ? sleepAfter == null : sleepBefore.equals(sleepAfter), "Opt-in/cancel must not alter system sleep timeout");
            result.putString("status", "PASS");
            result.putBoolean("explicit_opt_in_cancel_and_release", true);
            result.putBoolean("system_sleep_timeout_preserved", true);
            result.putString("limits", "No system timeout write, actual sleep/screensaver timing or physical TV validation.");
        } catch (Throwable failed) {
            result.putString("status", "FAIL"); result.putString("reason", failed.toString());
        } finally {
            SharedPreferences.Editor restore = prefs.edit();
            if (existed) restore.putBoolean("keep_launcher_awake", before); else restore.remove("keep_launcher_awake");
            boolean restored = restore.commit() && prefs.contains("keep_launcher_awake") == existed
                    && (!existed || prefs.getBoolean("keep_launcher_awake", !before) == before);
            result.putBoolean("preference_restored", restored);
            if (!restored) { result.putString("status", "FAIL"); result.putString("reason", "Preference restoration failed"); }
            if (protection != null) main(new Runnable() { @Override public void run() { protection.finish(); } });
            if (settings != null) main(new Runnable() { @Override public void run() { settings.finish(); } });
            if (home != null) main(new Runnable() { @Override public void run() { home.recreate(); } });
        }
        finish(0, result);
    }
    private AccessibilityNodeInfo awaitConfirmation() {
        long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                AccessibilityNodeInfo found = confirmation(root);
                root.recycle();
                if (found != null) return found;
            }
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Visible confirmation button unavailable after waiting for accessibility window");
    }
    private AccessibilityNodeInfo confirmation(AccessibilityNodeInfo node) {
        CharSequence text = node.getText();
        if (text != null && "Enable on Home".equalsIgnoreCase(text.toString()) && node.isClickable())
            return AccessibilityNodeInfo.obtain(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            AccessibilityNodeInfo result = confirmation(child);
            child.recycle();
            if (result != null) return result;
        }
        return null;
    }
    private void openSettings() {
        ActivityMonitor monitor = addMonitor(ROOT + "SettingsActivity", null, false);
        main(new Runnable() { @Override public void run() {
            Button opener = button(home.getWindow().getDecorView(), "Settings");
            check(opener != null, "Main Settings route"); opener.requestFocus();
        }});
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        settings = waitForMonitorWithTimeout(monitor, 3000); removeMonitor(monitor);
        check(settings != null, "Settings route must open"); settle();
        main(new Runnable() { @Override public void run() {
            Button device = button(settings.getWindow().getDecorView(), "Device");
            check(device != null && device.requestFocus(), "Device category must accept remote focus");
        }});
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        settle();
    }
    private void openProtection() {
        ActivityMonitor monitor = addMonitor(ROOT + "device.protection.ProtectionActivity", null, false);
        main(new Runnable() { @Override public void run() {
            Button opener = button(settings.getWindow().getDecorView(), "Sleep and protection");
            check(opener != null, "Protection route"); opener.requestFocus();
        }});
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        protection = waitForMonitorWithTimeout(monitor, 3000); removeMonitor(monitor);
        check(protection != null, "Protection must open"); settle();
    }
    private void focus(final Activity activity, final String tag) {
        main(new Runnable() { @Override public void run() { check(activity.getWindow().getDecorView().findViewWithTag(tag).requestFocus(), "Focusable control"); }});
    }
    private void main(final Runnable action) {
        final Throwable[] error = {null};
        runOnMainSync(new Runnable() { @Override public void run() { try { action.run(); } catch (Throwable failed) { error[0] = failed; } }});
        if (error[0] != null) throw new AssertionError(error[0]);
    }
    private void settle() { waitForIdleSync(); SystemClock.sleep(120); waitForIdleSync(); }
    private static boolean hasKeepScreenOn(View view) {
        if (view.getKeepScreenOn()) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                if (hasKeepScreenOn(group.getChildAt(i))) return true;
        }
        return false;
    }
    private static Button button(View view, String label) {
        if (view instanceof Button && label.contentEquals(((Button)view).getText())) return (Button)view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) { Button result = button(group.getChildAt(i), label); if (result != null) return result; }
        }
        return null;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
