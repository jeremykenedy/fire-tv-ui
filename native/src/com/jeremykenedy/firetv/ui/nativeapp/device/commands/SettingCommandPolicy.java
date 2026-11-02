package com.jeremykenedy.firetv.ui.nativeapp.device.commands;

import java.util.Objects;

/** One allowlisted OS setting per explicit Toolkit command. No preference or permission changes. */
public final class SettingCommandPolicy {
    public interface Store {
        boolean permitted(String namespace);
        String read(String namespace, String key);
        boolean write(String namespace, String key, String value);
        boolean componentAvailable(String component);
    }
    private SettingCommandPolicy() {}

    public static synchronized String execute(Store store, String namespace, String key, String value) {
        String wanted = validate(namespace, key, value);
        if (!store.permitted(namespace)) throw new SecurityException("The app does not have permission to write this setting.");
        if ("screensaver_components".equals(key) && wanted != null && !store.componentAvailable(wanted)) {
            throw new IllegalArgumentException("The requested screensaver is not an available DreamService.");
        }
        String before = store.read(namespace, key);
        if (before == null && ("sleep_timeout".equals(key) || "str.auto_wake_up_enabled".equals(key))) {
            throw new IllegalStateException("This TV does not expose the requested setting.");
        }
        if (Objects.equals(before, wanted)) return "ok";
        RuntimeException failure = null;
        try {
            if (store.write(namespace, key, wanted) && Objects.equals(wanted, store.read(namespace, key))) return "ok";
        } catch (RuntimeException denied) { failure = denied; }
        final String current;
        try { current = store.read(namespace, key); }
        catch (RuntimeException unavailable) {
            throw new IllegalStateException("The setting could not be read after failure. Recovery requires review.", unavailable);
        }
        if (!Objects.equals(before, current)) {
            if (!Objects.equals(wanted, current)) {
                throw new IllegalStateException("The setting changed during verification. The current value was preserved; recovery requires review.");
            }
            try {
                if (!store.write(namespace, key, before) || !Objects.equals(before, store.read(namespace, key))) {
                    throw new IllegalStateException("The previous setting could not be restored.");
                }
            } catch (RuntimeException unavailable) {
                throw new IllegalStateException("The previous setting could not be restored. Recovery requires review.", unavailable);
            }
        }
        if (failure instanceof SecurityException) {
            throw new SecurityException("The platform denied this setting. The previous value was retained.", failure);
        }
        throw new IllegalStateException("The TV did not save the setting. The previous value was retained.", failure);
    }

    private static String validate(String namespace, String key, String value) {
        boolean secure = "secure".equals(namespace);
        boolean sleep = secure && "sleep_timeout".equals(key);
        boolean idle = "system".equals(namespace) && "screen_off_timeout".equals(key);
        boolean flag = secure && ("str.auto_wake_up_enabled".equals(key) || "screensaver_enabled".equals(key));
        boolean component = secure && "screensaver_components".equals(key);
        if (!sleep && !idle && !flag && !component) throw new IllegalArgumentException("This setting is not supported by the source command bridge.");
        if (value == null) throw new IllegalArgumentException("A setting value is required.");
        if (sleep || idle) {
            if (!value.matches("0|[1-9][0-9]{0,9}")) throw new IllegalArgumentException("Use a whole millisecond value within the signed integer range.");
            final int milliseconds;
            try { milliseconds = Integer.parseInt(value); }
            catch (NumberFormatException outsideRange) { throw new IllegalArgumentException("The timeout exceeds the signed integer range."); }
            if (idle && milliseconds == 0) throw new IllegalArgumentException("Choose a positive screensaver delay or explicitly turn the screensaver off.");
        } else if (flag) {
            if (!"0".equals(value) && !"1".equals(value)) throw new IllegalArgumentException("Use 0 or 1 for this setting.");
        } else {
            if ("null".equals(value)) return null;
            int slash = value.indexOf('/');
            if (value.length() > 2048 || slash <= 0 || slash == value.length() - 1
                    || slash != value.lastIndexOf('/')
                    || !value.substring(0, slash).matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*")
                    || !value.substring(slash + 1).matches("\\.?[A-Za-z0-9_$]+(?:\\.[A-Za-z0-9_$]+)*")) {
                throw new IllegalArgumentException("Choose one valid installed screensaver component.");
            }
        }
        return value;
    }
}
