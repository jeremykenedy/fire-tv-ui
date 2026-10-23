package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.os.Environment;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

public final class BackupMigrationTest {
    private static final String REMOTE = "fire_tv_ui_equipment_remote";

    private static void check(boolean value) {
        if (!value) throw new AssertionError("Backup/default settings behavior differed");
    }

    public static void main(String[] args) throws Exception {
        Environment.directory = new java.io.File(args[0]);
        Context.files = new java.io.File(args[0]);
        Context context = new Context();
        context.prefs.values.put(REMOTE, "destination-identity");
        context.prefs.values.put("theme", "old");
        Files.writeString(BackupStore.file(true).toPath(), new JSONObject()
                .put(REMOTE, "source-identity").put("theme", "new").toString());
        BackupStore.restore(context, true);
        check("destination-identity".equals(context.prefs.values.get(REMOTE)));
        check("new".equals(context.prefs.values.get("theme")));
        BackupStore.save(context);
        JSONObject exported = new JSONObject(Files.readString(BackupStore.file(false).toPath()));
        check(!exported.has(REMOTE) && exported.getString("theme").equals("new"));
        context.prefs.values.remove(REMOTE);
        BackupStore.restore(context, true);
        check(!context.prefs.values.containsKey(REMOTE));
        context.prefs.values.put(REMOTE, "destination-identity");
        Map<String,Object> before = new HashMap<>(context.prefs.values);
        Files.writeString(BackupStore.file(true).toPath(), new JSONObject()
                .put(REMOTE, "source-identity").put("theme", 123).toString());
        try { BackupStore.restore(context, true); throw new AssertionError("Invalid backup accepted"); }
        catch (org.json.JSONException expected) { check(before.equals(context.prefs.values)); }
        Files.writeString(BackupStore.file(true).toPath(), new JSONObject().put(REMOTE, "source-identity").toString());
        try { BackupStore.restore(context, true); throw new AssertionError("Identity-only backup accepted"); }
        catch (org.json.JSONException expected) { check(before.equals(context.prefs.values)); }
        JSONObject visibility = new JSONObject().put("hdmi", new JSONObject()
                .put("customIconUri", "asset:input-icons/xbox-one.png"));
        JSONObject defaults = new JSONObject().put("apps_per_row", 5)
                .put("custom_gradient", "X Pro").put("fire_tv_ui_sleep", 1200000)
                .put("fire_tv_ui_deep_sleep_enabled", false).put("fire_tv_ui_auto_wake", 1)
                .put("fire_tv_ui_home_enabled", true).put("app_visibility", visibility.toString());
        android.content.res.AssetManager.contents.put("simple-setup.json", defaults.toString());
        android.content.res.AssetManager.contents.put("input-icons/xbox-one.png", "bundled-xbox");
        android.content.res.AssetManager.contents.put("input-icons/wii-u.png", "bundled-wii");
        Context upgrade = new Context();
        upgrade.prefs.values.put("fire_tv_ui_home_enabled", false);
        Map<String,Object> upgradeBefore = new HashMap<>(upgrade.prefs.values);
        android.content.pm.PackageManager.info.lastUpdateTime = 2;
        BackupStore.freshSetup(upgrade);
        check(upgradeBefore.equals(upgrade.prefs.values));
        android.content.pm.PackageManager.info.lastUpdateTime = 1;
        Context fresh = new Context();
        fresh.prefs.values.put(REMOTE, "destination-identity");
        fresh.prefs.values.put("fire_tv_ui_home_enabled", false);
        BackupStore.freshSetup(fresh);
        check(Integer.valueOf(5).equals(fresh.prefs.values.get("apps_per_row")));
        check(fresh.prefs.values.get("fire_tv_ui_sleep").equals(1200000));
        check(fresh.prefs.values.get("fire_tv_ui_deep_sleep_enabled").equals(false));
        check(fresh.prefs.values.get("fire_tv_ui_auto_wake").equals(1));
        check(fresh.prefs.values.get("fire_tv_ui_home_enabled").equals(false));
        check(fresh.prefs.values.get(REMOTE).equals("destination-identity"));
        String icon = new JSONObject((String)fresh.prefs.values.get("app_visibility"))
                .getJSONObject("hdmi").getString("customIconUri");
        check(Files.readString(new java.io.File(java.net.URI.create(icon)).toPath()).equals("bundled-xbox"));
        Map<String,Object> existing = new HashMap<>(fresh.prefs.values);
        fresh.prefs.values.put("apps_per_row", 6);
        existing.put("apps_per_row", 6);
        BackupStore.freshSetup(fresh);
        check(existing.equals(fresh.prefs.values));
        BackupStore.simpleSetup(fresh);
        check(DeviceSettings.repaired && Integer.valueOf(5).equals(fresh.prefs.values.get("apps_per_row")));
        check(fresh.prefs.values.get(REMOTE).equals("destination-identity"));
        String hidden = new String(new char[64]).replace('\0', 'a');
        Files.writeString(BackupStore.file(true).toPath(), new JSONObject()
                .put("hidden_featured_cards", new org.json.JSONArray().put(hidden)).toString());
        BackupStore.restore(context, true);
        check(((java.util.Set<?>) context.prefs.values.get("hidden_featured_cards")).contains(hidden));
        BackupStore.save(context);
        check(new JSONObject(Files.readString(BackupStore.file(false).toPath()))
                .getJSONArray("hidden_featured_cards").getString(0).equals(hidden));
        Map<String,Object> hiddenBefore = new HashMap<>(context.prefs.values);
        for (Object bad : new Object[] {"raw-intent", new org.json.JSONArray().put("raw-intent")}) {
            Files.writeString(BackupStore.file(true).toPath(), new JSONObject()
                    .put("hidden_featured_cards", bad).toString());
            try { BackupStore.restore(context, true); throw new AssertionError("Invalid hidden identity accepted"); }
            catch (org.json.JSONException expected) { check(hiddenBefore.equals(context.prefs.values)); }
        }
        for (Object invalid : new Object[] {-1, 6, 3600000, "5"}) {
            Files.writeString(BackupStore.file(true).toPath(), new JSONObject()
                    .put("featured_rotation_seconds", invalid).toString());
            try { BackupStore.restore(context, true); throw new AssertionError("Invalid rotation accepted"); }
            catch (org.json.JSONException expected) { check(hiddenBefore.equals(context.prefs.values)); }
        }
        System.out.println("Device-local backup migration checks passed.");
    }
}
