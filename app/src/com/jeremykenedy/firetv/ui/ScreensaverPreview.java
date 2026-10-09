package com.jeremykenedy.firetv.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;

/** Uses the system's exported dream starter, then restores the real selection. */
public final class ScreensaverPreview {
    public static final String PENDING = "fire_tv_ui_preview_pending";

    private ScreensaverPreview() {
    }

    public static void start(Context context, String component) {
        ComponentCheck.requireInstalledDream(context, component);
        restore(context);
        SharedPreferences prefs = DeviceSettings.prefs(context);
        if (!prefs.edit().putString("fire_tv_ui_preview_previous", DeviceSettings.active(context.getContentResolver()))
                .putBoolean("fire_tv_ui_preview_enabled", DeviceSettings.isEnabled(context.getContentResolver()))
                .putBoolean(PENDING, true).commit()) {
            throw new IllegalStateException("Could not save the current selection before previewing.");
        }
        try {
            DeviceSettings.select(context.getContentResolver(), component);
            Intent intent = new Intent().setComponent(new ComponentName("com.android.systemui",
                    "com.android.systemui.Somnambulator"));
            context.startActivity(intent);
        } catch (RuntimeException failed) {
            restore(context);
            throw new IllegalStateException("This TV could not start a screensaver preview.", failed);
        }
    }

    public static void restore(Context context) {
        SharedPreferences prefs = DeviceSettings.prefs(context);
        if (!prefs.getBoolean(PENDING, false) || !DeviceSettings.canWrite(context)) {
            return;
        }
        String previous = prefs.getString("fire_tv_ui_preview_previous", null);
        boolean enabled = prefs.getBoolean("fire_tv_ui_preview_enabled", false);
        if (!Settings.Secure.putString(context.getContentResolver(), DeviceSettings.COMPONENTS, previous)
                || !Screensavers.fullComponent(previous).equals(
                        Screensavers.fullComponent(DeviceSettings.active(context.getContentResolver())))) {
            throw new IllegalStateException("The TV did not restore your screensaver after previewing.");
        }
        DeviceSettings.setEnabled(context.getContentResolver(), enabled);
        prefs.edit().remove(PENDING).remove("fire_tv_ui_preview_previous")
                .remove("fire_tv_ui_preview_enabled").commit();
    }
}
