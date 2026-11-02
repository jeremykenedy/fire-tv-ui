package com.jeremykenedy.firetv.ui.nativeapp.widget.puzzles;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutOptions;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutPreferences;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public final class PuzzleInstrumentation extends Instrumentation {
    private Activity home;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences prefs = getTargetContext().getSharedPreferences(PuzzlePreferences.FILE, Context.MODE_PRIVATE);
        SharedPreferences layout = getTargetContext().getSharedPreferences("native_layout", Context.MODE_PRIVATE);
        Map<String, ?> before = prefs.getAll();
        Map<String, ?> layoutBefore = layout.getAll();
        try {
            AccessibilityServiceInfo info = automation().getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS; automation().setServiceInfo(info);
            check(prefs.edit().clear().commit(), "Temporary puzzle fixture.");
            LayoutPreferences.save(getTargetContext(), new LayoutOptions(320, 180,
                    Collections.singletonList("widget:daily-puzzle"), Collections.<String>emptyList(), LayoutOptions.Mode.WIDGETS_ONLY));
            home = startActivitySync(new Intent().setClassName(getTargetContext(),
                    "com.jeremykenedy.firetv.ui.nativeapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            waitForIdleSync();
            select();
            DailyPuzzle initial = current();
            awaitFocused(cell(4, initial));
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT); waitForIdleSync();
            awaitFocused(cell(5, initial));
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER); waitForIdleSync();
            check(current().board == (initial.board ^ DailyPuzzle.mask(5)) && current().moves == 1, "Remote Select applies the actual neighbor rule and saves one move.");
            int savedBoard = current().board;
            click("Done", false); select();
            check(current().board == savedBoard && current().moves == 1, "Closing and reopening preserves exact progress.");
            if (current().solved()) {
                click("Reset today's puzzle", false); click("Reset", false);
            }
            int beforeHint = current().board;
            click("Hint", false);
            check(current().hintsUsed && current().board == beforeHint, "Native hint records assistance without moving a light.");
            for (int cell : current().solution()) click(cell(cell, current()), true);
            DailyPuzzle solved = current();
            check(solved.solved(), "Actual native presses solve the puzzle.");
            await("Solved in " + solved.moves + " moves with hints", false, true);
            click("Done", false); select();
            check(current().solved(), "Solved result survives reopening.");
            click("Reset today's puzzle", false); click("Reset", false);
            check(current().board == DailyPuzzle.forDate(PuzzlePreferences.today()).board && current().moves == 0 && !current().hintsUsed,
                    "Confirmed reset restores today's exact starting board and clears assistance.");
            click("Done", false);
            result.putString("result", "PASS: actual Home puzzle, D-pad focus/Select, persisted moves, hints, validated solved board and reset");
        } catch (Throwable failure) { result.putString("error", android.util.Log.getStackTraceString(failure)); }
        finally {
            try {
                onMain(new Runnable() { @Override public void run() { if (home != null) home.finish(); } });
                restore(prefs, before); restore(layout, layoutBefore);
                check(prefs.getAll().equals(before) && layout.getAll().equals(layoutBefore), "Exact puzzle and layout restoration.");
                result.putString("restored", "true");
            } catch (Throwable failure) { result.putString("restoration_error", android.util.Log.getStackTraceString(failure)); }
        }
        finish(result.containsKey("error") || result.containsKey("restoration_error") ? Activity.RESULT_CANCELED : Activity.RESULT_OK, result);
    }
    private DailyPuzzle current() { return PuzzlePreferences.read(getTargetContext(), PuzzlePreferences.today()); }
    private String cell(int cell, DailyPuzzle puzzle) { return "Row " + (cell / 3 + 1) + ", column " + (cell % 3 + 1) + ", " + (puzzle.lit(cell) ? "on" : "off"); }
    private void awaitFocused(String description) {
        long deadline = SystemClock.uptimeMillis() + 3000;
        do {
            if (await(description, true, true).isFocused()) return;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Native keyboard focus reaches puzzle cell: " + description);
    }
    private void select() {
        onMain(new Runnable() { @Override public void run() {
            View slot = home.getWindow().getDecorView().findViewWithTag("slot:widget:daily-puzzle");
            check(slot != null, "Actual daily puzzle slot exists."); slot.requestFocus(); slot.performClick();
        } });
    }
    private void click(String text, boolean description) {
        AccessibilityNodeInfo node = await(text, description, false);
        node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
        node = await(text, description, true);
        while (node != null && !node.isClickable()) node = node.getParent();
        check(node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Native action accepts Select: " + text);
        waitForIdleSync(); SystemClock.sleep(100);
    }
    private AccessibilityNodeInfo await(String text, boolean description, boolean visible) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            for (AccessibilityWindowInfo window : automation().getWindows()) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root != null && window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION
                        && getTargetContext().getPackageName().equals(String.valueOf(root.getPackageName()))) {
                    AccessibilityNodeInfo node = find(root, text, description, visible); if (node != null) return node;
                }
            }
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Native target-package puzzle control: " + text);
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, String text, boolean description, boolean visible) {
        if (node == null) return null;
        if ((!visible || node.isVisibleToUser()) && text.equalsIgnoreCase(String.valueOf(description ? node.getContentDescription() : node.getText()))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = find(node.getChild(i), text, description, visible); if (found != null) return found;
        }
        return null;
    }
    private UiAutomation automation() { return getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES); }
    private void onMain(final Runnable action) {
        final Throwable[] failure = new Throwable[1];
        runOnMainSync(new Runnable() { @Override public void run() { try { action.run(); } catch (Throwable error) { failure[0] = error; } } });
        if (failure[0] != null) throw new AssertionError(failure[0]); waitForIdleSync();
    }
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
        check(editor.commit(), "Restore exact saved preferences.");
    }
    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}
