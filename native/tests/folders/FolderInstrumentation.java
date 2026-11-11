package com.jeremykenedy.firetv.ui.nativeapp.folders;

import android.app.Activity;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import java.util.Map;
import java.util.Set;

public final class FolderInstrumentation extends Instrumentation {
    private Activity activity;
    private UiAutomation automation;
    private String pendingName;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences prefs = getTargetContext().getSharedPreferences(FolderPreferences.STORE, Context.MODE_PRIVATE);
        SharedPreferences grid = getTargetContext().getSharedPreferences("native_app_grid", Context.MODE_PRIVATE);
        Map<String, ?> original = prefs.getAll(), originalGrid = grid.getAll();
        try {
            FolderOptions fixture = FolderOptions.empty().create("folder-proof-one", "Proof movies")
                    .create("folder-proof-two", "Proof games")
                    .addApp("folder-proof-one", "missing.proof/missing.proof.Main")
                    .addApp("folder-proof-one", "missing.second/missing.second.Main");
            FolderPreferences.save(getTargetContext(), fixture);
            check(FolderPreferences.encode(FolderPreferences.decode(FolderPreferences.encode(fixture)))
                    .equals(FolderPreferences.encode(fixture)), "Serialized folder round trip");
            open(null);
            select("Create folder"); key(KeyEvent.KEYCODE_DPAD_CENTER);
            typeName("Typed folder"); clickDialogButton("Save");
            FolderOptions created = FolderPreferences.load(getTargetContext());
            check(created.folders.size() == 3, "Keyboard create dialog persists folder");
            String typedId = created.folders.get(2).id;
            select("Typed folder · 0 apps"); longPress(); key(KeyEvent.KEYCODE_DPAD_CENTER);
            typeName("Renamed typed folder"); clickDialogButton("Save");
            check("Renamed typed folder".equals(FolderPreferences.load(getTargetContext()).find(typedId).name),
                    "Keyboard rename dialog preserves original stable identity");
            check(focus().startsWith("Renamed typed folder"), "Rename restores stable folder focus");
            select("Renamed typed folder · 0 apps"); longPress();
            for (int i = 0; i < 4; i++) key(KeyEvent.KEYCODE_DPAD_DOWN);
            key(KeyEvent.KEYCODE_DPAD_CENTER); clickDialogButton("Remove folder");
            check(FolderPreferences.load(getTargetContext()).find(typedId) == null, "Native remove confirmation removes only folder");
            check(FolderPreferences.load(getTargetContext()).find("folder-proof-one") != null, "Native remove keeps other folders");
            select("Proof movies · 2 apps"); key(KeyEvent.KEYCODE_DPAD_CENTER);
            check(has("Add apps"), "D-pad opens folder");
            check(has("missing.proof/missing.proof.Main · Unavailable"), "Missing app retains visible place");
            select("missing.proof/missing.proof.Main · Unavailable"); key(KeyEvent.KEYCODE_DPAD_CENTER);
            check(FolderPreferences.load(getTargetContext()).find("folder-proof-one").components.size() == 2,
                    "Unavailable app click keeps configuration");
            key(KeyEvent.KEYCODE_BACK);
            check(focus().startsWith("Proof movies"), "Back restores opener folder focus");
            open("folder-proof-one");
            check(has("Add apps"), "Direct folder route");
            select("missing.second/missing.second.Main · Unavailable");
            ActivityMonitor monitor = addMonitor(FolderActivity.class.getName(), null, false);
            onMain(new Runnable() { @Override public void run() { activity.recreate(); } });
            Activity recreated = monitor.waitForActivityWithTimeout(4000);
            removeMonitor(monitor);
            check(recreated != null, "Folder activity recreated");
            activity = recreated;
            SystemClock.sleep(500); waitForIdleSync();
            check(has("missing.proof/missing.proof.Main · Unavailable"), "Missing membership survives recreation");
            check(focus().equals("missing.second/missing.second.Main · Unavailable"), "Recreation restores the second member rather than the first app");
            FolderPreferences.save(getTargetContext(), fixture.rename("folder-proof-one", "Proof renamed")
                    .move("folder-proof-two", -1));
            open(null);
            check(has("Proof renamed · 2 apps"), "Rename persists without changing identity");
            check(FolderPreferences.load(getTargetContext()).folders.get(0).id.equals("folder-proof-two"), "Saved folder order");
            FolderPreferences.save(getTargetContext(), FolderPreferences.load(getTargetContext()).remove("folder-proof-one"));
            open(null);
            check(!has("Proof renamed · 2 apps") && has("Proof games · 0 apps"), "Remove affects only chosen folder");
            check(originalGrid.equals(grid.getAll()), "Folder operations leave ordinary grid and favorites untouched");
            prefs.edit().putString("folders", "broken JSON").commit();
            open(null);
            check("broken JSON".equals(prefs.getString("folders", "")), "Unreadable data preserved");
            result.putString("status", "PASS");
            result.putBoolean("keyboard_create_rename_remove_dialogs", true);
            result.putBoolean("dpad_open_back_focus", true);
            result.putBoolean("missing_membership_persistence", true);
            result.putBoolean("ordinary_grid_unchanged", true);
        } catch (Throwable error) {
            result.putString("status", "FAIL"); result.putString("reason", error.toString());
        } finally {
            if (activity != null) onMain(new Runnable() { @Override public void run() { activity.finish(); } });
            boolean restored = restore(prefs, original);
            result.putBoolean("folder_preferences_restored", restored && original.equals(prefs.getAll()));
            result.putBoolean("ordinary_grid_unchanged_final", originalGrid.equals(grid.getAll()));
        }
        finish(0, result);
    }
    private void open(String id) {
        if (activity != null) onMain(new Runnable() { @Override public void run() { activity.finish(); } });
        Intent intent = new Intent(getTargetContext(), FolderActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (id != null) intent.putExtra(FolderActivity.EXTRA_FOLDER_ID, id);
        activity = startActivitySync(intent); SystemClock.sleep(500); waitForIdleSync();
    }
    private void select(final String label) {
        onMain(new Runnable() { @Override public void run() {
            View button = button(activity.getWindow().getDecorView(), label);
            check(button != null, "Missing button: " + label); button.requestFocus();
        } });
    }
    private boolean has(final String label) {
        final boolean[] found = {false};
        onMain(new Runnable() { @Override public void run() { found[0] = button(activity.getWindow().getDecorView(), label) != null; } });
        return found[0];
    }
    private String focus() {
        final String[] label = {""};
        onMain(new Runnable() { @Override public void run() {
            View focus = activity.getCurrentFocus();
            if (focus instanceof Button) label[0] = ((Button) focus).getText().toString();
            else if (focus instanceof com.jeremykenedy.firetv.ui.nativeapp.focus.FocusTileView)
                label[0] = String.valueOf(focus.getContentDescription());
        } }); return label[0];
    }
    private View button(View view, String label) {
        if (view instanceof Button && ((Button) view).getText().toString().equals(label)) return (Button) view;
        if (view instanceof com.jeremykenedy.firetv.ui.nativeapp.focus.FocusTileView
                && label.equals(String.valueOf(view.getContentDescription()))) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = button(((ViewGroup) view).getChildAt(i), label); if (found != null) return found;
        }
        return null;
    }
    private void longPress() {
        onMain(new Runnable() { @Override public void run() {
            check(activity.getCurrentFocus() != null && activity.getCurrentFocus().performLongClick(), "Folder long press opens actions");
        } });
        SystemClock.sleep(200); waitForIdleSync();
    }
    private void typeName(String value) {
        AccessibilityNodeInfo input = awaitNode(null, true);
        for (int attempt = 0; !input.isFocused() && attempt < 30; attempt++) {
            input.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            SystemClock.sleep(100); waitForIdleSync();
            input = awaitNode(null, true);
        }
        check(input.isFocused(), "Native name input has keyboard focus");
        long selectTime = SystemClock.uptimeMillis();
        sendKeySync(new KeyEvent(selectTime, selectTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0, KeyEvent.META_CTRL_ON));
        sendKeySync(new KeyEvent(selectTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A, 0, KeyEvent.META_CTRL_ON));
        sendStringSync(value); waitForIdleSync();
        boolean entered = false;
        for (int attempt = 0; !entered && attempt < 40; attempt++) {
            input = awaitNode(null, true);
            if (input.refresh()) entered = value.equals(String.valueOf(input.getText()));
            if (!entered) SystemClock.sleep(100);
        }
        check(entered, "Keyboard text reaches the native folder name input (expected length=" + value.length()
                + ", observed length=" + (input.getText() == null ? 0 : input.getText().length())
                + ", focused=" + input.isFocused() + ")");
        pendingName = value;
    }
    private void clickDialogButton(String text) {
        if (keyboardVisible()) {
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            for (int attempt = 0; keyboardVisible() && attempt < 30; attempt++) SystemClock.sleep(100);
            check(!keyboardVisible(), "Back dismisses only the visible keyboard");
        }
        if ("Save".equalsIgnoreCase(text)) {
            AccessibilityNodeInfo input = awaitNode(null, true);
            check(input.refresh(), "Native name dialog remains readable after keyboard dismissal");
            check(pendingName != null && pendingName.equals(String.valueOf(input.getText())),
                    "Native name dialog remains open and keeps typed text after keyboard dismissal");
        }
        boolean clicked = false;
        for (int attempt = 0; !clicked && attempt < 20; attempt++) {
            AccessibilityNodeInfo button = awaitNode(text, false);
            if (button.isEnabled()) clicked = button.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            if (!clicked) SystemClock.sleep(100);
        }
        check(clicked, "Native dialog button accepts Select: " + text);
        SystemClock.sleep(250); waitForIdleSync();
        if ("Save".equalsIgnoreCase(text)) pendingName = null;
    }
    private UiAutomation automation() {
        if (automation == null) {
            automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            AccessibilityServiceInfo info = automation.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            automation.setServiceInfo(info);
        }
        return automation;
    }
    private boolean keyboardVisible() {
        for (AccessibilityWindowInfo window : automation().getWindows()) {
            AccessibilityNodeInfo root = window.getRoot();
            if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD
                    && root != null && root.isVisibleToUser()) return true;
        }
        return false;
    }
    private AccessibilityNodeInfo awaitNode(String text, boolean editable) {
        for (int attempt = 0; attempt < 50; attempt++) {
            AccessibilityNodeInfo node = findTargetNode(automation().getRootInActiveWindow(), text, editable);
            if (node != null) return node;
            for (AccessibilityWindowInfo window : automation().getWindows()) {
                node = findTargetNode(window.getRoot(), text, editable);
                if (node != null) return node;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Expected native dialog control: " + (editable ? "name input" : text));
    }
    private AccessibilityNodeInfo findTargetNode(AccessibilityNodeInfo root, String text, boolean editable) {
        if (root == null || !root.refresh()
                || !getTargetContext().getPackageName().equals(String.valueOf(root.getPackageName()))) return null;
        return findNode(root, text, editable);
    }
    private AccessibilityNodeInfo findNode(AccessibilityNodeInfo node, String text, boolean editable) {
        if (node == null) return null;
        if (node.isVisibleToUser() && (editable ? node.isEditable()
                : "android.widget.Button".equals(String.valueOf(node.getClassName()))
                        && text.equalsIgnoreCase(String.valueOf(node.getText())))) {
            return node.refresh() && node.isVisibleToUser() ? node : null;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findNode(node.getChild(i), text, editable);
            if (found != null) return found;
        }
        return null;
    }
    private void key(int code) { sendKeyDownUpSync(code); SystemClock.sleep(200); waitForIdleSync(); }
    private void onMain(final Runnable action) {
        final Throwable[] failure = {null};
        runOnMainSync(new Runnable() { @Override public void run() { try { action.run(); } catch (Throwable error) { failure[0] = error; } } });
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }
    @SuppressWarnings("unchecked")
    private boolean restore(SharedPreferences prefs, Map<String, ?> original) {
        SharedPreferences.Editor edit = prefs.edit().clear();
        for (Map.Entry<String, ?> entry : original.entrySet()) {
            Object value = entry.getValue(); String key = entry.getKey();
            if (value instanceof String) edit.putString(key, (String) value);
            else if (value instanceof Integer) edit.putInt(key, (Integer) value);
            else if (value instanceof Long) edit.putLong(key, (Long) value);
            else if (value instanceof Float) edit.putFloat(key, (Float) value);
            else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
            else if (value instanceof Set) edit.putStringSet(key, (Set<String>) value);
        }
        return edit.commit();
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
