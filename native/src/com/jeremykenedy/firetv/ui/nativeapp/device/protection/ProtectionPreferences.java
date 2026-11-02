package com.jeremykenedy.firetv.ui.nativeapp.device.protection;

import android.content.Context;
import android.content.SharedPreferences;

public final class ProtectionPreferences {
    private static final String FILE = "native_device_protection";
    public static final String KEEP_LAUNCHER_AWAKE = "keep_launcher_awake";
    private ProtectionPreferences() {}
    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences(FILE, Context.MODE_PRIVATE); }
    public static boolean keepLauncherAwake(Context context) {
        return prefs(context).getBoolean(KEEP_LAUNCHER_AWAKE, false);
    }
    public static boolean keepLauncherAwake(Context context, boolean enabled) {
        return prefs(context).edit().putBoolean(KEEP_LAUNCHER_AWAKE, enabled).commit();
    }
}
