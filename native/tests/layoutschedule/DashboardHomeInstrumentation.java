package com.jeremykenedy.firetv.ui.nativeapp.layoutschedule;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutOptions;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;

/** Run only on a disposable preview with no existing dashboard catalog. */
public final class DashboardHomeInstrumentation extends Instrumentation {
    private static final String ROOT = "com.jeremykenedy.firetv.ui.nativeapp.";
    private Activity home;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        File catalog = new File(getTargetContext().getFilesDir(), "layout-schedules/config.json");
        boolean fixture = false;
        try {
            check(!catalog.exists() && !new File(catalog + ".bak").exists(), "Requires disposable preview with no dashboard catalog");
            home = startActivitySync(new Intent().setClassName(getTargetContext(), ROOT + "MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            SystemClock.sleep(1500);
            final java.util.Map<String, ?> baseLayout = new java.util.HashMap<String, Object>(
                    getTargetContext().getSharedPreferences("native_layout", 0).getAll());
            Activity settings = open(home, "Settings", ROOT + "SettingsActivity");
            click(settings, "Home layout");
            Activity dashboard = open(settings, "Dashboards and schedules", ROOT + "layoutschedule.LayoutScheduleActivity");
            fixture = true;
            click(dashboard, "Save editor layout as dashboard");
            android.view.accessibility.AccessibilityNodeInfo edit = waitFocused("android.widget.EditText");
            Bundle text = new Bundle();
            text.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "ProofOne");
            check(edit.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, text), "Enter dashboard name");
            edit.recycle();
            SystemClock.sleep(700);
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            SystemClock.sleep(300);
            clickNode("SAVE");
            waitForIdleSync();
            LayoutScheduleStore store = new LayoutScheduleStore(getTargetContext());
            check(store.read().profiles.size() == 1, "Saving through dashboard screen failed");
            final String one = store.read().profiles.get(0).id;
            click(dashboard, "ProofOne");
            clickNode("Select manually");
            waitForIdleSync();
            check(store.read().manual.equals(one), "Manual selection through dashboard screen failed");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            waitWindow(settings);
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            waitWindow(home);
            LayoutOptions shown = new LayoutOptions(320, 180, Arrays.asList("widget:clock"), Collections.<String>emptyList(), LayoutOptions.Mode.WIDGETS_ONLY);
            LayoutOptions hidden = new LayoutOptions(320, 180, Arrays.asList("widget:clock"), Arrays.asList("widget:clock"), LayoutOptions.Mode.WIDGETS_ONLY);
            store.save("ProofOne", one, shown);
            String two = store.save("ProofTwo", null, hidden);
            store.rotation(Arrays.asList(one, two), 2, System.currentTimeMillis());
            store.mode(LayoutSchedule.Mode.ROTATION);
            // Returning to Home causes an immediate reload; the manual override must hold.
            Activity settingsAgain = open(home, "Settings", ROOT + "SettingsActivity");
            waitWindow(settingsAgain);
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            waitWindow(home);
            check(waitClock(true, 3000), "Manual dashboard did not render its clock");
            SystemClock.sleep(2500);
            check(clockVisible(), "Manual override did not hold across rotation boundary");
            store.resumeAutomatic();
            Activity settingsThird = open(home, "Settings", ROOT + "SettingsActivity");
            waitWindow(settingsThird);
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            waitWindow(home);
            check(waitClock(false, 5000), "Timed rotation did not hide clock");
            check(waitClock(true, 5000), "Timed rotation did not restore clock");
            check(baseLayout.equals(getTargetContext().getSharedPreferences("native_layout", 0).getAll()),
                    "Dashboard selection must preserve the base layout preferences");
            result.putBoolean("base_layout_preserved", true);
            result.putString("status", "PASS");
            result.putBoolean("route_save_and_select", true);
            result.putBoolean("manual_and_timed_home_render", true);
        } catch (Throwable failure) {
            result.putString("status", "FAIL"); result.putString("reason", failure.toString());
        } finally {
            if (home != null) runOnMainSync(new Runnable() { @Override public void run() { home.finish(); } });
            if (fixture) {
                boolean removed = (!catalog.exists() || catalog.delete()) && (!new File(catalog + ".bak").exists() || new File(catalog + ".bak").delete());
                result.putBoolean("fixture_catalog_removed", removed);
            }
        }
        finish(0, result);
    }
    private Activity open(Activity from, String label, String target) {
        ActivityMonitor monitor = addMonitor(target, null, false);
        click(from, label);
        Activity opened = waitForMonitorWithTimeout(monitor, 3000);
        removeMonitor(monitor);
        check(opened != null, "Could not open " + label);
        return opened;
    }
    private void click(final Activity activity, final String label) {
        runOnMainSync(new Runnable() { @Override public void run() {
            Button button = button(activity.getWindow().getDecorView(), label);
            if (button == null) throw new AssertionError("Missing button " + label);
            button.performClick();
        }});
        waitForIdleSync();
    }
    private android.view.accessibility.AccessibilityNodeInfo waitFocused(String className) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            android.view.accessibility.AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                android.view.accessibility.AccessibilityNodeInfo focus = root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT);
                root.recycle();
                if (focus != null) {
                    if (className.contentEquals(focus.getClassName())) return focus;
                    focus.recycle();
                }
            }
            SystemClock.sleep(40);
        }
        throw new AssertionError("Missing focused " + className);
    }
    private void clickNode(String text) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            android.view.accessibility.AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                java.util.List<android.view.accessibility.AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
                for (android.view.accessibility.AccessibilityNodeInfo node : nodes) {
                    if (node.getText() != null && text.equalsIgnoreCase(node.getText().toString()) && node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) {
                        for (android.view.accessibility.AccessibilityNodeInfo item : nodes) item.recycle();
                        root.recycle(); return;
                    }
                }
                for (android.view.accessibility.AccessibilityNodeInfo item : nodes) item.recycle();
                root.recycle();
            }
            SystemClock.sleep(40);
        }
        throw new AssertionError("Missing actionable " + text);
    }
    private void waitWindow(Activity activity) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (!activity.hasWindowFocus() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(40);
        check(activity.hasWindowFocus(), "Activity regains window focus: " + activity.getClass().getName());
    }
    private boolean waitClock(boolean visible, long timeout) {
        long end = SystemClock.uptimeMillis() + timeout;
        do { if (clockVisible() == visible) return true; SystemClock.sleep(100); } while (SystemClock.uptimeMillis() < end);
        return false;
    }
    private boolean clockVisible() {
        final boolean[] visible = {false};
        runOnMainSync(new Runnable() { @Override public void run() {
            View clock = home.getWindow().getDecorView().findViewWithTag("slot:widget:clock");
            visible[0] = clock != null && clock.isShown();
        }});
        return visible[0];
    }
    private static Button button(View view, String label) {
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
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
