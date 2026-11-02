package com.jeremykenedy.firetv.ui.nativeapp.widget.quickactions;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;
import com.jeremykenedy.firetv.ui.nativeapp.locks.LockStore;
import java.io.File;

/** Only the coordinator's exclusively leased preview emulator, never an owner's production TV. */
public final class QuickActionsInstrumentation extends Instrumentation {
    private Activity home;

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        File file = new File(getTargetContext().getNoBackupFilesDir(), "quick-actions-v1.txt");
        File backup = new File(file.getPath() + ".bak");
        File pending = new File(file.getPath() + ".new");
        boolean fresh = !file.exists() && !backup.exists() && !pending.exists();
        File lockDirectory = new File(getTargetContext().getNoBackupFilesDir(), "launcher-locks");
        boolean freshLocks = !lockDirectory.exists();
        try {
            check(fresh, "This proof requires absent quick-action storage and preserves existing owner actions.");
            check(freshLocks, "This proof requires absent lock storage and preserves existing owner locks.");
            final QuickActionsWidgetProvider provider = new QuickActionsWidgetProvider();
            check(!provider.defaultVisible(), "Quick actions must be opt-in on Home.");
            check(provider.state(getTargetContext()) == WidgetProvider.State.SETUP_REQUIRED, "An empty widget needs setup.");
            final String component = getContext().getPackageName() + "/" + QuickActionFixture.class.getName();
            final QuickActions.Entry app = QuickActions.Entry.create("Fixture app", component, "");
            final QuickActions.Entry link = QuickActions.Entry.create("Fixture title", component, "https://quick-action.example/title/42");
            QuickActions.Entry unsupported = QuickActions.Entry.create("Unsupported", component, "https://other.example/title/42");
            check(QuickActionLaunch.available(getTargetContext(), app), "The installed exported fixture app must be usable.");
            check(QuickActionLaunch.available(getTargetContext(), link), "Only a link resolved inside the selected app may be usable.");
            check(!QuickActionLaunch.available(getTargetContext(), unsupported), "Unsupported links must not fall back to another app or generic app open.");
            QuickActionStore store = new QuickActionStore(getTargetContext());
            store.put(app, true); store.put(link, true);
            store.move(link.id, -1);
            check(new QuickActionStore(getTargetContext()).read().entries.get(0).id.equals(link.id), "Native storage must preserve stable order.");
            check(provider.state(getTargetContext()) == WidgetProvider.State.READY, "Configured real actions must become ready.");
            home = startActivitySync(new Intent().setClassName(getTargetContext(), "com.jeremykenedy.firetv.ui.nativeapp.MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            onMain(new Runnable() { @Override public void run() { provider.configure(home); } });
            await("Quick actions");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); waitForIdleSync();
            onMain(new Runnable() { @Override public void run() { QuickActionLaunch.open(home, app); } });
            await("Fixture app opened");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); waitForIdleSync();
            onMain(new Runnable() { @Override public void run() { QuickActionLaunch.open(home, link); } });
            await("Fixture content link opened");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); waitForIdleSync();
            new LockStore(getTargetContext()).createPinAndLock("834905".toCharArray(), component);
            onMain(new Runnable() { @Override public void run() { QuickActionLaunch.open(home, app); } });
            await("Unlock for this launch");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); waitForIdleSync();
            check(!visible("Fixture app opened"), "Canceling the launcher lock must prevent quick-action dispatch.");
            store.remove(app.id);
            check(store.read().entries.size() == 1 && store.read().entries.get(0).id.equals(link.id), "Removing one action must retain another.");
            result.putString("status", "PASS");
            result.putBoolean("native_private_persistence_order_remove", true);
            result.putBoolean("source_scoped_supported_unsupported_links", true);
            result.putBoolean("actual_external_app_and_content_link_open", true);
            result.putBoolean("configuration_does_not_launch", true);
            result.putBoolean("launcher_component_lock_cancel_prevents_launch", true);
        } catch (Throwable failed) {
            result.putString("status", "FAIL"); result.putString("reason", failed.toString());
        } finally {
            if (home != null) onMain(new Runnable() { @Override public void run() { home.finish(); } });
            if (fresh) {
                boolean cleaned = (!file.exists() || file.delete()) && (!backup.exists() || backup.delete()) && (!pending.exists() || pending.delete());
                result.putBoolean("private_storage_restored_absent", cleaned && !file.exists() && !backup.exists() && !pending.exists());
            }
            if (freshLocks) {
                File[] locks = lockDirectory.listFiles();
                boolean cleaned = true;
                if (locks != null) for (File lock : locks) if (!lock.delete()) cleaned = false;
                if (lockDirectory.exists() && !lockDirectory.delete()) cleaned = false;
                result.putBoolean("lock_storage_restored_absent", cleaned && !lockDirectory.exists());
            }
        }
        finish(0, result);
    }

    private void await(String text) {
        for (int i = 0; i < 80; i++) {
            if (visible(text)) return;
            SystemClock.sleep(100);
        }
        throw new AssertionError("Expected native screen not visible: " + text);
    }

    private boolean visible(String text) {
        AccessibilityNodeInfo root = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).getRootInActiveWindow();
        if (root != null) for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text))
            if (node.isVisibleToUser() && text.equals(String.valueOf(node.getText()))) return true;
        return false;
    }

    private void onMain(final Runnable action) {
        final Throwable[] error = {null};
        runOnMainSync(new Runnable() { @Override public void run() { try { action.run(); } catch (Throwable failed) { error[0] = failed; } } });
        if (error[0] != null) throw new AssertionError(error[0]);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
