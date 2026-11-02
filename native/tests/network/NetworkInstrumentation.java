package com.jeremykenedy.firetv.ui.nativeapp.device.network;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

/** No network mutations, preferences, fixture apps or saved documents. */
public final class NetworkInstrumentation extends Instrumentation {
    private static final String ROOT = "com.jeremykenedy.firetv.ui.nativeapp.";
    private Activity settings;
    private Activity screen;

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            settings = startActivitySync(new Intent().setClassName(getTargetContext(), ROOT + "SettingsActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            settle();
            main(new Runnable() { @Override public void run() {
                Button device = button(settings.getWindow().getDecorView(), "Device");
                check(device != null && device.requestFocus(), "Device category must accept remote focus");
            }});
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            settle();
            openRoute("Network and IP", "NetworkActivity");
            check("network:settings".equals(String.valueOf(screen.getCurrentFocus().getTag())), "Network initial focus");
            awaitText("network:status", "Snapshot refreshed.");
            String network = text("network:details");
            check(network.length() > 30 && !network.contains("Reading network details"), "Actual network details must render");
            focus("network:refresh");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            awaitText("network:status", "Snapshot refreshed.");
            check("network:refresh".equals(String.valueOf(screen.getCurrentFocus().getTag())), "Refresh must retain focus");
            back("Network and IP");
            openRoute("Diagnostics", "DiagnosticsActivity");
            awaitText("diagnostics:status", "Report ready.");
            String report = text("diagnostics:report");
            check(report.contains("Android API: ") && report.contains("App version: ")
                    && report.contains("Signer SHA-256: ") && report.contains("heap limit bytes:"), "Report must show actual app/runtime values");
            check(!report.contains("192.168.") && !report.contains("content://") && !report.contains("/data/"), "Report must omit network addresses and private paths");
            IntentFilter documentFilter = new IntentFilter(Intent.ACTION_CREATE_DOCUMENT);
            documentFilter.addCategory(Intent.CATEGORY_OPENABLE);
            documentFilter.addDataType("text/plain");
            ActivityMonitor documents = addMonitor(documentFilter,
                    new ActivityResult(Activity.RESULT_CANCELED, null), true);
            focus("diagnostics:export");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            settle();
            removeMonitor(documents);
            String status = text("diagnostics:status");
            check(status.contains("canceled") || status.contains("unavailable"), "Canceled/unsupported export must be explicit");
            check(report.equals(text("diagnostics:report")), "Canceled export must preserve report");
            back("Diagnostics");
            result.putString("status", "PASS");
            result.putBoolean("settings_routes_refresh_back_focus", true);
            result.putBoolean("actual_report_and_canceled_export", true);
            result.putString("limits", "No offline/VPN fixture, real document write, physical TV or network-settings handoff claim.");
        } catch (Throwable failure) {
            result.putString("status", "FAIL");
            result.putString("reason", failure.toString());
        } finally {
            if (screen != null && !screen.isFinishing()) main(new Runnable() { @Override public void run() { screen.finish(); } });
            if (settings != null) main(new Runnable() { @Override public void run() { settings.finish(); } });
        }
        finish(0, result);
    }

    private void openRoute(final String label, String suffix) {
        ActivityMonitor monitor = addMonitor(ROOT + "device.network." + suffix, null, false);
        main(new Runnable() { @Override public void run() {
            Button route = button(settings.getWindow().getDecorView(), label);
            check(route != null, "Settings route missing: " + label);
            route.requestFocus();
        }});
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        screen = waitForMonitorWithTimeout(monitor, 3000);
        removeMonitor(monitor);
        check(screen != null, "Settings must open " + label);
        settle();
    }
    private void back(String label) {
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        settle();
        check(screen.isFinishing(), "Back must finish child");
        check(settings.getCurrentFocus() instanceof Button && label.contentEquals(((Button)settings.getCurrentFocus()).getText()),
                "Back must restore settings opener focus");
    }
    private void awaitText(String tag, String expected) {
        for (int i = 0; i < 60; i++) {
            settle();
            if (text(tag).contains(expected)) return;
        }
        throw new AssertionError("Timed out waiting for " + expected);
    }
    private String text(final String tag) {
        final String[] value = {""};
        main(new Runnable() { @Override public void run() {
            value[0] = ((TextView)screen.getWindow().getDecorView().findViewWithTag(tag)).getText().toString();
        }});
        return value[0];
    }
    private void focus(final String tag) {
        main(new Runnable() { @Override public void run() {
            check(screen.getWindow().getDecorView().findViewWithTag(tag).requestFocus(), "Focusable " + tag);
        }});
    }
    private void main(final Runnable action) {
        final Throwable[] error = {null};
        runOnMainSync(new Runnable() { @Override public void run() { try { action.run(); } catch (Throwable failure) { error[0] = failure; } }});
        if (error[0] != null) throw new AssertionError(error[0]);
    }
    private void settle() { waitForIdleSync(); SystemClock.sleep(80); waitForIdleSync(); }
    private static Button button(View view, String label) {
        if (view instanceof Button && label.contentEquals(((Button)view).getText())) return (Button)view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) { Button found = button(group.getChildAt(i), label); if (found != null) return found; }
        }
        return null;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
