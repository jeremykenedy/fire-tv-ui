package com.jeremykenedy.firetv.ui.nativeapp.statusbar;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.view.accessibility.AccessibilityNodeInfo;
import com.jeremykenedy.firetv.ui.nativeapp.stateguard.GuardStore;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;

/** Actual Home route proof. Requires the operator's durable guard and a disposable preview. */
public final class StatusBarHomeInstrumentation extends Instrumentation {
    private Bundle arguments;
    private Activity home;
    private static final String MAIN = "com.jeremykenedy.firetv.ui.nativeapp.MainActivity";
    private static final String SETTINGS = "com.jeremykenedy.firetv.ui.nativeapp.SettingsActivity";
    private static final String[] KEYS = {"minimal_status_bar", "hide_status_bar_unless_focused", "status_bar_position", "tinted_status_bar"};
    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments = args == null ? new Bundle() : new Bundle(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences prefs = null;
        Map<String, ?> before = null;
        boolean fixture = false;
        try {
            String id = arguments.getString("state_guard_case_id"), hash = arguments.getString("state_guard_manifest_sha256");
            check(id != null && hash != null, "Durable StateGuard case and hash required");
            File active = new File(getTargetContext().getDataDir(), "no_backup/native-test-state-guard-v1/active");
            check(new File(active, "manifest.bin").isFile() && !new File(active, "restore.pending").exists(), "Guard not ready");
            GuardStore.Result guard = new GuardStore(getTargetContext().getDataDir()).verify(id,
                    new LinkedHashSet<String>(Arrays.asList("shared_prefs", "files", "no_backup")), hash);
            check(guard.matches, "Guard must match before fixture changes");
            prefs = getTargetContext().getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE);
            before = prefs.getAll();
            for (String key : KEYS) check(!before.containsKey(key), "Absent-key default proof needs a fresh preview");
            ActivityMonitor defaultMonitor = addMonitor(MAIN, null, false);
            getTargetContext().startActivity(new Intent().setClassName(getTargetContext(), MAIN)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            home = waitForMonitorWithTimeout(defaultMonitor, 5000);
            removeMonitor(defaultMonitor);
            check(home != null, "Default Home did not open");
            waitHome();
            runOnMainSync(new Runnable() { @Override public void run() {
                View container = home.getWindow().getDecorView().findViewWithTag("home:statusbar-container");
                check(container != null && container.getVisibility() == View.GONE
                        && home.findViewById(0x1001) != null && home.findViewById(0x1002) != null,
                        "Absent legacy keys must retain the original Home header");
                home.finish();
            }});
            long destroyDeadline = SystemClock.uptimeMillis() + 5000;
            while (!home.isDestroyed() && SystemClock.uptimeMillis() < destroyDeadline) SystemClock.sleep(40);
            check(home.isDestroyed(), "Default Home must finish before reopening");
            home = null;
            result.putBoolean("absent_keys_keep_default_header", true);
            fixture = true;
            check(prefs.edit().putBoolean("minimal_status_bar", true).putBoolean("hide_status_bar_unless_focused", true)
                    .putString("status_bar_position", "Right").putBoolean("tinted_status_bar", true).commit(), "Could not save fixture");
            ActivityMonitor monitor = addMonitor(MAIN, null, false);
            getTargetContext().startActivity(new Intent().setClassName(getTargetContext(), MAIN)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            home = waitForMonitorWithTimeout(monitor, 5000);
            removeMonitor(monitor);
            check(home != null, "Home did not open");
            waitHome();
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            waitForIdleSync();
            boolean settingsFocused = false;
            long settingsDeadline = SystemClock.uptimeMillis() + 5000;
            while (!settingsFocused && SystemClock.uptimeMillis() < settingsDeadline) {
                final boolean[] accepted = {false};
                runOnMainSync(new Runnable() { @Override public void run() {
                    View settings = home.findViewById(0x1001);
                    accepted[0] = settings != null && settings.requestFocus();
                }});
                settingsFocused = accepted[0];
                if (!settingsFocused) SystemClock.sleep(40);
            }
            if (!settingsFocused) {
                final String[] diagnostic = {""};
                runOnMainSync(new Runnable() { @Override public void run() {
                    View target = home.findViewById(0x1001), focus = home.getCurrentFocus();
                    diagnostic[0] = "target=" + (target == null ? "null" : target.getClass().getName()
                            + ", shown=" + target.isShown() + ", enabled=" + target.isEnabled()
                            + ", focusable=" + target.isFocusable() + ", touch=" + target.isInTouchMode()
                            + ", window=" + home.hasWindowFocus())
                            + ", current=" + (focus == null ? "null" : focus.getClass().getName() + "/" + focus.getId());
                }});
                result.putString("focus_diagnostic", diagnostic[0]);
            }
            check(settingsFocused, "Settings must accept focus");
            waitForIdleSync();
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);
            waitForIdleSync();
            final boolean[] minimalUp = {false};
            runOnMainSync(new Runnable() { @Override public void run() {
                View row = home.getWindow().getDecorView().findViewWithTag("statusbar");
                View clock = home.getWindow().getDecorView().findViewWithTag("statusbar:clock");
                minimalUp[0] = row != null && row.getAlpha() == 1f && clock != null && clock.isFocused();
            }});
            check(minimalUp[0], "Up from actual Home must reveal and focus minimal clock");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            waitForIdleSync();
            final boolean[] minimalDown = {false};
            runOnMainSync(new Runnable() { @Override public void run() {
                View row = home.getWindow().getDecorView().findViewWithTag("statusbar");
                minimalDown[0] = row != null && row.getAlpha() == 0f && home.getCurrentFocus() != null && !row.hasFocus();
            }});
            check(minimalDown[0], "Down from actual minimal bar must hide it and focus below");
            final View[] views = new View[2];
            runOnMainSync(new Runnable() { @Override public void run() {
                views[0] = home.getWindow().getDecorView().findViewWithTag("statusbar:clock");
                views[1] = home.findViewById(0x1001);
                check(views[0] != null && views[1] != null && home.findViewById(0x1002) != null,
                        "Clock and normal Settings/Help routes must remain available");
                View row = home.getWindow().getDecorView().findViewWithTag("statusbar");
                check(row != null && row.requestFocus(), "Hidden status bar must remain reachable");
            }});
            waitForIdleSync();
            check(views[0].isFocused(), "Minimal clock did not receive focus");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            waitForIdleSync();
            for (String label : new String[]{"Network", "Settings", "Preferences", "Sound", "Accessibility", "Display", "Factory Home", "Screensavers"}) {
                long panelDeadline = SystemClock.uptimeMillis() + 5000;
                AccessibilityNodeInfo item = null;
                while (item == null && SystemClock.uptimeMillis() < panelDeadline) {
                    item = findNode(label);
                    if (item == null) SystemClock.sleep(40);
                }
                check(item != null, "Quick panel missing " + label);
                item.recycle();
            }
            ActivityMonitor screensaverMonitor = addMonitor("com.jeremykenedy.firetv.ui.nativeapp.device.screensavers.ScreensaverSettingsActivity", null, false);
            for (int i = 0; i < 7; i++) sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            Activity screensavers = waitForMonitorWithTimeout(screensaverMonitor, 5000);
            removeMonitor(screensaverMonitor);
            check(screensavers != null, "Quick panel Screensavers did not open launcher screensaver settings");
            waitWindow(screensavers);
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            waitWindow(home);
            runOnMainSync(new Runnable() { @Override public void run() {
                View row = home.getWindow().getDecorView().findViewWithTag("statusbar");
                check(row != null && row.requestFocus(), "Status bar did not regain focus");
            }});
            waitForIdleSync();
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            long reopenDeadline = SystemClock.uptimeMillis() + 5000;
            while (findNode("Preferences") == null && SystemClock.uptimeMillis() < reopenDeadline) SystemClock.sleep(40);
            check(findNode("Preferences") != null, "Quick panel did not reopen");
            result.putBoolean("screensaver_route_and_back", true);
            ActivityMonitor settings = addMonitor(SETTINGS, null, false);
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            Activity opened = waitForMonitorWithTimeout(settings, 5000);
            removeMonitor(settings);
            check(opened != null, "Quick panel Preferences did not open launcher settings");
            clickButton(opened, "Home layout");
            ActivityMonitor layout = addMonitor("com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutSettingsActivity", null, false);
            clickButton(opened, "Your cards and widgets");
            final Activity editor = waitForMonitorWithTimeout(layout, 5000);
            removeMonitor(layout);
            check(editor != null, "Home layout editor did not open");
            clickButton(editor, "Status bar");
            clickNode("Status bar: Minimal");
            clickNode("Full");
            check(!prefs.getBoolean("minimal_status_bar", true), "Status bar Full choice did not persist");
            clickNode("Status bar: Full");
            clickNode("Off");
            check(!prefs.contains("minimal_status_bar"), "Off must remove the explicit mode key");
            clickNode("Status bar: Off");
            clickNode("Full");
            clickNode("Hide unless focused: On");
            clickNode("Default");
            check(!prefs.contains("hide_status_bar_unless_focused"), "Default must remove the explicit hide key");
            clickNode("Hide unless focused: Default");
            clickNode("On");
            result.putBoolean("off_and_default_labels_and_choices", true);
            clickNode("DONE");
            waitWindow(editor);
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            waitWindow(opened);
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            waitForIdleSync();
            long deadline = SystemClock.uptimeMillis() + 5000;
            while (!home.hasWindowFocus() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(40);
            check(home.hasWindowFocus() && !home.isFinishing(), "Back did not return to Home");
            runOnMainSync(new Runnable() { @Override public void run() { check(home.findViewById(0x1001).requestFocus(), "Settings focus missing"); }});
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);
            waitForIdleSync();
            final boolean[] fullUp = {false};
            runOnMainSync(new Runnable() { @Override public void run() {
                View row = home.getWindow().getDecorView().findViewWithTag("statusbar");
                View network = home.getWindow().getDecorView().findViewWithTag("statusbar:network");
                fullUp[0] = row != null && row.getAlpha() == 1f && network != null && network.isFocused();
            }});
            check(fullUp[0], "Up from actual Home must reveal and focus full bar");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            waitForIdleSync();
            final boolean[] fullDown = {false};
            runOnMainSync(new Runnable() { @Override public void run() {
                View row = home.getWindow().getDecorView().findViewWithTag("statusbar");
                fullDown[0] = row != null && row.getAlpha() == 0f && home.getCurrentFocus() != null && !row.hasFocus();
            }});
            check(fullDown[0], "Down from actual full bar must hide it and focus below");
            result.putBoolean("actual_full_and_minimal_up_down", true);
            result.putBoolean("quick_panel_destinations_visible", true);
            result.putBoolean("preferences_route_and_back", true);
            result.putBoolean("status_bar_settings_route_and_save", true);
            result.putString("status", "PASS");
        } catch (Throwable failure) {
            result.putString("status", "FAIL"); result.putString("reason", android.util.Log.getStackTraceString(failure));
        } finally {
            if (home != null) { final Activity open = home; runOnMainSync(new Runnable() { @Override public void run() { open.finish(); } }); }
            if (fixture && prefs != null && before != null) {
                SharedPreferences.Editor restore = prefs.edit();
                for (String key : KEYS) {
                    Object old = before.get(key);
                    if (!before.containsKey(key)) restore.remove(key);
                    else if (old instanceof Boolean) restore.putBoolean(key, (Boolean) old);
                    else if (old instanceof String) restore.putString(key, (String) old);
                    else result.putString("restoration_error", "Unexpected fixture key type: " + key);
                }
                boolean restored = restore.commit();
                Map<String, ?> after = prefs.getAll();
                for (String key : KEYS) restored &= before.containsKey(key) == after.containsKey(key)
                        && (!before.containsKey(key) || java.util.Objects.equals(before.get(key), after.get(key)));
                result.putBoolean("fixture_keys_restored", restored);
            }
        }
        finish(0, result);
    }
    private void waitWindow(Activity activity) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (!activity.hasWindowFocus() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(40);
        check(activity.hasWindowFocus(), "Window did not regain focus: " + activity.getClass().getName());
    }
    private void waitHome() {
        for (int i = 0; i < 50; i++) {
            final boolean[] ready = {false};
            runOnMainSync(new Runnable() { @Override public void run() {
                ready[0] = home.hasWindowFocus() && home.findViewById(0x1001) != null;
            }});
            if (ready[0]) return;
            SystemClock.sleep(100);
        }
        throw new AssertionError("Home window did not become ready");
    }
    private void clickButton(final Activity activity, final String label) {
        runOnMainSync(new Runnable() { @Override public void run() {
            Button button = button(activity.getWindow().getDecorView(), label);
            check(button != null && button.performClick(), "Could not open " + label);
        }});
        waitForIdleSync();
    }
    private Button button(View view, String label) {
        if (view instanceof Button && label.contentEquals(((Button) view).getText())) return (Button) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = button(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }
    private void clickNode(String text) {
        AccessibilityNodeInfo node = null;
        for (int i = 0; i < 30 && node == null; i++) { node = findNode(text); if (node == null) SystemClock.sleep(100); }
        while (node != null && !node.isClickable()) node = node.getParent();
        check(node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Could not select " + text);
        waitForIdleSync();
    }
    private AccessibilityNodeInfo findNode(String text) {
        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
        if (root == null) return null;
        for (AccessibilityNodeInfo found : root.findAccessibilityNodeInfosByText(text))
            if (text.contentEquals(found.getText())) return found;
        return null;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
