package com.jeremykenedy.firetv.ui.nativeapp.device.commands;

import java.util.*;

public final class LegacySettingTransactionTest {
    public static void main(String[] args) {
        Store store = new Store(); Prefs prefs = new Prefs();
        LegacySettingTransaction.execute(store, prefs, "secure", "sleep_timeout", "1200000");
        check("1200000".equals(store.value) && Integer.valueOf(1200000).equals(prefs.values.get("fire_tv_ui_sleep_saved")));
        LegacySettingTransaction.execute(store, prefs, "secure", "str.auto_wake_up_enabled", "0");
        check(Boolean.TRUE.equals(prefs.values.get("fire_tv_ui_deep_sleep_enabled")));
        store.value = "600000"; prefs.failOnce = true;
        try { LegacySettingTransaction.execute(store, prefs, "secure", "sleep_timeout", "900000"); throw new AssertionError(); }
        catch (IllegalStateException expected) { check(expected.getMessage().contains("restored")); }
        check("600000".equals(store.value) && Integer.valueOf(1200000).equals(prefs.values.get("fire_tv_ui_sleep_saved")));
        store.value = "600000"; prefs.failOnce = true; prefs.onFailure = new Runnable() {
            @Override public void run() { store.value = "777000"; }
        };
        try { LegacySettingTransaction.execute(store, prefs, "secure", "sleep_timeout", "900000"); throw new AssertionError(); }
        catch (IllegalStateException expected) { check(expected.getMessage().contains("Review")); }
        check("777000".equals(store.value));
        store.permitted = false; int before = prefs.writes;
        try { LegacySettingTransaction.execute(store, prefs, "secure", "sleep_timeout", "100"); throw new AssertionError(); }
        catch (SecurityException expected) { check(before == prefs.writes); }
        System.out.println("Legacy setting commands preserve saved choices and rollback failed preference writes.");
    }
    static final class Store implements SettingCommandPolicy.Store {
        String value = "600000"; boolean permitted = true;
        public boolean permitted(String ns) { return permitted; }
        public String read(String ns,String key) { return value; }
        public boolean write(String ns,String key,String target) { value = target; return true; }
        public boolean componentAvailable(String value) { return true; }
    }
    static final class Prefs implements LegacySettingTransaction.Preferences {
        final Map<String,Object> values = new HashMap<>(); boolean failOnce; int writes; Runnable onFailure;
        public Map<String,?> read() { return new HashMap<>(values); }
        public boolean write(Map<String,Object> patch) {
            writes++; for (Map.Entry<String,Object> item : patch.entrySet()) {
                if (item.getValue() == null) values.remove(item.getKey()); else values.put(item.getKey(), item.getValue());
            }
            if (failOnce) { failOnce = false; if (onFailure != null) onFailure.run(); return false; } return true;
        }
    }
    static void check(boolean value) { if (!value) throw new AssertionError("Legacy setting transaction regression"); }
}
