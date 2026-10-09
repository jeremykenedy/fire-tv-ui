package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Rebinds Home routing after an APK update while preserving other services. */
public final class HomeControl {
    public static final String PREFERENCE = "fire_tv_ui_home_enabled";
    private static final String SERVICES = "enabled_accessibility_services";
    private static final String ENABLED = "accessibility_enabled";

    private HomeControl() {
    }

    public static List<String> services(String value) {
        return new ArrayList<>(value == null || "null".equals(value) || value.isEmpty()
                ? new ArrayList<String>() : Arrays.asList(value.split(":")));
    }

    private static String redirect(Context context) {
        return context.getPackageName() + "/" + HomeRedirectService.class.getName();
    }

    private static String controls(Context context) {
        return context.getPackageName() + "/" + context.getPackageName() + ".Hra";
    }

    public static boolean isEnabled(Context context) {
        List<String> list = services(Settings.Secure.getString(context.getContentResolver(), SERVICES));
        return list.contains(redirect(context)) && list.contains(controls(context))
                && Settings.Secure.getInt(context.getContentResolver(), ENABLED, 0) == 1;
    }

    public static boolean wanted(Context context) {
        return DeviceSettings.prefs(context).getBoolean(PREFERENCE, true);
    }

    public static void remember(Context context, boolean enabled) {
        DeviceSettings.prefs(context).edit().putBoolean(PREFERENCE, enabled)
                .putBoolean("start_on_boot", enabled)
                .putBoolean("override_home_button", enabled)
                .putBoolean("better_home_detection", true).commit();
    }

    public static void setEnabled(Context context, boolean enabled, boolean rebind) {
        String before = Settings.Secure.getString(context.getContentResolver(), SERVICES);
        int accessibility = Settings.Secure.getInt(context.getContentResolver(), ENABLED, 0);
        boolean wantedBefore = wanted(context);
        List<String> others = services(before);
        others.remove(redirect(context));
        others.remove(controls(context));
        if (enabled) {
            others.remove("com.overdevs.at4k/com.overdevs.at4k.Hra");
            others.remove("com.jeremykenedy.firetv.homeredirect/com.jeremykenedy.firetv.homeredirect.HomeRedirectService");
            others.remove("com.overdevs.at4khelper/com.overdevs.at4khelper.HomeRedirectAccessibilityService");
        }
        remember(context, enabled);
        try {
            if (enabled && rebind) {
                write(context, others);
            }
            if (enabled) {
                others.add(controls(context));
                others.add(redirect(context));
            }
            write(context, others);
            if (isEnabled(context) != enabled) {
                throw new IllegalStateException("The TV did not save Home routing.");
            }
        } catch (RuntimeException error) {
            remember(context, wantedBefore);
            Settings.Secure.putString(context.getContentResolver(), SERVICES, before);
            Settings.Secure.putInt(context.getContentResolver(), ENABLED, accessibility);
            throw error;
        }
    }

    private static void write(Context context, List<String> list) {
        String value = android.text.TextUtils.join(":", list);
        if (!Settings.Secure.putString(context.getContentResolver(), SERVICES, value)
                || !Settings.Secure.putInt(context.getContentResolver(), ENABLED, list.isEmpty() ? 0 : 1)
                || !value.equals(Settings.Secure.getString(context.getContentResolver(), SERVICES))) {
            throw new IllegalStateException("The TV did not save accessibility services.");
        }
    }
}
