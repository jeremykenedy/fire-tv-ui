package com.jeremykenedy.firetv.ui.nativeapp.device.commands;

import com.jeremykenedy.firetv.ui.nativeapp.device.protection.LegacyDeviceProtection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Keeps an explicit system-setting command and the saved guard choice together. */
public final class LegacySettingTransaction {
    public interface Preferences {
        Map<String, ?> read();
        boolean write(Map<String, Object> values);
    }
    private LegacySettingTransaction() {}
    public static String execute(SettingCommandPolicy.Store store, Preferences preferences,
            String namespace, String key, String value) {
        synchronized (LegacyDeviceProtection.LOCK) {
            String before = store.read(namespace, key);
            Map<String, ?> saved = preferences.read();
            String result = SettingCommandPolicy.execute(store, namespace, key, value);
            Map<String, Object> desired = values(key, value);
            Map<String, Object> previous = new LinkedHashMap<>();
            for (String name : desired.keySet()) previous.put(name, saved.containsKey(name) ? saved.get(name) : null);
            try { if (preferences.write(desired)) return result; }
            catch (RuntimeException failed) { }
            boolean recovered = false;
            try {
                String current = store.read(namespace, key);
                String wanted = "null".equals(value) && "screensaver_components".equals(key) ? null : value;
                boolean unchanged = Objects.equals(current, before);
                if (!unchanged && Objects.equals(current, wanted))
                    unchanged = store.write(namespace, key, before) && Objects.equals(store.read(namespace, key), before);
                boolean prefsRestored = preferences.write(previous);
                recovered = unchanged && prefsRestored;
            } catch (RuntimeException failure) { }
            throw new IllegalStateException(recovered ? "The saved setting could not be committed. Previous settings were restored."
                    : "The setting could not be committed or restored. Review TV settings before retrying.");
        }
    }
    private static Map<String, Object> values(String key, String value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if ("sleep_timeout".equals(key)) {
            result.put("fire_tv_ui_sleep", Integer.valueOf(value)); result.put("fire_tv_ui_sleep_saved", Integer.valueOf(value));
        } else if ("screen_off_timeout".equals(key)) result.put("fire_tv_ui_idle", Integer.valueOf(value));
        else if ("str.auto_wake_up_enabled".equals(key)) {
            result.put("fire_tv_ui_auto_wake", Integer.valueOf(value)); result.put("fire_tv_ui_deep_sleep_enabled", "0".equals(value));
        } else if ("screensaver_components".equals(key)) result.put("fire_tv_ui_dream", "null".equals(value) ? null : value);
        else if ("screensaver_enabled".equals(key)) result.put("fire_tv_ui_dream_enabled", "1".equals(value));
        return result;
    }
}
