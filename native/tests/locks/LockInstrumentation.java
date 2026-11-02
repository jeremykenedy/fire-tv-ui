package com.jeremykenedy.firetv.ui.nativeapp.locks;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Run only on the coordinator's leased, fresh preview emulator. Never on a production TV. */
public final class LockInstrumentation extends Instrumentation {
    private Activity activity;
    private String phase = "startup";
    private final AtomicInteger grants = new AtomicInteger();
    private static final String APP = "com.example.lockfixture/.Main";

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        File directory = new File(getTargetContext().getNoBackupFilesDir(), "launcher-locks");
        boolean fresh = !directory.exists();
        try {
            AccessibilityServiceInfo info = automation().getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            automation().setServiceInfo(info);
            check(fresh, "This proof requires absent lock storage; existing owner locks must not be changed.");
            activity = startActivitySync(new Intent().setClassName(getTargetContext(), LockSettingsActivity.class.getName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            phase = "create_pin";
            click("Create launcher PIN");
            enter("834905", "834905"); click("Save PIN");
            await("Change launcher PIN");
            final LockStore store = new LockStore(getTargetContext());
            check(store.hasPin(), "The native PIN creation dialog must persist enrollment.");
            phase = "lock_app";
            onMain(new Runnable() { @Override public void run() { LauncherLocks.showControls(activity, APP, "Fixture", refresh()); } });
            enter("834905"); click("Continue");
            for (int i = 0; i < 80 && !store.isLocked(APP); i++) SystemClock.sleep(100);
            check(store.isLocked(APP), "A confirmed lock must persist for the exact component.");
            onMain(new Runnable() { @Override public void run() { LauncherLocks.guard(activity, APP, grant()); } });
            phase = "wrong_pin";
            enter("0000"); click("Continue"); SystemClock.sleep(1500);
            check(grants.get() == 0, "Wrong PIN must not dispatch the callback.");
            cancelPinDialog();
            check(grants.get() == 0, "Back must cancel a locked launch.");
            phase = "correct_pin";
            onMain(new Runnable() { @Override public void run() { LauncherLocks.guard(activity, APP, grant()); } });
            enter("834905"); click("Continue");
            for (int i = 0; i < 80 && grants.get() == 0; i++) SystemClock.sleep(100);
            check(grants.get() == 1, "Correct PIN dispatches exactly once.");
            check(new LockStore(getTargetContext()).isLocked(APP), "Unlocking one launch must retain the lock.");
            phase = "folder_lock";
            check(store.setLocked("folder:proof-room", true, "834905".toCharArray()) == LockResult.GRANTED,
                    "A separate stable folder lock must be saved.");
            onMain(new Runnable() { @Override public void run() { LauncherLocks.guard(activity, "folder:proof-room", grant()); } });
            cancelPinDialog();
            check(grants.get() == 1, "Back must cancel a locked folder opening.");
            onMain(new Runnable() { @Override public void run() { LauncherLocks.guard(activity, "folder:proof-room", grant()); } });
            enter("834905"); click("Continue");
            for (int i = 0; i < 80 && grants.get() == 1; i++) SystemClock.sleep(100);
            check(grants.get() == 2 && store.isLocked(APP) && store.isLocked("folder:proof-room"),
                    "Folder opening must grant once and retain both independent locks.");
            phase = "recreate";
            ActivityMonitor monitor = addMonitor(LockSettingsActivity.class.getName(), null, false);
            onMain(new Runnable() { @Override public void run() { activity.recreate(); } });
            Activity recreated = monitor.waitForActivityWithTimeout(4000);
            removeMonitor(monitor);
            check(recreated != null, "Lock settings must recreate successfully.");
            activity = recreated; waitForIdleSync();
            check(new LockStore(getTargetContext()).isLocked(APP), "Activity recreation retains the component lock.");
            result.putString("status", "PASS");
            result.putBoolean("native_pin_creation_lock_wrong_cancel_correct", true);
            result.putBoolean("persistent_component_lock", true);
        } catch (Throwable failed) {
            result.putString("status", "FAIL"); result.putString("reason", failed.toString()); result.putString("phase", phase);
        } finally {
            if (activity != null) onMain(new Runnable() { @Override public void run() { activity.finish(); } });
            if (fresh) {
                File[] files = directory.listFiles();
                if (files != null) for (File file : files) if (!file.delete()) result.putBoolean("cleanup_failed", true);
                if (directory.exists() && !directory.delete()) result.putBoolean("cleanup_failed", true);
                result.putBoolean("storage_restored_absent", !directory.exists());
            }
        }
        finish(0, result);
    }

    private Runnable grant() { return new Runnable() { @Override public void run() { grants.incrementAndGet(); } }; }
    private Runnable refresh() { return new Runnable() { @Override public void run() { } }; }

    private void enter(String... values) {
        List<AccessibilityNodeInfo> inputs = inputs(values.length);
        for (int i = 0; i < values.length; i++) {
            AccessibilityNodeInfo input = inputs.get(i);
            check(input.isPassword(), "PIN inputs must be masked.");
            input.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
            input.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            waitForIdleSync();
            Bundle data = new Bundle(); data.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, values[i]);
            check(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, data), "PIN entry must work.");
            SystemClock.sleep(100);
            input.refresh();
            CharSequence observed = input.getText();
            check(observed != null && observed.length() == values[i].length(), "PIN field must retain the entered character count.");
        }
        dismissKeyboard();
    }

    private List<AccessibilityNodeInfo> inputs(int expected) {
        for (int attempt = 0; attempt < 80; attempt++) {
            for (AccessibilityNodeInfo root : appWindows()) {
                List<AccessibilityNodeInfo> found = new ArrayList<>();
                collect(root, found);
                if (found.size() == expected) return found;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Expected native PIN inputs not ready; " + inputState());
    }

    private void collect(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> inputs) {
        if (node == null) return;
        if (node.isEditable() && node.isPassword()) inputs.add(node);
        for (int i = 0; i < node.getChildCount(); i++) collect(node.getChild(i), inputs);
    }

    private void dismissKeyboard() {
        waitForIdleSync(); SystemClock.sleep(150);
        if (!keyboardVisible()) return;
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        for (int i = 0; i < 40 && keyboardVisible(); i++) SystemClock.sleep(100);
        check(!keyboardVisible(), "Native keyboard must dismiss before dialog actions.");
    }

    private boolean keyboardVisible() {
        for (AccessibilityWindowInfo window : automation().getWindows()) {
            if (window.getType() != AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue;
            AccessibilityNodeInfo root = window.getRoot();
            if (root != null && root.isVisibleToUser()) return true;
        }
        return false;
    }

    private void cancelPinDialog() {
        inputs(1); dismissKeyboard();
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        for (int attempt = 0; attempt < 80; attempt++) {
            boolean any = false;
            for (AccessibilityNodeInfo root : appWindows()) {
                List<AccessibilityNodeInfo> found = new ArrayList<>(); collect(root, found);
                any |= !found.isEmpty();
            }
            if (!any) return;
            SystemClock.sleep(100);
        }
        throw new AssertionError("Back did not dismiss PIN dialog; " + inputState());
    }

    private String inputState() {
        StringBuilder state = new StringBuilder("keyboard_visible=").append(keyboardVisible());
        for (AccessibilityNodeInfo root : appWindows()) {
            List<AccessibilityNodeInfo> found = new ArrayList<>(); collect(root, found);
            state.append(";password_fields=").append(found.size());
            for (AccessibilityNodeInfo field : found) state.append("[visible=").append(field.isVisibleToUser())
                    .append(",focused=").append(field.isFocused()).append(']');
        }
        return state.toString();
    }

    private AccessibilityNodeInfo await(String text) {
        for (int i = 0; i < 80; i++) {
            for (AccessibilityNodeInfo root : appWindows()) {
                AccessibilityNodeInfo found = matching(root, text);
                if (found != null) return found;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Expected native control not found: " + text + "; " + buttonState());
    }

    private UiAutomation automation() { return getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES); }

    private List<AccessibilityNodeInfo> appWindows() {
        List<AccessibilityNodeInfo> roots = new ArrayList<>();
        for (AccessibilityWindowInfo window : automation().getWindows()) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root != null && getTargetContext().getPackageName().equals(String.valueOf(root.getPackageName()))) roots.add(root);
        }
        if (roots.isEmpty()) {
            AccessibilityNodeInfo root = automation().getRootInActiveWindow();
            if (root != null) roots.add(root);
        }
        return roots;
    }

    private AccessibilityNodeInfo matching(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        if (!node.isPassword() && node.isVisibleToUser() && text.equalsIgnoreCase(String.valueOf(node.getText()))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = matching(node.getChild(i), text);
            if (found != null) return found;
        }
        return null;
    }

    private String buttonState() {
        StringBuilder state = new StringBuilder();
        for (AccessibilityNodeInfo root : appWindows()) buttonState(root, state);
        return "native_buttons=" + state;
    }

    private void buttonState(AccessibilityNodeInfo node, StringBuilder state) {
        if (node == null) return;
        if ("android.widget.Button".equals(String.valueOf(node.getClassName()))) {
            String label = String.valueOf(node.getText());
            boolean known = "Save PIN".equalsIgnoreCase(label) || "Cancel".equalsIgnoreCase(label)
                    || "Continue".equalsIgnoreCase(label) || "Create launcher PIN".equalsIgnoreCase(label);
            state.append(known ? label : "other").append(":visible=").append(node.isVisibleToUser()).append(';');
        }
        for (int i = 0; i < node.getChildCount(); i++) buttonState(node.getChild(i), state);
    }

    private void click(String text) {
        AccessibilityNodeInfo node = await(text);
        check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Native control must accept Select: " + text);
        waitForIdleSync();
    }

    private void onMain(final Runnable action) {
        final Throwable[] error = {null};
        runOnMainSync(new Runnable() { @Override public void run() { try { action.run(); } catch (Throwable failed) { error[0] = failed; } } });
        if (error[0] != null) throw new AssertionError(error[0]);
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
