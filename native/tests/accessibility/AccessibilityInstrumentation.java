package com.jeremykenedy.firetv.ui.nativeapp.accessibility;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import java.util.Map;

public final class AccessibilityInstrumentation extends Instrumentation {
    private Activity settings;
    private Activity editor;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences prefs = AccessibilityPreferences.file(getTargetContext());
        Map<String, ?> previous = prefs.getAll();
        try {
            AccessibilityPreferences.save(getTargetContext(), 100, false);
            settings = startActivitySync(new Intent().setClassName(getTargetContext(),
                    "com.jeremykenedy.firetv.ui.nativeapp.SettingsActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ActivityMonitor monitor = addMonitor(AccessibilitySettingsActivity.class.getName(), null, false);
            runOnMainSync(new Runnable() { @Override public void run() {
                TextView route = findText(settings.getWindow().getDecorView(), "Text and contrast");
                check(route != null, "Appearance has the canonical readability route");
                route.requestFocus();
            } });
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            editor = waitForMonitorWithTimeout(monitor, 4000);
            check(editor != null, "Select opens readability settings");
            waitForIdleSync();
            final float[] baseline = new float[1];
            runOnMainSync(new Runnable() { @Override public void run() {
                Button size = editor.findViewById(0x2071);
                check(size.hasFocus(), "Text size is initially focused");
                baseline[0] = size.getTextSize();
            } });
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            long chooserDeadline = android.os.SystemClock.uptimeMillis() + 5000;
            boolean chooserFocused = false;
            while (android.os.SystemClock.uptimeMillis() < chooserDeadline) {
                android.view.accessibility.AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
                if (root != null) {
                    android.view.accessibility.AccessibilityNodeInfo focus = root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT);
                    chooserFocused = !root.findAccessibilityNodeInfosByText("Larger (110%)").isEmpty()
                            && focus != null && "android.widget.ListView".contentEquals(focus.getClassName());
                    if (focus != null) focus.recycle();
                    root.recycle();
                }
                if (chooserFocused) break;
                android.os.SystemClock.sleep(40);
            }
            check(chooserFocused, "Text size chooser receives remote focus");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            waitForIdleSync();
            check(AccessibilityPreferences.textPercent(getTargetContext()) == 110, "Remote chooser saves larger text");
            runOnMainSync(new Runnable() { @Override public void run() {
                Button size = editor.findViewById(0x2071);
                check(size.hasFocus(), "Closing chooser returns focus");
                check(Math.abs(size.getTextSize() - baseline[0] * 1.1f) < 0.1f, "Visible text grows once");
                AccessibilityStyle.apply(editor, editor.getWindow().getDecorView());
                check(Math.abs(size.getTextSize() - baseline[0] * 1.1f) < 0.1f, "Refresh does not compound text size");
            } });
            long editorDeadline = android.os.SystemClock.uptimeMillis() + 5000;
            while (!editor.hasWindowFocus() && android.os.SystemClock.uptimeMillis() < editorDeadline) android.os.SystemClock.sleep(40);
            check(editor.hasWindowFocus(), "Readability page regains window focus");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            waitForIdleSync();
            check(AccessibilityPreferences.highContrast(getTargetContext()), "Remote contrast action saves choice");
            runOnMainSync(new Runnable() { @Override public void run() {
                Button contrast = editor.findViewById(0x2072);
                check(contrast.hasFocus() && contrast.getCurrentTextColor() == android.graphics.Color.BLACK,
                        "Focused contrast button uses dark text on its white surface");
                AccessibilityPreferences.save(getTargetContext(), 100, false);
                AccessibilityStyle.apply(editor, editor.getWindow().getDecorView());
                check(Math.abs(((Button) editor.findViewById(0x2071)).getTextSize() - baseline[0]) < 0.1f,
                        "Returning to Default restores original text size");
            } });
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            waitForIdleSync();
            runOnMainSync(new Runnable() { @Override public void run() {
                check(findText(settings.getWindow().getDecorView(), "Text and contrast").hasFocus(), "Back restores the settings route focus");
            } });
            result.putString("result", "PASS: settings route, remote text/contrast choices, idempotent scaling, default restoration and Back focus");
        } catch (Throwable failure) {
            result.putString("failure", failure.toString());
        } finally {
            runOnMainSync(new Runnable() { @Override public void run() { if (editor != null) editor.finish(); if (settings != null) settings.finish(); } });
            SharedPreferences.Editor restore = prefs.edit();
            restore(restore, "text_percent", previous.get("text_percent"));
            restore(restore, "high_contrast", previous.get("high_contrast"));
            if (!restore.commit() || !prefs.getAll().equals(previous)) result.putString("failure", "Readability preferences were not restored exactly");
        }
        finish(result.containsKey("failure") ? 1 : -1, result);
    }

    private static TextView findText(View view, String value) {
        if (view instanceof TextView && ((TextView) view).getText().toString().equals(value)) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) view;
            for (int i = 0; i < parent.getChildCount(); i++) {
                TextView found = findText(parent.getChildAt(i), value);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void restore(SharedPreferences.Editor edit, String key, Object value) {
        if (value instanceof Integer) edit.putInt(key, (Integer) value);
        else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
        else if (value instanceof String) edit.putString(key, (String) value);
        else if (value instanceof Long) edit.putLong(key, (Long) value);
        else if (value instanceof Float) edit.putFloat(key, (Float) value);
        else if (value instanceof java.util.Set) {
            @SuppressWarnings("unchecked") java.util.Set<String> values = (java.util.Set<String>) value;
            edit.putStringSet(key, values);
        } else edit.remove(key);
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
