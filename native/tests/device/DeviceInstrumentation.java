package com.jeremykenedy.firetv.ui.nativeapp.device;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.TextView;

/** Native route proof, run only by the exclusive target operator after integration. */
public final class DeviceInstrumentation extends Instrumentation {
    private static final String ROOT = "com.jeremykenedy.firetv.ui.nativeapp.";
    private Activity settings;
    private Activity device;
    private String destination;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        destination = args.getString("destination");
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (destination != null) {
                proveDestination(result);
                finish(0, result);
                return;
            }
            settings = startActivitySync(new Intent().setClassName(getTargetContext(), ROOT + "SettingsActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            waitForIdleSync();
            click(settings, "Device");
            ActivityMonitor monitor = addMonitor(ROOT + "device.DeviceSettingsActivity", null, false);
            click(settings, "Device screens");
            device = waitForMonitorWithTimeout(monitor, 3000);
            removeMonitor(monitor);
            check(device != null, "Device screens must open from the real Settings route");
            waitForActions();
            final Intent nativeSettings = DeviceRoutes.resolve(getTargetContext(), DeviceRoutes.Destination.SYSTEM_SETTINGS);
            check(nativeSettings != null, "Native Settings unavailable: cannot establish native route proof on this target");
            onMain(new Runnable() {
                @Override public void run() {
                    View action = device.getWindow().getDecorView().findViewWithTag("device:SYSTEM_SETTINGS");
                    check(action != null && action.requestFocus(), "System settings action must accept remote focus");
                }
            });
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            String targetPackage = nativeSettings.getComponent().getPackageName();
            check(waitForPackage(targetPackage, 6000), "Native Settings must visibly become the active external package");
            result.putString("native_settings_package", targetPackage);
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            check(waitForPackage(getTargetContext().getPackageName(), 4000), "Back must return to source Device screen");
            waitForActions();
            onMain(new Runnable() {
                @Override public void run() {
                    View action = device.getWindow().getDecorView().findViewWithTag("device:SYSTEM_SETTINGS");
                    check(action != null && action.hasFocus(), "Back must preserve System settings action focus");
                    for (DeviceRoutes.Destination destination : new DeviceRoutes.Destination[] {
                            DeviceRoutes.Destination.FACTORY_UI, DeviceRoutes.Destination.SYSTEM_SETTINGS,
                            DeviceRoutes.Destination.LIVE_TV}) {
                        TextView state = (TextView) device.getWindow().getDecorView()
                                .findViewWithTag("device:availability:" + destination.name());
                        boolean available = DeviceRoutes.resolve(device, destination) != null;
                        check((available ? "Available" : "Unavailable on this device").contentEquals(state.getText()),
                                "Each destination must expose its actual capability state");
                        if (!available) {
                            device.getWindow().getDecorView().findViewWithTag("device:" + destination.name()).performClick();
                            TextView status = (TextView) device.getWindow().getDecorView().findViewWithTag("device:status");
                            check(status.getText().toString().contains("unavailable"), "Unavailable action must explain its state");
                        }
                    }
                }
            });
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            waitForIdleSync();
            onMain(new Runnable() {
                @Override public void run() {
                    Button route = button(settings.getWindow().getDecorView(), "Device screens");
                    check(route != null && route.hasFocus(), "Back must restore the Device screens opener");
                }
            });
            result.putString("status", "PASS");
            result.putBoolean("actual_native_settings_window", true);
            result.putBoolean("back_and_focus", true);
            result.putBoolean("capability_states", true);
            result.putString("limits", "No Home takeover, Factory UI/Live TV launch, production key service or physical remote claim.");
        } catch (Throwable error) {
            result.putString("status", "FAIL");
            result.putString("reason", error.toString());
        }
        finish(0, result);
    }

    private void proveDestination(Bundle result) {
        final DeviceRoutes.Destination target = DeviceRoutes.Destination.valueOf(destination);
        check(target == DeviceRoutes.Destination.FACTORY_UI || target == DeviceRoutes.Destination.LIVE_TV,
                "Destination proof supports Factory UI or Live TV only");
        settings = startActivitySync(new Intent().setClassName(getTargetContext(), ROOT + "SettingsActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitForIdleSync();
        click(settings, "Device");
        ActivityMonitor monitor = addMonitor(ROOT + "device.DeviceSettingsActivity", null, false);
        click(settings, "Device screens");
        device = waitForMonitorWithTimeout(monitor, 3000);
        removeMonitor(monitor);
        check(device != null, "Device screens must open through Settings");
        waitForActions();
        final Intent nativeTarget = DeviceRoutes.resolve(getTargetContext(), target);
        onMain(new Runnable() {
            @Override public void run() {
                View action = device.getWindow().getDecorView().findViewWithTag("device:" + target.name());
                check(action != null && action.requestFocus(), "Destination must be remote-focusable");
            }
        });
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        result.putString("destination", target.name());
        if (nativeTarget == null) {
            onMain(new Runnable() {
                @Override public void run() {
                    TextView status = (TextView) device.getWindow().getDecorView().findViewWithTag("device:status");
                    check(status.getText().toString().contains("unavailable"), "Missing capability must be explained");
                }
            });
            result.putString("capability", "UNAVAILABLE");
            result.putBoolean("actual_native_window", false);
        } else {
            String pkg = nativeTarget.getComponent().getPackageName();
            check(waitForPackage(pkg, 6000), "Resolved native destination must become the actual active package");
            result.putString("capability", "AVAILABLE");
            result.putString("native_package", pkg);
            result.putString("native_component", nativeTarget.getComponent().flattenToShortString());
            result.putBoolean("actual_native_window", true);
            device = startActivitySync(new Intent().setClassName(getTargetContext(), ROOT + "device.DeviceSettingsActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            waitForActions();
            result.putString("return", "Explicit source route; native Factory UI Back behavior is not claimed");
        }
        result.putString("status", "PASS");
        result.putString("limits", "Native capability/window proof only; preview never owns Home and this does not validate Home interception or physical remotes.");
    }

    private boolean waitForPackage(String expected, long timeout) {
        long deadline = SystemClock.uptimeMillis() + timeout;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                boolean matches = expected.contentEquals(root.getPackageName() == null ? "" : root.getPackageName());
                root.recycle();
                if (matches) return true;
            }
            SystemClock.sleep(50);
        }
        return false;
    }

    private void waitForActions() {
        long deadline = SystemClock.uptimeMillis() + 5000;
        final boolean[] ready = new boolean[1];
        while (SystemClock.uptimeMillis() < deadline) {
            onMain(new Runnable() {
                @Override public void run() {
                    View action = device.getWindow().getDecorView().findViewWithTag("device:SYSTEM_SETTINGS");
                    ready[0] = action != null && action.isEnabled();
                }
            });
            if (ready[0]) { waitForIdleSync(); return; }
            SystemClock.sleep(25);
        }
        throw new AssertionError("Device capabilities did not load");
    }

    private void click(final Activity activity, final String label) {
        onMain(new Runnable() {
            @Override public void run() {
                Button action = button(activity.getWindow().getDecorView(), label);
                check(action != null, "Missing route: " + label);
                action.requestFocus();
            }
        });
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        waitForIdleSync();
    }

    private static Button button(View view, String label) {
        if (view instanceof Button && label.contentEquals(((Button) view).getText())) return (Button) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = button(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void onMain(final Runnable action) {
        final Throwable[] failure = new Throwable[1];
        runOnMainSync(new Runnable() {
            @Override public void run() { try { action.run(); } catch (Throwable error) { failure[0] = error; } }
        });
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
