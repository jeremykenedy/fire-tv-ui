package com.jeremykenedy.firetv.ui.nativeapp.device.protection;

import android.app.Instrumentation;
import android.os.Bundle;
import com.jeremykenedy.firetv.ui.nativeapp.device.commands.LegacySettingTransaction;
import com.jeremykenedy.firetv.ui.nativeapp.device.commands.SettingCommandPolicy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** In-memory policy fixture; no platform settings or owner preferences are touched. */
public final class DeviceProtectionInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (!"com.jeremykenedy.firetv.migrationproof".equals(getTargetContext().getPackageName()))
                throw new AssertionError("Unexpected target.");
            Map<String,Object> prefs = new LinkedHashMap<>();
            prefs.put("fire_tv_ui_protect_settings", false);
            prefs.put("fire_tv_ui_sleep_saved", 1200000);
            prefs.put("fire_tv_ui_deep_sleep_enabled", false);
            prefs.put("fire_tv_ui_keep_adb", true);
            Store store = new Store();
            store.values.put("secure:sleep_timeout", "10");
            store.values.put("secure:str.auto_wake_up_enabled", "1");
            store.values.put("global:development_settings_enabled", "1");
            store.values.put("global:adb_enabled", "1");
            store.values.put("global:adb_wifi_enabled", "1");
            LegacyProtectionSession session = new LegacyProtectionSession();
            check(!session.check(store, prefs).complete() && store.writes == 0, "Mismatch must not change the TV.");
            store.values.put("secure:sleep_timeout", "1200000");
            check(session.check(store, prefs).complete() && session.armed(), "Matching saved values arm protection.");
            store.values.put("secure:sleep_timeout", "10");
            check(session.check(store, prefs).complete() && "1200000".equals(store.values.get("secure:sleep_timeout")),
                    "Explicit sleep choice remains enforced even with protect off.");
            session.preferencesChanged(); store.values.put("secure:sleep_timeout", "10");
            int before = store.writes;
            check(!session.check(store, prefs).complete() && store.writes == before, "Changed settings require read-only reconciliation.");
            Command command = new Command(); Saved saved = new Saved();
            LegacySettingTransaction.execute(command, saved, "secure", "sleep_timeout", "900000");
            check("900000".equals(command.value) && Integer.valueOf(900000).equals(saved.values.get("fire_tv_ui_sleep_saved")), "Command preserves saved choice.");
            saved.fail = true;
            try { LegacySettingTransaction.execute(command, saved, "secure", "sleep_timeout", "800000"); throw new AssertionError("Expected failure."); }
            catch (IllegalStateException expected) { check("900000".equals(command.value), "Failed commit restores prior setting."); }
            result.putString("status", "PASS");
        } catch (Throwable failure) { result.putString("status", "FAIL"); result.putString("reason", failure.toString()); }
        finish(0, result);
    }
    private static void check(boolean value, String text) { if (!value) throw new AssertionError(text); }
    private static final class Command implements SettingCommandPolicy.Store {
        String value = "600000";
        public boolean permitted(String ns) { return true; }
        public String read(String ns, String key) { return value; }
        public boolean write(String ns, String key, String wanted) { value = wanted; return true; }
        public boolean componentAvailable(String component) { return true; }
    }
    private static final class Saved implements LegacySettingTransaction.Preferences {
        final Map<String,Object> values = new HashMap<>(); boolean fail;
        public Map<String,?> read() { return new HashMap<>(values); }
        public boolean write(Map<String,Object> patch) {
            for (Map.Entry<String,Object> entry : patch.entrySet()) {
                if (entry.getValue() == null) values.remove(entry.getKey()); else values.put(entry.getKey(), entry.getValue());
            }
            if (fail) { fail = false; return false; } return true;
        }
    }
    private static final class Store implements LegacyDeviceProtection.Store {
        final Map<String,String> values = new HashMap<>(); int writes;
        public boolean writable() { return true; }
        public boolean fireTv() { return true; }
        public String read(String namespace,String key) { return values.get(namespace + ":" + key); }
        public boolean write(String namespace,String key,String value) { writes++; values.put(namespace + ":" + key,value); return true; }
        public boolean availableDream(String component) { return true; }
    }
}
