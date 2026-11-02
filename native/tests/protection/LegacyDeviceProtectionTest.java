package com.jeremykenedy.firetv.ui.nativeapp.device.protection;

import java.util.*;

public final class LegacyDeviceProtectionTest {
    public static void main(String[] args) {
        Map<String,Object> saved = new LinkedHashMap<>();
        saved.put("fire_tv_ui_protect_settings", false);
        saved.put("fire_tv_ui_sleep", 5000);
        saved.put("fire_tv_ui_sleep_saved", 1200000);
        saved.put("fire_tv_ui_deep_sleep_enabled", true);
        saved.put("fire_tv_ui_auto_wake", 1);
        saved.put("fire_tv_ui_keep_adb", true);
        saved.put("fire_tv_ui_dream_unlocked", false);
        saved.put("fire_tv_ui_dream", "fixture.dream/.Service");
        saved.put("fire_tv_ui_dream_enabled", true);
        saved.put("fire_tv_ui_idle", 300000);
        Fake store = new Fake();
        store.data.put("secure:sleep_timeout", "1");
        store.data.put("secure:str.auto_wake_up_enabled", "1");
        int beforeInspect = store.writes;
        LegacyProtectionSession session = new LegacyProtectionSession();
        saved.put("fire_tv_ui_protect_settings", true);
        check(!session.check(store, saved).complete() && !session.armed() && store.writes == beforeInspect);
        saved.put("fire_tv_ui_protect_settings", false);
        check(LegacyDeviceProtection.repair(store, saved).complete());
        check(LegacyDeviceProtection.inspect(store, saved).complete());
        saved.put("fire_tv_ui_protect_settings", true);
        store.data.put("secure:screensaver_components", "fixture.dream/fixture.dream.Service");
        store.data.put("secure:screensaver_enabled", "1");
        store.data.put("system:screen_off_timeout", "300000");
        check(session.check(store, saved).complete() && session.armed());
        store.data.put("secure:sleep_timeout", "42");
        check(session.check(store, saved).complete() && "1200000".equals(store.data.get("secure:sleep_timeout")));
        session.preferencesChanged(); store.data.put("secure:sleep_timeout", "42"); beforeInspect = store.writes;
        check(!session.check(store, saved).complete() && store.writes == beforeInspect);
        store.data.put("secure:sleep_timeout", "1200000");
        saved.put("fire_tv_ui_protect_settings", false);
        store.data.remove("secure:screensaver_components");
        check("1200000".equals(store.data.get("secure:sleep_timeout")));
        check("0".equals(store.data.get("secure:str.auto_wake_up_enabled")));
        check("1".equals(store.data.get("global:adb_wifi_enabled")));
        check(!store.data.containsKey("secure:screensaver_components"));
        saved.put("fire_tv_ui_protect_settings", true);
        check(LegacyDeviceProtection.repair(store, saved).complete());
        check("fixture.dream/.Service".equals(store.data.get("secure:screensaver_components")));
        check("300000".equals(store.data.get("system:screen_off_timeout")));
        saved.put("fire_tv_ui_dream_unlocked", true);
        store.data.remove("secure:screensaver_components");
        check(LegacyDeviceProtection.repair(store, saved).complete());
        check(!store.data.containsKey("secure:screensaver_components"));
        saved.put("fire_tv_ui_preview_pending", true);
        store.data.remove("system:screen_off_timeout");
        check(LegacyDeviceProtection.repair(store, saved).complete());
        check(!store.data.containsKey("system:screen_off_timeout"));
        saved.remove("fire_tv_ui_preview_pending"); saved.put("fire_tv_ui_dream_unlocked", false);
        store.dream = false;
        check(LegacyDeviceProtection.repair(store, saved).unavailable.contains("screensaver_components"));
        check(saved.containsKey("fire_tv_ui_dream"));
        store.allowed = false; int count = store.writes;
        check(!LegacyDeviceProtection.repair(store, saved).complete() && store.writes == count);
        store.allowed = true; saved.put("fire_tv_ui_idle", "bad"); count = store.writes;
        check(!LegacyDeviceProtection.repair(store, saved).complete() && store.writes == count);
        System.out.println("Legacy device policy, saved values, missing dream, permission and failure behavior passed.");
    }
    static final class Fake implements LegacyDeviceProtection.Store {
        final Map<String,String> data = new HashMap<>(); boolean allowed = true, dream = true; int writes;
        public boolean writable(){return allowed;} public boolean fireTv(){return true;}
        public String read(String namespace, String key){return data.get(namespace + ":" + key);}
        public boolean write(String namespace,String key,String value){writes++; data.put(namespace + ":" + key,value); return true;}
        public boolean availableDream(String component){return dream;}
    }
    static void check(boolean value) { if (!value) throw new AssertionError("Legacy device protection regression."); }
}
