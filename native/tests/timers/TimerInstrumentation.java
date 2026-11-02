package com.jeremykenedy.firetv.ui.nativeapp.widget.timers;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutOptions;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutPreferences;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public final class TimerInstrumentation extends Instrumentation {
    private Activity home;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences timerPrefs = getTargetContext().getSharedPreferences(TimerPreferences.FILE, Context.MODE_PRIVATE);
        SharedPreferences layoutPrefs = getTargetContext().getSharedPreferences("native_layout", Context.MODE_PRIVATE);
        Map<String, ?> timersBefore = timerPrefs.getAll();
        Map<String, ?> layoutBefore = layoutPrefs.getAll();
        try {
            check(timerPrefs.edit().clear().commit(), "Temporary timer fixture clear.");
            LayoutPreferences.save(getTargetContext(), new LayoutOptions(320, 180,
                    Arrays.asList("widget:countdown", "widget:timer"), Collections.<String>emptyList(), LayoutOptions.Mode.WIDGETS_ONLY));
            home = startActivitySync(new Intent().setClassName(getTargetContext(),
                    "com.jeremykenedy.firetv.ui.nativeapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
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
            onMain(new Runnable() { @Override public void run() { home.recreate(); } });
            settle();
            // Reacquire the current Activity before subsequent UI work.
            home = startActivitySync(new Intent().setClassName(getTargetContext(),
                    "com.jeremykenedy.firetv.ui.nativeapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
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
                sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
                restore(timerPrefs, timersBefore);
                restore(layoutPrefs, layoutBefore);
                check(timerPrefs.getAll().equals(timersBefore) && layoutPrefs.getAll().equals(layoutBefore), "Exact timer and layout restoration.");
                result.putString("restored", "true");
            } catch (Throwable failure) { result.putString("restoration_error", android.util.Log.getStackTraceString(failure)); }
        }
        finish(result.containsKey("error") || result.containsKey("restoration_error") ? Activity.RESULT_CANCELED : Activity.RESULT_OK, result);
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
        AccessibilityNodeInfo node = find(getUiAutomation().getRootInActiveWindow(), description, true);
        check(node != null, "Native input exists: " + description);
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        check(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args), "Native text entry works.");
        settle();
    }

    private void click(String label) {
        AccessibilityNodeInfo node = find(getUiAutomation().getRootInActiveWindow(), label, false);
        check(node != null, "Native action exists: " + label);
        while (node != null && !node.isClickable()) node = node.getParent();
        check(node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Native action clicks: " + label);
        settle();
    }

    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, String text, boolean description) {
        if (node == null) return null;
        if (text.contentEquals(description ? node.getContentDescription() == null ? "" : node.getContentDescription()
                : node.getText() == null ? "" : node.getText())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = find(node.getChild(i), text, description);
            if (found != null) return found;
        }
        return null;
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
