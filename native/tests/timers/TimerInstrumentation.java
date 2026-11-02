package com.jeremykenedy.firetv.ui.nativeapp.widget.timers;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutOptions;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.stateguard.GuardStore;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public final class TimerInstrumentation extends Instrumentation {
    private Activity home;
    private Bundle arguments;
    private static final String MAIN = "com.jeremykenedy.firetv.ui.nativeapp.MainActivity";
    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments = args == null ? new Bundle() : new Bundle(args); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences timerPrefs = null;
        SharedPreferences layoutPrefs = null;
        Map<String, ?> timersBefore = null;
        Map<String, ?> layoutBefore = null;
        boolean fixtureStarted = false;
        try {
            requireDurableGuard(result);
            timerPrefs = getTargetContext().getSharedPreferences(TimerPreferences.FILE, Context.MODE_PRIVATE);
            layoutPrefs = getTargetContext().getSharedPreferences("native_layout", Context.MODE_PRIVATE);
            timersBefore = timerPrefs.getAll();
            layoutBefore = layoutPrefs.getAll();
            AccessibilityServiceInfo info = automation().getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            automation().setServiceInfo(info);
            fixtureStarted = true;
            check(timerPrefs.edit().clear().commit(), "Temporary timer fixture clear.");
            LayoutPreferences.save(getTargetContext(), new LayoutOptions(320, 180,
                    Arrays.asList("widget:countdown", "widget:timer"), Collections.<String>emptyList(), LayoutOptions.Mode.WIDGETS_ONLY));
            launchHome();
            settle();
            select("countdown");
            fill("Label", "Timezone proof");
            fill("Date: YYYY-MM-DD", "2099-11-01");
            fill("24-hour time: HH:MM", "01:30");
            fill("Time zone: America/Los_Angeles or UTC", "America/Los_Angeles");
            click("Save");
            check(TimerPreferences.countdown(getTargetContext()).label.equals("Timezone proof"), "Native countdown form saves a label and zone.");
            check(findView("local:countdown") != null, "Home replaces setup with the actual countdown view after dialog dismissal.");

            select("timer");
            fill("Label", "Tea proof");
            fill("Duration in seconds: 1 through 604800", "60");
            click("Save timer");
            check(!TimerPreferences.timer(getTargetContext()).sound, "Native creation defaults completion sound to off.");
            check(findView("local:timer") != null, "Home replaces empty state with actual timer.");
            select("timer"); click("Start / Resume");
            TimerState running = TimerPreferences.timer(getTargetContext());
            check(running.running, "Native action starts timer.");
            long deadline = running.elapsedDeadline;
            SystemClock.sleep(1200);
            recreateHome();
            settle();
            check(TimerPreferences.timer(getTargetContext()).elapsedDeadline == deadline, "Saved deadline survives native Activity recreation.");
            select("timer"); click("Pause");
            TimerState paused = TimerPreferences.timer(getTargetContext());
            check(!paused.running && paused.pausedRemaining < 60000 && paused.pausedRemaining > 0, "Pause retains elapsed progress.");
            long remaining = paused.pausedRemaining;
            SystemClock.sleep(1100);
            check(TimerPreferences.timer(getTargetContext()).pausedRemaining == remaining, "Paused persisted value stays fixed.");
            select("timer"); click("Start / Resume");
            check(TimerPreferences.timer(getTargetContext()).running, "Resume restores running state.");
            select("timer"); click("Reset");
            check(!TimerPreferences.timer(getTargetContext()).running && TimerPreferences.timer(getTargetContext()).pausedRemaining == 60000,
                    "Reset restores configured duration.");
            select("timer"); click("Cancel timer"); click("Cancel timer");
            check(TimerPreferences.timer(getTargetContext()) == null, "Cancel removes only the timer.");
            check(TimerPreferences.countdown(getTargetContext()) != null, "Cancel preserves independent countdown.");
            result.putString("result", "PASS: native Home setup, explicit-zone countdown, quiet timer, start/pause/resume/reset/cancel and Activity persistence");
        } catch (Throwable failure) { result.putString("error", android.util.Log.getStackTraceString(failure)); }
        finally {
            try {
                if (fixtureStarted) {
                    sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
                    restore(timerPrefs, timersBefore);
                    restore(layoutPrefs, layoutBefore);
                    check(timerPrefs.getAll().equals(timersBefore) && layoutPrefs.getAll().equals(layoutBefore), "Exact timer and layout restoration.");
                    result.putString("restored", "true");
                } else result.putString("fixture_started", "false");
            } catch (Throwable failure) { result.putString("restoration_error", android.util.Log.getStackTraceString(failure)); }
        }
        finish(result.containsKey("error") || result.containsKey("restoration_error") ? Activity.RESULT_CANCELED : Activity.RESULT_OK, result);
    }

    private void requireDurableGuard(Bundle result) throws Exception {
        String id = arguments.getString("state_guard_case_id");
        String hash = arguments.getString("state_guard_manifest_sha256");
        check(id != null && hash != null, "Operator StateGuard case and manifest hash are required before timer fixtures.");
        java.io.File active = new java.io.File(getTargetContext().getDataDir(), "no_backup/native-test-state-guard-v1/active");
        check(new java.io.File(active, "manifest.bin").isFile() && !new java.io.File(active, "restore.pending").exists(),
                "A completed operator StateGuard capture with no pending restore is required.");
        GuardStore.Result guard = new GuardStore(getTargetContext().getDataDir()).verify(id,
                new java.util.LinkedHashSet<String>(Arrays.asList("shared_prefs", "files", "no_backup")), hash);
        check(guard.matches, "Current private state must exactly match the operator's durable guard before fixture writes.");
        result.putString("state_guard_case_id", guard.caseId);
        result.putString("state_guard_manifest_sha256", guard.hash);
        result.putString("state_guard_preflight", "verified before fixture writes; operator owns final restore/verify/resolve");
    }

    private void launchHome() {
        ActivityMonitor monitor = addMonitor(MAIN, null, false);
        try {
            getTargetContext().startActivity(new Intent().setClassName(getTargetContext(), MAIN)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            home = waitForMonitorWithTimeout(monitor, 5000);
            check(home != null, "New Home Activity must be observed within five seconds.");
            awaitHomeWindow();
        } finally { removeMonitor(monitor); }
    }

    private void recreateHome() {
        final Activity previous = home;
        ActivityMonitor monitor = addMonitor(MAIN, null, false);
        try {
            onMain(new Runnable() { @Override public void run() { previous.recreate(); } });
            Activity recreated = waitForMonitorWithTimeout(monitor, 5000);
            check(recreated != null && recreated != previous, "A distinct recreated Home Activity must be observed within five seconds.");
            home = recreated;
            awaitHomeWindow();
        } finally { removeMonitor(monitor); }
    }

    private void awaitHomeWindow() {
        long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            final boolean[] focused = new boolean[1];
            onMain(new Runnable() { @Override public void run() { focused[0] = home.hasWindowFocus() && !home.isFinishing(); } });
            if (focused[0]) return;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Home window must reach the foreground within five seconds.");
    }

    private void select(final String id) {
        onMain(new Runnable() { @Override public void run() {
            View slot = home.getWindow().getDecorView().findViewWithTag("slot:widget:" + id);
            check(slot != null, "Actual Home widget slot exists.");
            slot.requestFocus(); slot.performClick();
        } });
        settle();
    }

    private View findView(final String tag) {
        final View[] found = new View[1];
        onMain(new Runnable() { @Override public void run() { found[0] = home.getWindow().getDecorView().findViewWithTag(tag); } });
        return found[0];
    }

    private void fill(String description, String text) {
        AccessibilityNodeInfo node = awaitNode(description, true);
        node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
        settle();
        node = awaitNode(description, true);
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        check(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args), "Native text entry works.");
        settle();
        node = awaitNode(description, true);
        check(text.equals(String.valueOf(node.getText())), "Native input preserves entered value: " + description);
    }

    private void click(String label) {
        if (label.equals("Save") || label.equals("Save timer")) {
            dismissKeyboardIfVisible();
            awaitNode("Label", true);
        }
        AccessibilityNodeInfo node = awaitNode(label, false);
        while (node != null && !node.isClickable()) node = node.getParent();
        check(node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Native action clicks: " + label);
        settle();
    }

    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, String text, boolean description) {
        if (node == null) return null;
        if (description ? node.isEditable() && text.equals(String.valueOf(node.getContentDescription()))
                : node.isVisibleToUser() && text.equalsIgnoreCase(String.valueOf(node.getText()))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = find(node.getChild(i), text, description);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo awaitNode(String text, boolean description) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            for (AccessibilityWindowInfo window : automation().getWindows()) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root != null && window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION
                        && getTargetContext().getPackageName().equals(String.valueOf(root.getPackageName()))) {
                    AccessibilityNodeInfo found = find(root, text, description);
                    if (found != null) return found;
                }
            }
            AccessibilityNodeInfo root = automation().getRootInActiveWindow();
            if (root != null && getTargetContext().getPackageName().equals(String.valueOf(root.getPackageName()))) {
                AccessibilityNodeInfo found = find(root, text, description);
                if (found != null) return found;
            }
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Native target-package dialog control: " + text);
    }

    private UiAutomation automation() { return getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES); }

    private boolean keyboardVisible() {
        for (AccessibilityWindowInfo window : automation().getWindows()) {
            AccessibilityNodeInfo root = window.getRoot();
            if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD && root != null && root.isVisibleToUser()) return true;
        }
        return false;
    }

    private void dismissKeyboardIfVisible() {
        if (!keyboardVisible()) return;
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        long deadline = SystemClock.uptimeMillis() + 3000;
        while (keyboardVisible() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100);
        check(!keyboardVisible(), "Back dismisses only the visible native keyboard before Save.");
    }

    private void onMain(final Runnable action) {
        final Throwable[] error = new Throwable[1];
        runOnMainSync(new Runnable() { @Override public void run() { try { action.run(); } catch (Throwable failure) { error[0] = failure; } } });
        if (error[0] != null) throw new AssertionError(error[0]);
        waitForIdleSync();
    }
    private void settle() { waitForIdleSync(); SystemClock.sleep(250); }
    @SuppressWarnings("unchecked") private static void restore(SharedPreferences prefs, Map<String, ?> values) {
        SharedPreferences.Editor editor = prefs.edit().clear();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            Object value = entry.getValue(); String key = entry.getKey();
            if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
            else throw new AssertionError("Unexpected preference type.");
        }
        check(editor.commit(), "Preference snapshot restored.");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
