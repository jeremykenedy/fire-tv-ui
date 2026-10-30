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
        check(context.broadcasts.size() == 1);
        check(context.broadcasts.get(0).action.equals("com.jeremykenedy.firetv.ui.PREFS_CHANGED"));
        check(context.broadcasts.get(0).pkg.equals(context.getPackageName()));
        check(context.valuesAtBroadcast.equals(context.prefs.values));
        BackupStore.save(context);
        JSONObject exported = new JSONObject(Files.readString(BackupStore.file(false).toPath()));
        check(!exported.has(REMOTE) && exported.getString("theme").equals("new"));
        context.prefs.values.remove(REMOTE);
        BackupStore.restore(context, true);
        check(!context.prefs.values.containsKey(REMOTE));
        context.prefs.values.put(REMOTE, "destination-identity");
        Map<String,Object> before = new HashMap<>(context.prefs.values);
        int notificationsBefore = context.broadcasts.size();
        context.prefs.failCommit = true;
        try { BackupStore.restore(context, true); throw new AssertionError("Failed commit accepted"); }
        catch (java.io.IOException expected) {
            check(before.equals(context.prefs.values));
            check(context.broadcasts.size() == notificationsBefore);
        }
        context.prefs.failCommit = false;
        Files.writeString(BackupStore.file(true).toPath(), new JSONObject()
                .put(REMOTE, "source-identity").put("theme", 123).toString());
        try { BackupStore.restore(context, true); throw new AssertionError("Invalid backup accepted"); }
        catch (org.json.JSONException expected) { check(before.equals(context.prefs.values)); }
        Files.writeString(BackupStore.file(true).toPath(), new JSONObject().put(REMOTE, "source-identity").toString());
        try { BackupStore.restore(context, true); throw new AssertionError("Identity-only backup accepted"); }
        catch (org.json.JSONException expected) { check(before.equals(context.prefs.values)); }
        check(context.broadcasts.size() == notificationsBefore);
        Context notificationDenied = new Context();
        notificationDenied.rejectBroadcast = true;
        Files.writeString(BackupStore.file(true).toPath(), new JSONObject().put("theme", "saved").toString());
        BackupStore.restore(notificationDenied, true);
        check(notificationDenied.prefs.values.get("theme").equals("saved"));
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
        for (String key : new String[]{"menu_animation", "menu_animation_speed", "menu_animation_intensity", "menu_animation_brightness"}) {
            Object invalid = key.equals("menu_animation") ? "unknown-style" : 6;
            Files.writeString(BackupStore.file(true).toPath(), new JSONObject().put(key, invalid).toString());
            try { BackupStore.restore(context, true); throw new AssertionError("Invalid animation accepted"); }
            catch (org.json.JSONException expected) { check(hiddenBefore.equals(context.prefs.values)); }
        }
        for (String key : new String[]{"menu_overlay", "menu_overlay_speed", "menu_overlay_intensity",
                "menu_overlay_opacity", "menu_animation_frozen", "menu_overlay_frozen",
                "menu_background_palette", "menu_overlay_palette"}) {
            Object invalid = key.endsWith("palette") || key.equals("menu_overlay") ? "unknown" : 6;
            Files.writeString(BackupStore.file(true).toPath(), new JSONObject().put(key, invalid).toString());
            try { BackupStore.restore(context, true); throw new AssertionError("Invalid overlay accepted"); }
            catch (org.json.JSONException expected) { check(hiddenBefore.equals(context.prefs.values)); }
        }
        JSONObject layers = new JSONObject().put("menu_animation", "aurora")
                .put("menu_overlay", "hearts_floating").put("menu_overlay_speed", 25)
                .put("menu_overlay_intensity", 75).put("menu_overlay_opacity", 50)
                .put("menu_animation_frozen", false).put("menu_overlay_frozen", true)
                .put("menu_background_palette", "").put("menu_overlay_palette", "valentine");
        Files.writeString(BackupStore.file(true).toPath(), layers.toString());
        BackupStore.restore(context, true); BackupStore.save(context);
        JSONObject restoredLayers = new JSONObject(Files.readString(BackupStore.file(false).toPath()));
        for (String key : layers.keySet()) check(layers.get(key).equals(restoredLayers.get(key)));
        JSONObject animation = new JSONObject().put("menu_animation", "aurora")
                .put("menu_animation_speed", 25).put("menu_animation_intensity", 75)
                .put("menu_animation_brightness", 50);
        Files.writeString(BackupStore.file(true).toPath(), animation.toString());
        BackupStore.restore(context, true);
        check(context.prefs.values.get("menu_animation").equals("aurora"));
        check(context.prefs.values.get("menu_animation_speed").equals(25));
        for (String value : new String[]{"silk", "bokeh", "stars", "embers", "waves", "rain", "orbits", "rays",
                "still_midnight", "still_graphite", "still_forest", "still_plum"}) {
            animation.put("menu_animation", value);
            Files.writeString(BackupStore.file(true).toPath(), animation.toString());
            BackupStore.restore(context, true);
            check(context.prefs.values.get("menu_animation").equals(value));
            BackupStore.save(context);
            check(new JSONObject(Files.readString(BackupStore.file(false).toPath()))
                    .getString("menu_animation").equals(value));
        }
        Files.writeString(BackupStore.file(true).toPath(), new JSONObject(hiddenBefore).toString());
        BackupStore.restore(context, true);
        for (int seconds : new int[] {0, 1, 5, 7, 8, 15, 30, 86400}) {
            Files.writeString(BackupStore.file(true).toPath(), new JSONObject(hiddenBefore)
                    .put("featured_rotation_seconds", seconds).toString());
            BackupStore.restore(context, true);
            BackupStore.save(context);
            String saved = Files.readString(BackupStore.file(false).toPath());
            check(new JSONObject(saved).getInt("featured_rotation_seconds") == seconds);
            context.prefs.values.put("featured_rotation_seconds", 15);
            Files.writeString(BackupStore.file(true).toPath(), saved);
            BackupStore.restore(context, true);
            check(context.prefs.values.get("featured_rotation_seconds").equals(seconds));
            check(FeaturedCardTiming.delay(context.prefs)
                    == (seconds == 0 ? Long.MAX_VALUE : seconds * 1000L));
        }
        Files.writeString(BackupStore.file(true).toPath(), new JSONObject(hiddenBefore).toString());
        BackupStore.restore(context, true);
        for (Object invalid : new Object[] {-1, 86401, 3600000, "7", 7.5, true}) {
            Files.writeString(BackupStore.file(true).toPath(), new JSONObject()
                    .put("featured_rotation_seconds", invalid).toString());
            try { BackupStore.restore(context, true); throw new AssertionError("Invalid rotation accepted"); }
            catch (org.json.JSONException expected) { check(hiddenBefore.equals(context.prefs.values)); }
        }
        for (String key : new String[] {"menu_transition_style", "app_scroll_direction", "app_max_rows"}) {
            Object[] good = key.equals("menu_transition_style") ? new Object[] {"default", "none", "fade", "slide"}
                    : key.equals("app_scroll_direction") ? new Object[] {"vertical", "horizontal"}
                    : new Object[] {0, 1, 2, 3, 4, 5, 6};
            for (Object value : good) {
                Files.writeString(BackupStore.file(true).toPath(), new JSONObject(hiddenBefore).put(key, value).toString());
                BackupStore.restore(context, true);
                check(context.prefs.values.get(key).equals(value));
                BackupStore.save(context);
                check(new JSONObject(Files.readString(BackupStore.file(false).toPath())).get(key).equals(value));
            }
            Files.writeString(BackupStore.file(true).toPath(), new JSONObject(hiddenBefore).toString());
            BackupStore.restore(context, true);
            for (Object bad : new Object[] {"unknown", "1", -1, 7, 1.5, true}) {
                Files.writeString(BackupStore.file(true).toPath(), new JSONObject(hiddenBefore)
                        .put("theme", "must-not-be-committed").put(key, bad).toString());
                try { BackupStore.restore(context, true); throw new AssertionError("Invalid layout/motion accepted"); }
                catch (org.json.JSONException expected) { check(hiddenBefore.equals(context.prefs.values)); }
            }
        }
        String feed = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        org.json.JSONArray customTimers = new org.json.JSONArray().put(feed + ":7");
        Files.writeString(BackupStore.file(true).toPath(), new JSONObject(hiddenBefore)
                .put(FeaturedCardTiming.OVERRIDES, customTimers).toString());
        BackupStore.restore(context, true);
        BackupStore.save(context);
        check(FeaturedCardTiming.seconds(context.prefs, feed) == 7);
        check(new JSONObject(Files.readString(BackupStore.file(false).toPath()))
                .getJSONArray(FeaturedCardTiming.OVERRIDES).getString(0).equals(feed + ":7"));
        Files.writeString(BackupStore.file(true).toPath(), new JSONObject(hiddenBefore).toString());
        BackupStore.restore(context, true);
        for (Object bad : new Object[]{"wrong",
                new org.json.JSONArray().put(feed + ":7").put(feed + ":8"),
                new org.json.JSONArray().put(feed + ":7").put(feed + ":7"),
                new org.json.JSONArray().put(feed + ":86401"),
                new org.json.JSONArray().put(7), new org.json.JSONArray().put("bad:7")}) {
            Files.writeString(BackupStore.file(true).toPath(), new JSONObject(hiddenBefore)
                    .put("theme", "should-not-commit").put(FeaturedCardTiming.OVERRIDES, bad).toString());
            try {
                BackupStore.restore(context, true);
                throw new AssertionError("Invalid channel timer accepted");
            } catch (org.json.JSONException expected) {
                check(hiddenBefore.equals(context.prefs.values));
            }
        }
        System.out.println("Device-local backup migration checks passed.");
    }
}
