package com.jeremykenedy.firetv.ui.nativeapp.device.screensavers;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

/** Run by the exclusive operator with the isolated fixture installed. No device settings are written. */
public final class ScreensaverInstrumentation extends Instrumentation {
    private Activity admin;
    private static final String FIXTURE = "com.jeremykenedy.firetv.ui.saverfixture/com.jeremykenedy.firetv.ui.saverfixture.FixtureDream";
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        final String active = ScreensaverSystem.active(getTargetContext());
        final boolean enabled = ScreensaverSystem.enabled(getTargetContext());
        final int delay = ScreensaverSystem.idleDelay(getTargetContext());
        try {
            Activity settings = startActivitySync(new Intent().setClassName(getTargetContext(),
                    "com.jeremykenedy.firetv.ui.nativeapp.SettingsActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            clickLabel(settings, "Device");
            ActivityMonitor monitor = addMonitor(ScreensaverSettingsActivity.class.getName(), null, false);
            clickLabel(settings, "Screensavers");
            admin = waitForMonitorWithTimeout(monitor, 4000);
            removeMonitor(monitor);
            check(admin != null, "Real Device > Screensavers route opens native administration");
            waitFor("configure:" + FIXTURE);
            onMain(new Runnable() { @Override public void run() {
                View configure = find("configure:" + FIXTURE);
                check(configure.requestFocus(), "Configure accepts remote focus");
            }});
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            check(waitPackage("com.jeremykenedy.firetv.ui.saverfixture"), "Configure visibly opens installed settings activity");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            check(waitPackage(getTargetContext().getPackageName()), "Back visibly returns to the launcher");
            waitFor("configure:" + FIXTURE);
            waitForIdleSync();
            onMain(new Runnable() { @Override public void run() {
                check(find("configure:" + FIXTURE).hasFocus(), "Configure retains list focus");
                check(((TextView) find("screensaver:editing")).getText().toString().contains("Administration fixture"), "Edited saver is labeled independently");
                find("preview:" + FIXTURE).performClick();
                check(((TextView) find("screensaver:status")).getText().toString().contains("no accessible preview"), "Unavailable preview is explicit");
            }});
            ActivityMonitor memoryMonitor = addMonitor(ScreensaverMemoryActivity.class.getName(), null, false);
            clickLabel(admin, "Memory and storage");
            Activity memory = waitForMonitorWithTimeout(memoryMonitor, 4000);
            removeMonitor(memoryMonitor);
            check(memory != null, "Native memory route opens");
            waitForTag(memory, "screensaver:memory");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            waitForIdleSync();
            ActivityMonitor catalogMonitor = addMonitor(ScreensaverCatalogActivity.class.getName(), null, false);
            clickLabel(admin, "Install screensavers");
            final Activity catalog = waitForMonitorWithTimeout(catalogMonitor, 4000);
            removeMonitor(catalogMonitor);
            check(catalog != null, "Native reviewed catalog route opens");
            onMain(new Runnable() { @Override public void run() {
                for (ScreensaverInstallCatalog.Entry entry : ScreensaverInstallCatalog.ENTRIES) {
                    check(catalog.getWindow().getDecorView().findViewWithTag("catalog:" + entry.packageName) != null,
                            "Every reviewed catalog entry is rendered");
                }
                check(((TextView) catalog.getWindow().getDecorView().findViewWithTag("screensaver:catalog-status"))
                        .getText().toString().contains("before downloading"), "Catalog does not download on entry");
            }});
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            waitForIdleSync();
            check(active.equals(ScreensaverSystem.active(getTargetContext())), "Configure/preview never select a saver");
            check(enabled == ScreensaverSystem.enabled(getTargetContext()), "Enabled state preserved");
            check(delay == ScreensaverSystem.idleDelay(getTargetContext()), "Shared idle delay preserved");
            result.putString("scenarios", "real Settings route, installed service metadata, D-pad Configure, separate edited state, unavailable preview, read-only memory, reviewed catalog without automatic download, no selection/timer mutation");
            finish(-1, result);
        } catch (Throwable failed) { result.putString("failure", failed.toString()); finish(0, result); }
    }
    private View find(String tag) { return admin.getWindow().getDecorView().findViewWithTag(tag); }
    private void waitFor(String tag) throws Exception {
        long until = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < until) {
            final boolean[] found = {false};
            onMain(new Runnable() { @Override public void run() { found[0] = find(tag) != null; }});
            if (found[0]) return;
            SystemClock.sleep(40);
        }
        throw new AssertionError("Missing installed fixture row");
    }
    private void clickLabel(final Activity activity, final String label) throws Exception {
        waitForIdleSync();
        onMain(new Runnable() { @Override public void run() {
            Button button = button(activity.getWindow().getDecorView(), label);
            check(button != null, "Missing route " + label);
            button.performClick();
        }});
        waitForIdleSync();
    }
    private Button button(View view, String label) {
        if (view instanceof Button && label.contentEquals(((Button) view).getText())) return (Button) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            Button found = button(((ViewGroup) view).getChildAt(i), label);
            if (found != null) return found;
        }
        return null;
    }
    private void waitForTag(final Activity activity, final String tag) throws Exception {
        long until = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < until) {
            final boolean[] found = {false};
            onMain(new Runnable() { @Override public void run() {
                found[0] = activity.getWindow().getDecorView().findViewWithTag(tag) != null;
            }});
            if (found[0]) return;
            SystemClock.sleep(40);
        }
        throw new AssertionError("Missing native content " + tag);
    }
    private boolean waitPackage(String wanted) {
        long until = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < until) {
            android.view.accessibility.AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                boolean match = wanted.contentEquals(root.getPackageName());
                root.recycle();
                if (match) return true;
            }
            SystemClock.sleep(40);
        }
        return false;
    }
    private void onMain(Runnable task) { runOnMainSync(task); }
    private void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
