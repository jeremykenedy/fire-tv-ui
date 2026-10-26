package com.jeremykenedy.firetv.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;

/** Opens reviewed app previews without changing the system screensaver selection. */
public final class ScreensaverPreview {
    public static final String PENDING = "fire_tv_ui_preview_pending";

    private ScreensaverPreview() {
    }

    public static void start(Context context, String component) {
        ComponentCheck.requireInstalledDream(context, component);
        restore(context);
        if (DeviceSettings.prefs(context).getBoolean(PENDING, false)) {
            throw new IllegalStateException("Restore the previous screensaver before starting another preview.");
        }
        ComponentName preview = appPreview(context, component);
        if (preview != null) {
            try {
                context.startActivity(new Intent().setComponent(preview));
                return;
            } catch (RuntimeException unavailable) {
                // An app update can remove its preview between resolution and launch.
            }
        }
        ComponentName starter = new ComponentName("com.android.systemui", "com.android.systemui.Somnambulator");
        if (!canLaunch(context, starter)) {
            throw new IllegalStateException("This screensaver does not expose a preview on this TV. Open its settings to check its preview options.");
        }
        if (!DeviceSettings.canWrite(context)) {
            throw new IllegalStateException("System screensaver previews require the existing screensaver settings access.");
        }
        SharedPreferences prefs = DeviceSettings.prefs(context);
        if (!prefs.edit().putString("fire_tv_ui_preview_previous", DeviceSettings.active(context.getContentResolver()))
                .putBoolean("fire_tv_ui_preview_enabled", DeviceSettings.isEnabled(context.getContentResolver()))
                .putBoolean(PENDING, true).commit()) {
            throw new IllegalStateException("Could not save the current selection before previewing.");
        }
        try {
            DeviceSettings.select(context.getContentResolver(), component);
            Intent intent = new Intent().setComponent(starter);
            context.startActivity(intent);
        } catch (RuntimeException failed) {
            restore(context);
            throw new IllegalStateException("This TV could not start a screensaver preview.", failed);
        }
    }

    private static ComponentName appPreview(Context context, String dream) {
        ComponentName selected = ComponentName.unflattenFromString(dream);
        try {
            for (ScreensaverCatalog.Entry entry : ScreensaverCatalog.load(context)) {
                if (!Screensavers.fullComponent(entry.dream).equals(Screensavers.fullComponent(dream))) {
                    continue;
                }
                ComponentName preview = ComponentName.unflattenFromString(entry.preview);
                if (preview != null && selected != null
                        && selected.getPackageName().equals(preview.getPackageName())
                        && canLaunch(context, preview)) {
                    return preview;
                }
            }
        } catch (Exception unavailable) {
            throw new IllegalStateException("The screensaver preview catalog could not be read.", unavailable);
        }
        return null;
    }

    private static boolean canLaunch(Context context, ComponentName component) {
        try {
            ActivityInfo activity = context.getPackageManager().getActivityInfo(component, 0);
            return activity.enabled && activity.exported && activity.applicationInfo.enabled
                    && (activity.permission == null || activity.permission.isEmpty()
                            || context.checkSelfPermission(activity.permission) == PackageManager.PERMISSION_GRANTED);
        } catch (PackageManager.NameNotFoundException | SecurityException unavailable) {
            return false;
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
        if (!prefs.edit().remove(PENDING).remove("fire_tv_ui_preview_previous")
                .remove("fire_tv_ui_preview_enabled").commit()) {
            throw new IllegalStateException("The restored screensaver could not be saved. Try returning again.");
        }
    }
}
