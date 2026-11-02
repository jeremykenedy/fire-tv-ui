package com.jeremykenedy.firetv.ui.nativeapp.device.protection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Plans saved legacy settings without reading or changing TV state. */
public final class LegacyDeviceProtection {
    public static final Object LOCK = new Object();
    public static final class Change {
        public final String namespace, key, value;
        Change(String namespace, String key, String value) {
            this.namespace = namespace; this.key = key; this.value = value;
        }
    }
    public interface Store {
        boolean writable();
        boolean fireTv();
        String read(String namespace, String key);
        boolean write(String namespace, String key, String value);
        boolean availableDream(String component);
    }
    public static final class Result {
        public final List<String> unavailable;
        Result(List<String> unavailable) { this.unavailable = Collections.unmodifiableList(unavailable); }
        public boolean complete() { return unavailable.isEmpty(); }
    }
    private LegacyDeviceProtection() {}

    public static boolean hasSavedChoices(Map<String, ?> prefs) {
        return Boolean.TRUE.equals(prefs.get("fire_tv_ui_protect_settings"))
                || prefs.containsKey("fire_tv_ui_sleep_saved") || prefs.containsKey("fire_tv_ui_sleep")
                || prefs.containsKey("fire_tv_ui_deep_sleep_enabled") || prefs.containsKey("fire_tv_ui_auto_wake")
                || Boolean.TRUE.equals(prefs.get("fire_tv_ui_keep_adb"));
    }

    public static List<Change> plan(Map<String, ?> prefs, boolean fireTv) {
        List<Change> changes = new ArrayList<>();
        Integer sleep = integer(prefs, "fire_tv_ui_sleep_saved");
        if (sleep == null && prefs.containsKey("fire_tv_ui_sleep")) {
            int prior = requiredInt(prefs, "fire_tv_ui_sleep");
            if (prior <= 0) throw invalid();
            sleep = prior;
        }
        if (sleep != null) {
            if (sleep < 0) throw invalid();
            changes.add(new Change("secure", "sleep_timeout", sleep.toString()));
        }
        Boolean deep = bool(prefs, "fire_tv_ui_deep_sleep_enabled");
        Integer auto = integer(prefs, "fire_tv_ui_auto_wake");
        if (auto != null && auto != 0 && auto != 1) throw invalid();
        if (deep != null || auto != null)
            changes.add(new Change("secure", "str.auto_wake_up_enabled", String.valueOf(deep != null ? (deep ? 0 : 1) : auto)));
        Boolean adb = bool(prefs, "fire_tv_ui_keep_adb");
        if (fireTv && Boolean.TRUE.equals(adb)) {
            for (String key : new String[]{"development_settings_enabled", "adb_enabled", "adb_wifi_enabled"})
                changes.add(new Change("global", key, "1"));
        }
        Boolean protect = bool(prefs, "fire_tv_ui_protect_settings");
        Boolean unlocked = bool(prefs, "fire_tv_ui_dream_unlocked");
        Boolean pending = bool(prefs, "fire_tv_ui_preview_pending");
        if (Boolean.TRUE.equals(protect) && !Boolean.TRUE.equals(pending)) {
            Object dream = prefs.get("fire_tv_ui_dream");
            if (dream != null && !(dream instanceof String)) throw invalid();
            if (dream instanceof String && !((String) dream).isEmpty() && !Boolean.TRUE.equals(unlocked))
                changes.add(new Change("secure", "screensaver_components", (String) dream));
            Boolean enabled = bool(prefs, "fire_tv_ui_dream_enabled");
            if (enabled != null) changes.add(new Change("secure", "screensaver_enabled", enabled ? "1" : "0"));
            Integer idle = integer(prefs, "fire_tv_ui_idle");
            if (idle != null) {
                if (idle <= 0) throw invalid();
                changes.add(new Change("system", "screen_off_timeout", idle.toString()));
            }
        }
        return Collections.unmodifiableList(changes);
    }

    /** Read-only upgrade reconciliation. A mismatch is never applied during startup. */
    public static Result inspect(Store store, Map<String, ?> prefs) {
        List<String> unavailable = new ArrayList<>();
        if (!store.writable()) { unavailable.add("permission"); return new Result(unavailable); }
        if (Boolean.TRUE.equals(prefs.get("fire_tv_ui_keep_adb")) && !store.fireTv()) unavailable.add("adb_wifi_enabled");
        try {
            List<Change> changes = plan(prefs, store.fireTv());
            for (Change change : changes) {
                if ("screensaver_components".equals(change.key) && !store.availableDream(change.value)) {
                    unavailable.add(change.key); continue;
                }
                if (!same(change, store.read(change.namespace, change.key))) unavailable.add(change.key);
            }
        } catch (RuntimeException unavailableState) { unavailable.add("saved settings"); }
        return new Result(unavailable);
    }

    /** Caller owns lifecycle and must run only after the production migration gate succeeds. */
    public static Result repair(Store store, Map<String, ?> prefs) {
        List<String> unavailable = new ArrayList<>();
        if (!store.writable()) { unavailable.add("permission"); return new Result(unavailable); }
        if (Boolean.TRUE.equals(prefs.get("fire_tv_ui_keep_adb")) && !store.fireTv()) unavailable.add("adb_wifi_enabled");
        final List<Change> changes;
        try { changes = plan(prefs, store.fireTv()); }
        catch (RuntimeException invalid) { unavailable.add("saved settings"); return new Result(unavailable); }
        for (Change change : changes) {
            try {
                if ("screensaver_components".equals(change.key) && !store.availableDream(change.value)) {
                    unavailable.add(change.key); continue;
                }
                String before = store.read(change.namespace, change.key);
                if (before == null && ("sleep_timeout".equals(change.key) || "str.auto_wake_up_enabled".equals(change.key))) {
                    unavailable.add(change.key); continue;
                }
                if (!same(change, before)
                        && (!store.write(change.namespace, change.key, change.value)
                        || !same(change, store.read(change.namespace, change.key)))) unavailable.add(change.key);
            } catch (RuntimeException failure) { unavailable.add(change.key); }
        }
        return new Result(unavailable);
    }
    private static boolean same(Change change, String actual) {
        if (!"screensaver_components".equals(change.key)) return Objects.equals(change.value, actual);
        return Objects.equals(component(change.value), component(actual));
    }
    private static String component(String value) {
        if (value == null) return null;
        int comma = value.indexOf(',');
        if (comma >= 0) value = value.substring(0, comma);
        int slash = value.indexOf('/');
        if (slash <= 0 || slash == value.length() - 1) return value;
        String pkg = value.substring(0, slash), name = value.substring(slash + 1);
        return pkg + "/" + (name.startsWith(".") ? pkg + name : name);
    }
    private static Boolean bool(Map<String, ?> prefs, String key) {
        Object value = prefs.get(key);
        if (value == null && !prefs.containsKey(key)) return null;
        if (!(value instanceof Boolean)) throw invalid();
        return (Boolean) value;
    }
    private static Integer integer(Map<String, ?> prefs, String key) {
        Object value = prefs.get(key);
        if (value == null && !prefs.containsKey(key)) return null;
        if (!(value instanceof Integer)) throw invalid();
        return (Integer) value;
    }
    private static int requiredInt(Map<String, ?> prefs, String key) { return integer(prefs, key); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid saved device setting."); }
}
