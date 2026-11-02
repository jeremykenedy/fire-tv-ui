package com.jeremykenedy.firetv.ui.nativeapp.device.commands;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SettingCommandPolicyTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static RuntimeException failure(Store store, String namespace, String key, String value) {
        try { SettingCommandPolicy.execute(store, namespace, key, value); throw new AssertionError("Failure expected"); }
        catch (RuntimeException expected) { return expected; }
    }
    public static void main(String[] args) {
        Store store = new Store();
        store.values.put("secure/sleep_timeout", "1200000");
        store.values.put("secure/str.auto_wake_up_enabled", "0");
        store.values.put("owner/other", "preserved");
        for (String[] command : new String[][] {
            {"secure", "sleep_timeout", "0"}, {"secure", "sleep_timeout", "2147483647"},
            {"system", "screen_off_timeout", "300000"}, {"secure", "str.auto_wake_up_enabled", "1"},
            {"secure", "screensaver_enabled", "0"}, {"secure", "screensaver_components", "example.saver/.Dream"}
        }) {
            check("ok".equals(SettingCommandPolicy.execute(store, command[0], command[1], command[2])), "Exact success response");
            check(command[2].equals(store.values.get(command[0] + "/" + command[1])), "Only the requested value is saved");
        }
        check("preserved".equals(store.values.get("owner/other")), "Unrelated settings survive");
        check("0".equals(store.values.get("secure/screensaver_enabled")), "Selecting a component does not enable screensavers");
        int unchangedWrites = store.writes;
        SettingCommandPolicy.execute(store, "secure", "sleep_timeout", "2147483647");
        check(unchangedWrites == store.writes, "An already-saved setting is verified without another write");
        SettingCommandPolicy.execute(store, "secure", "screensaver_components", "null");
        check(!store.values.containsKey("secure/screensaver_components"), "Literal null clears only components");
        Map<String, String> before = new LinkedHashMap<>(store.values);
        int reads = store.reads, writes = store.writes;
        for (String[] command : new String[][] {
            {"global", "sleep_timeout", "0"}, {"secure", "screen_off_timeout", "300000"},
            {"secure", "enabled_accessibility_services", "other/Service"}, {"secure", "sleep_timeout", "-1"},
            {"secure", "sleep_timeout", "2147483648"}, {"secure", "sleep_timeout", "1.5"},
            {"secure", "sleep_timeout", "null"}, {"system", "screen_off_timeout", "0"},
            {"secure", "screensaver_enabled", "true"}, {"secure", "str.auto_wake_up_enabled", "2"},
            {"secure", "screensaver_components", "example.saver/.Dream,other/.Dream"},
            {"secure", "screensaver_components", "../private"}, {"secure", "screensaver_components", null}
        }) failure(store, command[0], command[1], command[2]);
        check(before.equals(store.values) && reads == store.reads && writes == store.writes, "Invalid commands do not access settings");
        store.allowed = false;
        check(failure(store, "secure", "sleep_timeout", "60000") instanceof SecurityException, "Permission denial remains explicit");
        check(reads == store.reads && writes == store.writes, "Denied commands do not read or write settings");
        store.allowed = true; store.component = false;
        failure(store, "secure", "screensaver_components", "missing.saver/.Dream");
        check(before.equals(store.values), "Unavailable components change nothing");
        store.component = true;
        Store unsupported = new Store();
        failure(unsupported, "secure", "str.auto_wake_up_enabled", "1");
        failure(unsupported, "secure", "sleep_timeout", "0");
        check(unsupported.writes == 0, "Absent vendor controls are not invented");
        Store rejected = new Store(); rejected.values.put("secure/sleep_timeout", "1200000"); rejected.ignore = true;
        failure(rejected, "secure", "sleep_timeout", "60000");
        check("1200000".equals(rejected.values.get("secure/sleep_timeout")), "Ignored writes report failure and retain prior state");
        Store partial = new Store(); partial.partial = true;
        failure(partial, "secure", "screensaver_enabled", "1");
        check(!partial.values.containsKey("secure/screensaver_enabled"), "Failed writes restore actual prior absence");
        Store raced = new Store(); raced.values.put("secure/sleep_timeout", "1200000"); raced.ownerChange = "90000";
        check(failure(raced, "secure", "sleep_timeout", "60000").getMessage().contains("recovery requires review"), "Intervening change is reported");
        check(raced.writes == 1 && "90000".equals(raced.values.get("secure/sleep_timeout")), "Owner changes are never overwritten by rollback");
        Store broken = new Store(); broken.values.put("secure/sleep_timeout", "1200000"); broken.partial = true; broken.refuseRollback = true;
        check(failure(broken, "secure", "sleep_timeout", "60000").getMessage().contains("Recovery requires review"), "Failed rollback never reports restoration");
        Store denied = new Store(); denied.values.put("secure/sleep_timeout", "1200000"); denied.denyWrite = true;
        check(failure(denied, "secure", "sleep_timeout", "60000") instanceof SecurityException, "Provider security failure remains explicit");
        check("1200000".equals(denied.values.get("secure/sleep_timeout")), "Denied provider write retains current state");
        System.out.println("Setting command allowlist, permission, readback, rollback and owner-change checks passed");
    }

    private static final class Store implements SettingCommandPolicy.Store {
        final Map<String, String> values = new LinkedHashMap<>();
        boolean allowed = true, component = true, ignore, partial, refuseRollback, denyWrite;
        String ownerChange;
        int reads, writes;
        @Override public boolean permitted(String namespace) { return allowed; }
        @Override public String read(String namespace, String key) { reads++; return values.get(namespace + "/" + key); }
        @Override public boolean componentAvailable(String value) { return component; }
        @Override public boolean write(String namespace, String key, String value) {
            writes++;
            if (denyWrite) throw new SecurityException("Fixture denial");
            if (ignore || (refuseRollback && writes > 1)) return false;
            if (value == null) values.remove(namespace + "/" + key); else values.put(namespace + "/" + key, value);
            if (ownerChange != null) values.put(namespace + "/" + key, ownerChange);
            if (partial) { partial = false; return false; }
            return true;
        }
    }
}
