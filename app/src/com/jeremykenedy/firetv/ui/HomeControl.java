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
    private static final String[] CHOICES = {
        PREFERENCE, "start_on_boot", "override_home_button", "better_home_detection"
    };

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
        if (!DeviceSettings.prefs(context).edit().putBoolean(PREFERENCE, enabled)
                .putBoolean("start_on_boot", enabled)
                .putBoolean("override_home_button", enabled)
                .putBoolean("better_home_detection", true).commit()) {
            throw new IllegalStateException("The TV did not save Home preferences.");
        }
    }

    public static void rememberEnabledPreference(Context context, boolean enabled) {
        if (!DeviceSettings.prefs(context).edit().putBoolean(PREFERENCE, enabled).commit()) {
            throw new IllegalStateException("The TV did not save the Home preference.");
        }
    }

    public static void clearEnabledPreference(Context context) {
        if (!DeviceSettings.prefs(context).edit().remove(PREFERENCE).commit()) {
            throw new IllegalStateException("The TV did not clear the Home preference.");
        }
    }

    public static String enabledPreferenceState(Context context) {
        SharedPreferences prefs = DeviceSettings.prefs(context);
        return "home-preference=" + (!prefs.contains(PREFERENCE) ? "unset"
                : prefs.getBoolean(PREFERENCE, true) ? "on" : "off");
    }

    public static void setEnabled(Context context, boolean enabled, boolean rebind) {
        setEnabled(context, enabled, rebind, true);
    }

    public static void rebind(Context context) {
        if (wanted(context)) {
            setEnabled(context, true, true, false);
        }
    }

    private static void setEnabled(Context context, boolean enabled, boolean rebind,
            boolean rememberChoice) {
        String before = Settings.Secure.getString(context.getContentResolver(), SERVICES);
        int accessibility = Settings.Secure.getInt(context.getContentResolver(), ENABLED, 0);
        SharedPreferences prefs = DeviceSettings.prefs(context);
        boolean[] present = new boolean[CHOICES.length];
        boolean[] saved = new boolean[CHOICES.length];
        if (rememberChoice) {
            for (int i = 0; i < CHOICES.length; i++) {
                present[i] = prefs.contains(CHOICES[i]);
                if (present[i]) {
                    saved[i] = prefs.getBoolean(CHOICES[i], false);
                }
            }
        }
        List<String> others = services(before);
        others.remove(redirect(context));
        others.remove(controls(context));
        if (enabled) {
            others.remove("com.overdevs.at4k/com.overdevs.at4k.Hra");
            others.remove("com.jeremykenedy.firetv.homeredirect/com.jeremykenedy.firetv.homeredirect.HomeRedirectService");
            others.remove("com.overdevs.at4khelper/com.overdevs.at4khelper.HomeRedirectAccessibilityService");
        }
        try {
            if (rememberChoice) {
                remember(context, enabled);
            }
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
            if (rememberChoice) {
                SharedPreferences.Editor restore = prefs.edit();
                for (int i = 0; i < CHOICES.length; i++) {
                    if (present[i]) {
                        restore.putBoolean(CHOICES[i], saved[i]);
                    } else {
                        restore.remove(CHOICES[i]);
                    }
                }
                restore.commit();
            }
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
