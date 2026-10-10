package com.jeremykenedy.firetv.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;

/** Avoids Fire OS's Settings trampoline through the Amazon home screen. */
public final class SystemSettings {
    private SystemSettings() {
    }

    public static void open(Context context) {
        ComponentName settings = new ComponentName("com.amazon.tv.launcher",
                "com.amazon.tv.launcher.ui.MainSettingsActivity");
        try {
            ActivityInfo activity = context.getPackageManager().getActivityInfo(settings, 0);
            if (activity.exported && activity.enabled && activity.applicationInfo.enabled
                    && (activity.permission == null || context.checkSelfPermission(activity.permission)
                        == PackageManager.PERMISSION_GRANTED)) {
                context.startActivity(new Intent().setComponent(settings));
                return;
            }
        } catch (PackageManager.NameNotFoundException | SecurityException unavailable) {
            // Other TV platforms use the standard Settings action.
        }
        context.startActivity(new Intent(Settings.ACTION_SETTINGS));
    }

}
