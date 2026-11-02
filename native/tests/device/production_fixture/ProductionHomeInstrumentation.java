package com.jeremykenedy.firetv.ui.homeproof;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import com.jeremykenedy.firetv.ui.nativeapp.device.HomeSetup;

/** Dedicated emulator only. Configures through app APIs and uses source UI routes; no secure writes. */
public final class ProductionHomeInstrumentation extends Instrumentation {
    private static final String ROOT = "com.jeremykenedy.firetv.ui.nativeapp.";
    private String phase;
    @Override public void onCreate(Bundle args) { super.onCreate(args); phase = args.getString("phase", "status"); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if ("keys".equals(phase) || "windows".equals(phase) || "both".equals(phase) || "off".equals(phase)) {
                boolean keys = "keys".equals(phase) || "both".equals(phase);
                boolean windows = "windows".equals(phase) || "both".equals(phase);
                check(HomeSetup.saveChoices(getTargetContext(), keys, windows), "Explicit test mode must save through HomeSetup");
            } else if ("factory".equals(phase) || "settings".equals(phase) || "homeui".equals(phase)) {
                Activity main = startActivitySync(new Intent().setClassName(getTargetContext(), ROOT + "MainActivity")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                waitForIdleSync();
                ActivityMonitor settingsMonitor = addMonitor(ROOT + "SettingsActivity", null, false);
                click(main, "Settings");
                Activity settings = waitForMonitorWithTimeout(settingsMonitor, 4000);
                removeMonitor(settingsMonitor);
                check(settings != null, "Source Settings must open");
                click(settings, "Device");
                ActivityMonitor deviceMonitor = addMonitor(ROOT + "device.DeviceSettingsActivity", null, false);
                click(settings, "Device screens");
                Activity device = waitForMonitorWithTimeout(deviceMonitor, 4000);
                removeMonitor(deviceMonitor);
                check(device != null, "Source Device route must open");
                long end = android.os.SystemClock.uptimeMillis() + 5000;
                while (android.os.SystemClock.uptimeMillis() < end) {
                    final boolean[] ready = new boolean[1];
                    final Activity screen = device;
                    runOnMainSync(new Runnable() {
                        @Override public void run() {
                            View action = screen.getWindow().getDecorView().findViewWithTag("device:SYSTEM_SETTINGS");
                            ready[0] = action != null && action.isEnabled();
                        }
                    });
                    if (ready[0]) break;
                    android.os.SystemClock.sleep(25);
                }
                if ("homeui".equals(phase)) {
                    HomeSetup before = HomeSetup.read(getTargetContext());
                    java.util.Map<String, ?> preferences = getTargetContext().getSharedPreferences("launcher_prefs", 0).getAll();
                    Object boot = preferences.get("start_on_boot");
                    click(device, "Home key routing: " + (before.choices.keys ? "On" : "Off"));
                    check(HomeSetup.read(getTargetContext()).choices.keys != before.choices.keys, "Native key control saves its choice");
                    click(device, "Home key routing: " + (before.choices.keys ? "Off" : "On"));
                    click(device, "Home window fallback: " + (before.choices.windows ? "On" : "Off"));
                    check(HomeSetup.read(getTargetContext()).choices.windows != before.choices.windows, "Native window control saves its choice");
                    click(device, "Home window fallback: " + (before.choices.windows ? "Off" : "On"));
                    HomeSetup after = HomeSetup.read(getTargetContext());
                    check(after.choices.keys == before.choices.keys && after.choices.windows == before.choices.windows,
                            "Native mode controls restore the starting choices");
                    Object afterBoot = getTargetContext().getSharedPreferences("launcher_prefs", 0).getAll().get("start_on_boot");
                    check(boot == null ? afterBoot == null : boot.equals(afterBoot), "Native mode controls preserve boot choice and absence");
                    result.putBoolean("native_home_controls", true);
                } else click(device, "factory".equals(phase) ? "Factory UI" : "System settings");
                result.putString("route", "Source Settings -> Device -> Device screens -> " + phase);
            } else if (!"status".equals(phase)) throw new IllegalArgumentException("Unknown proof phase");
            HomeSetup setup = HomeSetup.read(getTargetContext());
            result.putString("setup_state", setup.state.name());
            result.putBoolean("keys", setup.choices.keys);
            result.putBoolean("windows", setup.choices.windows);
            result.putBoolean("service_enabled", setup.serviceEnabled);
            result.putBoolean("key_capability", setup.keyCapability);
            result.putString("phase", phase);
            result.putString("status", "PASS");
        } catch (Throwable failed) {
            result.putString("status", "FAIL"); result.putString("reason", failed.toString());
        }
        finish(0, result);
    }
    private void click(final Activity activity, final String text) {
        final boolean[] found = new boolean[1];
        runOnMainSync(new Runnable() {
            @Override public void run() {
                Button action = button(activity.getWindow().getDecorView(), text);
                found[0] = action != null && action.isEnabled() && action.requestFocus();
            }
        });
        check(found[0], "Missing focusable source action: " + text);
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER); waitForIdleSync();
    }
    private static Button button(View view, String label) {
        if (view instanceof Button && label.contentEquals(((Button) view).getText())) return (Button) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            Button found = button(((ViewGroup) view).getChildAt(i), label);
            if (found != null) return found;
        }
        return null;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
