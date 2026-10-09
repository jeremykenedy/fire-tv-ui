package com.jeremykenedy.firetv.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

/** Reads back changes before the UI reports that they succeeded. */
public final class DeviceSettings {
    public static final String COMPONENTS = "screensaver_components";
    public static final String ENABLED = "screensaver_enabled";
    public static final String PROTECT = "fire_tv_ui_protect_settings";
    public static final String DREAM_UNLOCKED = "fire_tv_ui_dream_unlocked";
    public static final String SLEEP = "sleep_timeout";
    public static final String IDLE = "screen_off_timeout";
    public static final String AUTO_WAKE = "str.auto_wake_up_enabled";
    public static final String KEEP_ADB = "fire_tv_ui_keep_adb";
    public static final String WIFI_ADB = "adb_wifi_enabled";

    private DeviceSettings() {
    }

    public static void initialize(Context context) {
        SharedPreferences prefs = prefs(context);
        SharedPreferences.Editor editor = prefs.edit().putBoolean("is_premium", true);
        editor.remove("app_launch_count").remove("rate_app_shown");
        // The TV's screensaver is the default. The launcher clock remains
        // available in Appearance for people who prefer that option.
        if (!prefs.contains("screensaver_enabled")) {
            editor.putBoolean("screensaver_enabled", false);
        }
        editor.apply();
        if (!prefs.contains(KEEP_ADB) && hasWifiAdb(context)) {
            prefs.edit().putBoolean(KEEP_ADB,
                    Settings.Global.getInt(context.getContentResolver(), WIFI_ADB, 0) == 1).commit();
        }
        ScreensaverPreview.restore(context);
        if (!prefs.contains(HomeControl.PREFERENCE)) {
            HomeControl.remember(context, HomeControl.isEnabled(context));
        }
        if (!prefs.contains(PROTECT)) {
            capture(context);
            prefs.edit().putBoolean(PROTECT, true).commit();
        }
        if (!prefs.contains("fire_tv_ui_auto_wake") && hasAutoWake(context)) {
            prefs.edit().putInt("fire_tv_ui_auto_wake", autoWake(context) ? 1 : 0).commit();
        }
        if (canWrite(context)) {
            try {
                ProtectionService.start(context);
                repair(context);
            } catch (RuntimeException ignored) {
                // Controls display the read-back state and can retry setup.
            }
        }
    }

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("launcher_prefs", 0);
    }

    public static boolean canWrite(Context context) {
        return context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasWifiAdb(Context context) {
        return context.getPackageManager().hasSystemFeature("amazon.hardware.fire_tv")
                && Settings.Global.getString(context.getContentResolver(), WIFI_ADB) != null;
    }

    public static void keepAdb(Context context, boolean enabled) {
        if (!hasWifiAdb(context)) {
            throw new IllegalStateException("This TV does not expose Fire TV network debugging.");
        }
        boolean before = prefs(context).getBoolean(KEEP_ADB, false);
        prefs(context).edit().putBoolean(KEEP_ADB, enabled).commit();
        try {
            if (enabled) {
                repairAdb(context);
            }
        } catch (RuntimeException failed) {
            prefs(context).edit().putBoolean(KEEP_ADB, before).commit();
            throw failed;
        }
    }

    private static void repairAdb(Context context) {
        if (!prefs(context).getBoolean(KEEP_ADB, false) || !hasWifiAdb(context)) {
            return;
        }
        // Fire TV's own network-debugging switch writes both global flags.
        for (String key : new String[] {Settings.Global.ADB_ENABLED, WIFI_ADB}) {
            if (Settings.Global.getInt(context.getContentResolver(), key, 0) != 1
                    && (!Settings.Global.putInt(context.getContentResolver(), key, 1)
                    || Settings.Global.getInt(context.getContentResolver(), key, 0) != 1)) {
                throw new IllegalStateException("The TV did not keep ADB debugging enabled.");
            }
        }
    }

    public static void capture(Context context) {
        SharedPreferences.Editor editor = prefs(context).edit()
                .putBoolean(DREAM_UNLOCKED, false)
                .putString("fire_tv_ui_dream", active(context.getContentResolver()))
                .putBoolean("fire_tv_ui_dream_enabled", isEnabled(context.getContentResolver()))
                .putInt("fire_tv_ui_sleep", sleep(context)).putInt("fire_tv_ui_idle", idle(context));
        if (hasAutoWake(context)) {
            editor.putInt("fire_tv_ui_auto_wake", autoWake(context) ? 1 : 0);
        }
        editor.commit();
    }

    public static int sleep(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(), SLEEP, 1200000);
    }

    public static int idle(Context context) {
        return Settings.System.getInt(context.getContentResolver(), IDLE, 300000);
    }

    public static boolean hasAutoWake(Context context) {
        return Settings.Secure.getString(context.getContentResolver(), AUTO_WAKE) != null;
    }

    public static boolean autoWake(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(), AUTO_WAKE, 0) == 1;
    }

    public static void autoWake(Context context, boolean enabled) {
        if (!hasAutoWake(context)) {
            throw new IllegalStateException("This TV does not expose the Alexa deep-sleep fix.");
        }
        writeNumber(context, true, AUTO_WAKE, "fire_tv_ui_auto_wake", autoWake(context) ? 1 : 0, enabled ? 1 : 0);
    }

    public static void sleep(Context context, int milliseconds) {
        int previous = sleep(context);
        writeTimer(context, true, milliseconds);
        if (milliseconds > 0 || previous > 0) {
            prefs(context).edit().putInt("fire_tv_ui_sleep_saved", milliseconds > 0 ? milliseconds : previous).commit();
        }
    }

    public static void idle(Context context, int milliseconds) {
        if (milliseconds <= 0) {
            throw new IllegalArgumentException("Choose a positive screensaver delay, or turn the screensaver off.");
        }
        writeTimer(context, false, milliseconds);
    }

    private static void writeTimer(Context context, boolean sleep, int milliseconds) {
        if (milliseconds < 0) {
            throw new IllegalArgumentException("The timer cannot be negative.");
        }
        String key = sleep ? "fire_tv_ui_sleep" : "fire_tv_ui_idle";
        int before = sleep ? sleep(context) : idle(context);
        writeNumber(context, sleep, sleep ? SLEEP : IDLE, key, before, milliseconds);
    }

    private static void writeNumber(Context context, boolean secure, String setting, String preference,
            int before, int value) {
        prefs(context).edit().putInt(preference, value).commit();
        try {
            boolean saved = secure
                    ? Settings.Secure.putInt(context.getContentResolver(), setting, value)
                    : Settings.System.putInt(context.getContentResolver(), setting, value);
            int readBack = secure ? Settings.Secure.getInt(context.getContentResolver(), setting, before)
                    : Settings.System.getInt(context.getContentResolver(), setting, before);
            if (!saved || readBack != value) {
                throw new IllegalStateException("The TV did not save the setting.");
            }
        } catch (RuntimeException failed) {
            prefs(context).edit().putInt(preference, before).commit();
            try {
                if (secure) {
                    Settings.Secure.putInt(context.getContentResolver(), setting, before);
                } else {
                    Settings.System.putInt(context.getContentResolver(), setting, before);
                }
            } catch (RuntimeException ignored) {
                // Report the original rejection if rollback is also denied.
            }
            throw failed;
        }
    }

    public static void component(Context context, String component) {
        if (component != null) {
            ComponentCheck.requireInstalledDream(context, component);
        }
        String before = active(context.getContentResolver());
        prefs(context).edit().putString("fire_tv_ui_dream", component).commit();
        try {
            if (!Settings.Secure.putString(context.getContentResolver(), COMPONENTS, component)
                    || !Screensavers.fullComponent(component).equals(
                            Screensavers.fullComponent(active(context.getContentResolver())))) {
                throw new IllegalStateException("The TV did not save the selected screensaver.");
            }
        } catch (RuntimeException failed) {
            prefs(context).edit().putString("fire_tv_ui_dream", before).commit();
            try {
                Settings.Secure.putString(context.getContentResolver(), COMPONENTS, before);
            } catch (RuntimeException ignored) {
                // Report the original rejection if rollback is also denied.
            }
            throw failed;
        }
    }

    public static void choose(Context context, String component) {
        ComponentCheck.requireInstalledDream(context, component);
        select(context.getContentResolver(), component);
        prefs(context).edit().putString("fire_tv_ui_dream", component)
                .putBoolean("fire_tv_ui_dream_enabled", true).commit();
    }

    public static void enableDream(Context context, boolean enabled) {
        setEnabled(context.getContentResolver(), enabled);
        prefs(context).edit().putBoolean("fire_tv_ui_dream_enabled", enabled).commit();
    }

    public static void repair(Context context) {
        SharedPreferences prefs = prefs(context);
        if (!canWrite(context) || !prefs.getBoolean(PROTECT, false)) {
            return;
        }
        repairAdb(context);
        if (prefs.getBoolean(HomeControl.PREFERENCE, false) && !HomeControl.isEnabled(context)) {
            HomeControl.setEnabled(context, true, true);
        }
        if (prefs.contains("fire_tv_ui_auto_wake") && hasAutoWake(context)) {
            int wanted = prefs.getInt("fire_tv_ui_auto_wake", autoWake(context) ? 1 : 0);
            if ((autoWake(context) ? 1 : 0) != wanted) {
                Settings.Secure.putInt(context.getContentResolver(), AUTO_WAKE, wanted);
            }
        }
        if (prefs.getBoolean(ScreensaverPreview.PENDING, false)) {
            return;
        }
        String dream = prefs.getString("fire_tv_ui_dream", null);
        if (dream != null && !prefs.getBoolean(DREAM_UNLOCKED, false)) {
            try {
                ComponentCheck.requireInstalledDream(context, dream);
                if (!Screensavers.fullComponent(dream)
                        .equals(Screensavers.fullComponent(active(context.getContentResolver())))) {
                    Settings.Secure.putString(context.getContentResolver(), COMPONENTS, dream);
                }
            } catch (IllegalStateException | IllegalArgumentException removed) {
                prefs.edit().remove("fire_tv_ui_dream").putBoolean("fire_tv_ui_dream_enabled", false).commit();
            }
        }
        boolean enabled = prefs.getBoolean("fire_tv_ui_dream_enabled", isEnabled(context.getContentResolver()));
        if (isEnabled(context.getContentResolver()) != enabled) {
            setEnabled(context.getContentResolver(), enabled);
        }
        int sleep = prefs.getInt("fire_tv_ui_sleep", sleep(context));
        int idle = prefs.getInt("fire_tv_ui_idle", idle(context));
        if (sleep(context) != sleep) {
            Settings.Secure.putInt(context.getContentResolver(), SLEEP, sleep);
        }
        if (idle(context) != idle) {
            Settings.System.putInt(context.getContentResolver(), IDLE, idle);
        }
    }

    public static boolean isEnabled(ContentResolver resolver) {
        return Settings.Secure.getInt(resolver, ENABLED, 0) == 1;
    }

    public static String active(ContentResolver resolver) {
        return Settings.Secure.getString(resolver, COMPONENTS);
    }

    public static void setEnabled(ContentResolver resolver, boolean enabled) {
        if (!Settings.Secure.putInt(resolver, ENABLED, enabled ? 1 : 0)
                || isEnabled(resolver) != enabled) {
            throw new IllegalStateException("The TV did not save the screensaver setting.");
        }
    }

    public static void select(ContentResolver resolver, String component) {
        String previous = active(resolver);
        boolean wasEnabled = isEnabled(resolver);
        try {
            if (!Settings.Secure.putString(resolver, COMPONENTS, component)
                    || !Screensavers.fullComponent(component)
                            .equals(Screensavers.fullComponent(active(resolver)))) {
                throw new IllegalStateException("The TV did not save the selected screensaver.");
            }
            setEnabled(resolver, true);
        } catch (SecurityException | IllegalStateException error) {
            // Keep the prior selection if only part of the change succeeded.
            try {
                Settings.Secure.putString(resolver, COMPONENTS, previous);
                Settings.Secure.putInt(resolver, ENABLED, wasEnabled ? 1 : 0);
            } catch (SecurityException ignored) {
                // The permission error is reported by the caller.
            }
            throw error;
        }
    }
}
