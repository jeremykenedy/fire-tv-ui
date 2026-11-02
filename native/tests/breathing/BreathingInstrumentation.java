package com.jeremykenedy.firetv.ui.nativeapp.widget.breathing;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutOptions;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutPreferences;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public final class BreathingInstrumentation extends Instrumentation {
    private Activity home;
    private Activity settings;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences prefs = BreathingPreferences.preferences(getTargetContext());
        SharedPreferences layout = getTargetContext().getSharedPreferences("native_layout", Context.MODE_PRIVATE);
        Map<String, ?> before = prefs.getAll();
        Map<String, ?> layoutBefore = layout.getAll();
        try {
            check(prefs.edit().clear().commit(), "Temporary exercise fixture.");
            LayoutPreferences.save(getTargetContext(), new LayoutOptions(320, 180,
                    Collections.singletonList("widget:breathing-break"), Collections.<String>emptyList(), LayoutOptions.Mode.WIDGETS_ONLY));
            home = startActivitySync(new Intent().setClassName(getTargetContext(),
                    "com.jeremykenedy.firetv.ui.nativeapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            settle();
            check(!BreathingPreferences.read(getTargetContext()).running, "Home does not start an exercise implicitly.");
            select(); click("Start exercise");
            SystemClock.sleep(1100);
            check(BreathingPreferences.read(getTargetContext()).running, "Explicit Home action starts exercise.");
            settings = startActivitySync(new Intent().setClassName(getTargetContext(),
                    "com.jeremykenedy.firetv.ui.nativeapp.SettingsActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            settle();
            BreathingSession paused = BreathingPreferences.read(getTargetContext());
            check(!paused.running && paused.elapsed >= 1000, "Leaving Home pauses and saves elapsed progress.");
            final long progress = paused.elapsed;
            onMain(new Runnable() { @Override public void run() { settings.finish(); } });
            settle();
            check(!BreathingPreferences.read(getTargetContext()).running && BreathingPreferences.read(getTargetContext()).elapsed == progress,
                    "Returning Home preserves paused progress without autoplay.");
            select(); click("Resume exercise");
            check(BreathingPreferences.read(getTargetContext()).running, "Explicit resume works.");
            select(); click("Reset exercise");
            check(!BreathingPreferences.read(getTargetContext()).running && BreathingPreferences.read(getTargetContext()).elapsed == 0,
                    "Native reset returns to stopped state.");
            select(); click("Cadence and motion");
            click("Reduced motion: still circle and phase text"); click("Save");
            check(BreathingPreferences.read(getTargetContext()).reducedMotion && !BreathingPreferences.read(getTargetContext()).running,
                    "Reduced-motion configuration saves and remains opt-in.");
            result.putString("result", "PASS: native Home start, lifecycle pause, explicit resume/reset and reduced-motion dialog");
        } catch (Throwable error) { result.putString("error", android.util.Log.getStackTraceString(error)); }
        finally {
            try {
                onMain(new Runnable() { @Override public void run() {
                    if (settings != null) settings.finish();
                    if (home != null) home.finish();
                } });
                restore(prefs, before); restore(layout, layoutBefore);
                check(prefs.getAll().equals(before) && layout.getAll().equals(layoutBefore), "Exact exercise and layout restoration.");
                result.putString("restored", "true");
            } catch (Throwable error) { result.putString("restoration_error", android.util.Log.getStackTraceString(error)); }
        }
        finish(result.containsKey("error") || result.containsKey("restoration_error") ? Activity.RESULT_CANCELED : Activity.RESULT_OK, result);
    }

    private void select() {
        onMain(new Runnable() { @Override public void run() {
            View slot = home.getWindow().getDecorView().findViewWithTag("slot:widget:breathing-break");
            check(slot != null, "Actual breathing Home slot.");
            slot.requestFocus(); slot.performClick();
        } }); settle();
    }
    private void click(String text) {
        AccessibilityNodeInfo node = find(getUiAutomation().getRootInActiveWindow(), text);
        check(node != null, "Native action: " + text);
        while (node != null && !node.isClickable()) node = node.getParent();
        check(node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Native action clicks: " + text);
        settle();
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        if (node.getText() != null && text.contentEquals(node.getText())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = find(node.getChild(i), text);
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
    private void settle() { waitForIdleSync(); SystemClock.sleep(300); }
    @SuppressWarnings("unchecked") private static void restore(SharedPreferences prefs, Map<String, ?> values) {
        SharedPreferences.Editor editor = prefs.edit().clear();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            Object value = entry.getValue(); String key = entry.getKey();
            if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
            else throw new AssertionError("Unsupported preference type.");
        }
        check(editor.commit(), "Restore saved preferences.");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
