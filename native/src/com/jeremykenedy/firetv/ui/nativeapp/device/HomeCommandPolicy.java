package com.jeremykenedy.firetv.ui.nativeapp.device;

import java.util.LinkedHashMap;
import java.util.Map;

/** Ordered CLI Home operations, with exact preference absence and recoverable private writes. */
public final class HomeCommandPolicy {
    public interface Store {
        Map<String, ?> preferences();
        boolean write(Map<String, Boolean> changes);
        boolean routeMatches(boolean enabled);
        Map<String, Boolean> pending();
        boolean journal(Map<String, Boolean> before);
        boolean clearJournal();
    }
    private HomeCommandPolicy() {}

    public static synchronized void recover(Store store) {
        Map<String, Boolean> pending = store.pending();
        if (pending != null && (!store.write(pending) || !store.clearJournal())) {
            throw new IllegalStateException("Interrupted Home settings could not be restored.");
        }
    }

    public static synchronized String execute(Store store, String operation) {
        boolean state = "home-preference-state".equals(operation);
        boolean full = "home-on".equals(operation) || "home-off".equals(operation);
        boolean preference = "home-preference-on".equals(operation) || "home-preference-off".equals(operation)
                || "home-preference-clear".equals(operation);
        if (!state && !full && !preference) throw new IllegalArgumentException("Unsupported Home operation.");
        recover(store);
        if (state) {
            Map<String, ?> values = store.preferences();
            if (!values.containsKey(HomeSetupPolicy.LEGACY_HOME)) return "home-preference=unset";
            Object value = values.get(HomeSetupPolicy.LEGACY_HOME);
            if (!(value instanceof Boolean)) throw new IllegalStateException("The Home preference has an invalid type.");
            return "home-preference=" + ((Boolean) value ? "on" : "off");
        }
        boolean enable = "home-on".equals(operation) || "home-preference-on".equals(operation);
        if (full && !store.routeMatches(enable)) throw new IllegalStateException("The requested Home route is not selected.");
        Map<String, Boolean> changes = new LinkedHashMap<>();
        changes.put(HomeSetupPolicy.LEGACY_HOME, "home-preference-clear".equals(operation) ? null : enable);
        if (full) {
            changes.put("start_on_boot", enable);
            changes.put("override_home_button", enable);
            changes.put("better_home_detection", true);
            changes.put(HomeSetupPolicy.HOME_KEYS, enable);
            changes.put(HomeSetupPolicy.HOME_WINDOWS, enable);
        }
        Map<String, Boolean> before = new LinkedHashMap<>();
        Map<String, ?> current = store.preferences();
        for (String key : changes.keySet()) {
            Object value = current.get(key);
            if (current.containsKey(key) && !(value instanceof Boolean)) {
                throw new IllegalStateException("A Home setting has an invalid type.");
            }
            before.put(key, (Boolean) value);
        }
        if (!store.journal(before)) throw new IllegalStateException("Home recovery state could not be saved.");
        try {
            if (!store.write(changes)) throw new IllegalStateException("Home settings could not be saved.");
            if (full && !store.routeMatches(enable)) throw new IllegalStateException("The Home route changed before commit.");
            if (!store.clearJournal()) throw new IllegalStateException("Home recovery state could not be completed.");
        } catch (RuntimeException failed) {
            if (!store.write(before) || !store.clearJournal()) {
                throw new IllegalStateException("Home settings could not be restored; recovery remains pending.", failed);
            }
            throw failed;
        }
        return "ok";
    }
}
