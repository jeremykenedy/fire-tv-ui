package com.jeremykenedy.firetv.ui.nativeapp.locks;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Run only on the coordinator's leased, fresh preview emulator. Never on a production TV. */
public final class LockInstrumentation extends Instrumentation {
    private Activity activity;
    private final AtomicInteger grants = new AtomicInteger();
    private static final String APP = "com.example.lockfixture/.Main";

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        File directory = new File(getTargetContext().getNoBackupFilesDir(), "launcher-locks");
        boolean fresh = !directory.exists();
        try {
            check(fresh, "This proof requires absent lock storage; existing owner locks must not be changed.");
            activity = startActivitySync(new Intent().setClassName(getTargetContext(), LockSettingsActivity.class.getName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            click("Create launcher PIN");
            enter("834905", "834905"); click("Save PIN");
            await("Change launcher PIN");
            final LockStore store = new LockStore(getTargetContext());
            check(store.hasPin(), "The native PIN creation dialog must persist enrollment.");
            onMain(new Runnable() { @Override public void run() { LauncherLocks.showControls(activity, APP, "Fixture", refresh()); } });
            enter("834905"); click("Continue");
            for (int i = 0; i < 80 && !store.isLocked(APP); i++) SystemClock.sleep(100);
            check(store.isLocked(APP), "A confirmed lock must persist for the exact component.");
            onMain(new Runnable() { @Override public void run() { LauncherLocks.guard(activity, APP, grant()); } });
            enter("0000"); click("Continue"); SystemClock.sleep(1500);
            check(grants.get() == 0, "Wrong PIN must not dispatch the callback.");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); SystemClock.sleep(200);
            check(grants.get() == 0, "Back must cancel a locked launch.");
            onMain(new Runnable() { @Override public void run() { LauncherLocks.guard(activity, APP, grant()); } });
            enter("834905"); click("Continue");
            for (int i = 0; i < 80 && grants.get() == 0; i++) SystemClock.sleep(100);
            check(grants.get() == 1, "Correct PIN dispatches exactly once.");
            check(new LockStore(getTargetContext()).isLocked(APP), "Unlocking one launch must retain the lock.");
            check(store.setLocked("folder:proof-room", true, "834905".toCharArray()) == LockResult.GRANTED,
                    "A separate stable folder lock must be saved.");
            onMain(new Runnable() { @Override public void run() { LauncherLocks.guard(activity, "folder:proof-room", grant()); } });
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); SystemClock.sleep(200);
            check(grants.get() == 1, "Back must cancel a locked folder opening.");
            onMain(new Runnable() { @Override public void run() { LauncherLocks.guard(activity, "folder:proof-room", grant()); } });
            enter("834905"); click("Continue");
            for (int i = 0; i < 80 && grants.get() == 1; i++) SystemClock.sleep(100);
            check(grants.get() == 2 && store.isLocked(APP) && store.isLocked("folder:proof-room"),
                    "Folder opening must grant once and retain both independent locks.");
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
            result.putString("status", "FAIL"); result.putString("reason", failed.toString());
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
        List<AccessibilityNodeInfo> inputs = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            inputs.clear(); collect(getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).getRootInActiveWindow(), inputs);
            if (inputs.size() == values.length) break;
            SystemClock.sleep(100);
        }
        check(inputs.size() == values.length, "Expected native PIN inputs must be visible.");
        for (int i = 0; i < values.length; i++) {
            check(inputs.get(i).isPassword(), "PIN inputs must be masked.");
            Bundle data = new Bundle(); data.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, values[i]);
            check(inputs.get(i).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, data), "PIN entry must work.");
        }
    }

    private void collect(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> inputs) {
        if (node == null) return;
        if (node.isEditable() && node.isVisibleToUser()) inputs.add(node);
        for (int i = 0; i < node.getChildCount(); i++) collect(node.getChild(i), inputs);
    }

    private AccessibilityNodeInfo await(String text) {
        for (int i = 0; i < 80; i++) {
            AccessibilityNodeInfo root = getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).getRootInActiveWindow();
            if (root != null) for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) {
                if (node.isVisibleToUser() && text.equals(String.valueOf(node.getText()))) return node;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Expected native control not found: " + text);
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
