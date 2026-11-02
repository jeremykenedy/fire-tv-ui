package com.jeremykenedy.firetv.ui.nativeapp.widget.notes;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.UiAutomation;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.Map;
import java.util.Set;

public final class NotesInstrumentation extends Instrumentation {
    private Activity activity;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences prefs = getTargetContext().getSharedPreferences(NotesPreferences.STORE, Context.MODE_PRIVATE);
        Map<String, ?> before = prefs.getAll();
        try {
            AccessibilityServiceInfo info = automation().getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            automation().setServiceInfo(info);
            check(prefs.edit().clear().commit(), "Clear fixture");
            NotesWidgetProvider provider = new NotesWidgetProvider();
            check(provider.state(getTargetContext()) == com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider.State.SETUP_REQUIRED, "Fresh widget needs setup");
            activity = startActivitySync(new Intent(getTargetContext(), NotesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            clickTag("notes:add-note"); enter("Private fixture note"); clickText("Save");
            String note = NotesPreferences.load(getTargetContext()).items.get(0).id;
            check(NotesPreferences.load(getTargetContext()).find(note).text.equals("Private fixture note"), "Native note entry saved");
            clickTag("notes:add-item"); enter("Fixture checklist"); clickText("Save");
            String item = NotesPreferences.load(getTargetContext()).items.get(1).id;
            check(!NotesPreferences.load(getTargetContext()).homeText().contains("Private fixture"), "Default Home redaction");
            clickTag("notes:" + item); check(NotesPreferences.load(getTargetContext()).find(item).checked, "Native check");
            clickTag("notes:" + item); check(!NotesPreferences.load(getTargetContext()).find(item).checked, "Native uncheck");
            holdTag("notes:" + item); clickText("Move earlier");
            check(NotesPreferences.load(getTargetContext()).items.get(0).id.equals(item), "Native order persisted");
            holdTag("notes:" + note); clickText("Edit text"); enter("Edited fixture"); clickText("Save");
            check(NotesPreferences.load(getTargetContext()).find(note).text.equals("Edited fixture"), "Native edit preserved identity");
            clickTag("notes:privacy"); clickText("Show details");
            check(NotesPreferences.load(getTargetContext()).homeText().contains("Edited fixture"), "Deliberate Home preview opt-in");
            clickTag("notes:privacy"); check(NotesPreferences.load(getTargetContext()).summaryOnly, "Native redaction restored");
            clickTag("notes:export"); clickText("Allow in backups");
            check(NotesPreferences.exportAllowed(getTargetContext()), "Explicit backup privacy opt-in");
            clickTag("notes:export"); check(!NotesPreferences.exportAllowed(getTargetContext()), "Backup privacy opt-out");
            holdTag("notes:" + note); clickText("Delete"); clickText("Delete");
            check(NotesPreferences.load(getTargetContext()).find(note) == null, "Native deletion");
            check(NotesPreferences.load(getTargetContext()).find(item) != null, "Delete preserves other item");
            runOnMainSync(new Runnable() { @Override public void run() { activity.recreate(); } });
            waitForIdleSync();
            check(NotesPreferences.load(getTargetContext()).items.get(0).id.equals(item), "Activity recreation preserves order and identity");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); waitForIdleSync();
            check(provider.state(getTargetContext()) == com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider.State.READY, "Ready state");
            NotesPreferences.save(getTargetContext(), NotesPreferences.load(getTargetContext()).remove(item));
            check(provider.state(getTargetContext()) == com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider.State.EMPTY, "Configured empty state");
            result.putString("result", "PASS: native entry, edit, checklist, order, delete, privacy and recreation");
        } catch (Throwable error) { result.putString("error", android.util.Log.getStackTraceString(error)); }
        finally {
            if (activity != null) runOnMainSync(new Runnable() { @Override public void run() { activity.finish(); } });
            SharedPreferences.Editor restore = prefs.edit().clear();
            for (Map.Entry<String, ?> entry : before.entrySet()) {
                Object value = entry.getValue(); String key = entry.getKey();
                if (value instanceof String) restore.putString(key, (String) value);
                else if (value instanceof Boolean) restore.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) restore.putInt(key, (Integer) value);
                else if (value instanceof Long) restore.putLong(key, (Long) value);
                else if (value instanceof Float) restore.putFloat(key, (Float) value);
                else if (value instanceof Set) { @SuppressWarnings("unchecked") Set<String> set = (Set<String>) value; restore.putStringSet(key, set); }
            }
            boolean restored = restore.commit() && before.equals(prefs.getAll());
            result.putBoolean("restored", restored);
            if (!restored) result.putString("error", "Notes fixture restoration failed");
        }
        finish(result.containsKey("error") ? Activity.RESULT_CANCELED : Activity.RESULT_OK, result);
    }
    private void clickTag(final String tag) {
        runOnMainSync(new Runnable() { @Override public void run() {
            View view = activity.getWindow().getDecorView().findViewWithTag(tag);
            check(view != null, "Missing native control " + tag); view.requestFocus(); view.performClick();
        } }); waitForIdleSync();
    }
    private void holdTag(final String tag) {
        runOnMainSync(new Runnable() { @Override public void run() {
            View view = activity.getWindow().getDecorView().findViewWithTag(tag);
            check(view != null, "Missing item " + tag); view.requestFocus(); view.performLongClick();
        } }); waitForIdleSync();
    }
    private void enter(String value) {
        AccessibilityNodeInfo node = null;
        for (int attempt = 0; attempt < 40 && node == null; attempt++) {
            for (AccessibilityNodeInfo root : appWindows()) {
                node = findEdit(root); if (node != null) break;
            }
            if (node == null) SystemClock.sleep(50);
        }
        check(node != null, "Native text entry visible in the target app"); Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        check(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args), "Enter native text"); waitForIdleSync();
        check(node.refresh() && value.contentEquals(node.getText()), "Native editor contains the entered text");
    }
    private AccessibilityNodeInfo findEdit(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if ("android.widget.EditText".contentEquals(node.getClassName())) return node;
        for (int i = 0; i < node.getChildCount(); i++) { AccessibilityNodeInfo found = findEdit(node.getChild(i)); if (found != null) return found; }
        return null;
    }
    private void clickText(String text) {
        AccessibilityNodeInfo found = null;
        for (int attempt = 0; attempt < 40 && found == null; attempt++) {
            for (AccessibilityNodeInfo root : appWindows()) {
                found = matching(root, text); if (found != null) break;
            }
            if (found == null) SystemClock.sleep(50);
        }
        check(found != null, "Missing dialog action " + text);
        while (!found.isClickable() && found.getParent() != null) found = found.getParent();
        check(found.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Activate dialog action " + text);
        waitForIdleSync();
    }
    private AccessibilityNodeInfo matching(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        if (node.isVisibleToUser() && text.equalsIgnoreCase(String.valueOf(node.getText()))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = matching(node.getChild(i), text); if (found != null) return found;
        }
        return null;
    }
    private UiAutomation automation() { return getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES); }
    private java.util.List<AccessibilityNodeInfo> appWindows() {
        java.util.List<AccessibilityNodeInfo> roots = new java.util.ArrayList<>();
        for (AccessibilityWindowInfo window : automation().getWindows()) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root != null && getTargetContext().getPackageName().equals(String.valueOf(root.getPackageName()))) roots.add(root);
        }
        if (roots.isEmpty()) {
            AccessibilityNodeInfo root = automation().getRootInActiveWindow();
            if (root != null && getTargetContext().getPackageName().equals(String.valueOf(root.getPackageName()))) roots.add(root);
        }
        return roots;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
