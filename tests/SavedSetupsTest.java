package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.net.Uri;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.file.Files;
import java.util.*;

public class SavedSetupsTest {
    private static int checks;
    private static void check(boolean value, String label) {checks++; if (!value) throw new AssertionError(label);}
    private interface Work {void run() throws Exception;}
    private static void rejects(Work work, String label) throws Exception {
        checks++; try {work.run();} catch (Exception expected) {return;} throw new AssertionError(label);
    }
    public static void main(String[] args) throws Exception {
        Context c = new Context(new File(args[0], "data"));
        c.prefs.values.put("apps_per_row", 7);
        c.prefs.values.put("menu_overlay", "snow");
        c.prefs.values.put("menu_overlay_frozen", false);
        c.prefs.values.put("color_widget_bg_color_argb", 123);
        c.prefs.values.put("featured_feed_rotation_seconds", new LinkedHashSet<>(Arrays.asList(repeat('a',64)+":0",repeat('b',64)+":300")));
        c.prefs.values.put("fire_tv_ui_sleep", 1200000);
        c.prefs.values.put("fire_tv_ui_equipment_remote", "device-local");
        c.prefs.values.put("access_token", "local-secret");
        c.prefs.values.put("folder_lock_pin", "local-pin");
        JSONObject capture = SavedSetupSettings.capture(c.prefs.getAll());
        Map<String,Object> saved = SavedSetupSettings.validate(capture);
        check(saved.get("apps_per_row").equals(7), "typed integer");
        check(saved.get("menu_overlay_frozen").equals(false), "typed boolean");
        check(saved.get("featured_feed_rotation_seconds") instanceof Set, "typed set");
        check(saved.containsKey("menu_animation") && saved.get("menu_animation")==null, "absent settings recorded");
        check(!saved.containsKey("fire_tv_ui_sleep") && !saved.containsKey("access_token") && !saved.containsKey("folder_lock_pin"), "operational and secret exclusions");
        c.prefs.values.put("menu_animation", "aurora");
        c.prefs.values.put("color_later", 456);
        SavedSetupSettings.apply(c.prefs, SavedSetupSettings.toApply(capture,c.prefs.getAll(),true));
        check(!c.prefs.values.containsKey("menu_animation") && !c.prefs.values.containsKey("color_later"), "restore absence and custom colors");
        check(c.prefs.values.get("access_token").equals("local-secret") && c.prefs.values.get("fire_tv_ui_sleep").equals(1200000), "restore preserves account and device");
        JSONObject bad = new JSONObject(capture.toString());
        bad.getJSONObject("values").put("apps_per_row", "7");
        rejects(()->SavedSetupSettings.validate(bad),"wrong type");
        JSONObject unsafe = new JSONObject().put("keys",new JSONArray(Arrays.asList("fire_tv_ui_sleep"))).put("values",new JSONObject().put("fire_tv_ui_sleep",0));
        rejects(()->SavedSetupSettings.validate(unsafe),"unsafe key");
        JSONObject timers = new JSONObject(capture.toString());
        timers.getJSONObject("values").put("featured_feed_rotation_seconds",new JSONArray(Arrays.asList(repeat('a',64)+":0",repeat('a',64)+":5")));
        rejects(()->SavedSetupSettings.validate(timers),"conflicting timer feeds");
        Map<String,Object> before = new LinkedHashMap<>(c.prefs.values);
        c.prefs.failures=1;
        rejects(()->SavedSetupSettings.apply(c.prefs,Collections.singletonMap("apps_per_row",3)),"failed preference commit");
        check(c.prefs.values.equals(before),"failed preference commit rolls back memory");
        File source = new File(args[0],"wallpaper.png"); Files.write(source.toPath(),new byte[]{1,2,3,4});
        c.prefs.values.put("custom_wallpaper_uri",Uri.fromFile(source).toString());
        c.prefs.values.put("icon_pack_package","builtin:holiday:winter");
        c.prefs.values.put("app_visibility",new JSONObject().put("app.one",new JSONObject().put("hidden",true).put("customIconUri",Uri.fromFile(source).toString())).toString());
        SavedSetupStore store = new SavedSetupStore(c);
        String id=store.save("Winter night",null);
        check(store.list().size()==1,"save lists setup");
        JSONObject stored=store.list().get(0).getJSONObject("settings").getJSONObject("values");
        File copy=new File(Uri.parse(stored.getString("custom_wallpaper_uri")).getPath());
        check(copy.exists() && !copy.equals(source),"custom wallpaper privately copied");
        check(new JSONObject(stored.getString("app_visibility")).getJSONObject("app.one").getString("customIconUri").equals(Uri.fromFile(copy).toString()),"shared icon deduplicated");
        Files.delete(source.toPath());
        c.prefs.values.put("apps_per_row",3);
        File blur=new File(c.files,"wallpaper_blur.jpg");Files.write(blur.toPath(),new byte[]{6,6,6});
        store.apply(id,true);
        check(!blur.exists(),"switching wallpaper invalidates old blur cache");
        check(c.prefs.values.get("apps_per_row").equals(7),"apply restores layout");
        check(c.prefs.values.get("custom_wallpaper_uri").equals(Uri.fromFile(copy).toString()),"apply survives original image deletion");
        c.prefs.values.put("icon_pack_package","other-pack");
        c.prefs.values.put("app_visibility",new JSONObject().put("app.one",new JSONObject().put("hidden",false).put("customIconUri","asset:local.png")).toString());
        store.apply(id,false);
        check(c.prefs.values.get("icon_pack_package").equals("other-pack"),"optional icons preserves pack");
        JSONObject app=new JSONObject((String)c.prefs.values.get("app_visibility")).getJSONObject("app.one");
        check(app.getBoolean("hidden") && app.getString("customIconUri").equals("asset:local.png"),"optional icons preserves custom icon but restores visibility");
        c.prefs.values.put("app_visibility",new JSONObject().put("app.one",new JSONObject().put("customIconUri",Uri.fromFile(new File(args[0],"stale-icon.png")).toString())).toString());
        store.apply(id,false);
        check(new JSONObject((String)c.prefs.values.get("app_visibility")).getJSONObject("app.one").getString("customIconUri").endsWith("stale-icon.png"),"preserved missing icon does not block appearance restore");
        c.prefs.values.put("app_visibility",new JSONObject().put("app.one",new JSONObject().put("hidden",true).put("customIconUri","asset:local.png")).toString());
        rejects(()->store.save(" winter NIGHT ",null),"duplicate names case insensitive");
        rejects(()->store.save(" ",null),"blank name");
        AtomicFile.failNext=true;
        rejects(()->store.rename(id,"Changed"),"failed atomic write");
        check(store.list().get(0).getString("name").equals("Winter night"),"failed write keeps previous record");
        File secondImage=new File(args[0],"second.png");Files.write(secondImage.toPath(),new byte[]{9,8,7});
        c.prefs.values.put("custom_wallpaper_uri",Uri.fromFile(secondImage).toString());
        AtomicFile.failNext=true;
        rejects(()->store.save("Failed save",null),"failed save after image copy");
        check(store.list().size()==1 && new File(c.files,"saved-setups").listFiles((dir,name)->name.endsWith(".image")).length==1,"failed save cleans copied images and preserves registry");
        c.prefs.values.put("custom_wallpaper_uri",Uri.fromFile(copy).toString());
        String imageUri=Uri.fromFile(copy).toString();
        c.prefs.values.put("custom_wallpaper_uri",Uri.fromFile(new File(args[0],"missing.png")).toString());
        rejects(()->store.save("Missing image",null),"missing image rejects complete save");
        check(store.list().size()==1,"missing image does not add partial setup");
        c.prefs.values.put("custom_wallpaper_uri",imageUri);
        final SavedSetupStore other=new SavedSetupStore(c);
        final List<Throwable> failures=Collections.synchronizedList(new ArrayList<Throwable>());
        Thread first=new Thread(()->{try{store.save("Concurrent one",null);}catch(Throwable failure){failures.add(failure);}});
        Thread second=new Thread(()->{try{other.save("Concurrent two",null);}catch(Throwable failure){failures.add(failure);}});
        first.start();second.start();first.join();second.join();
        check(failures.isEmpty() && store.list().size()==3,"multiple store instances serialize registry changes");
        for(JSONObject entry:store.list())if(!entry.getString("id").equals(id))store.delete(entry.getString("id"));
        store.rename(id,"Quiet evening");
        check(store.list().get(0).getString("name").equals("Quiet evening"),"rename");
        c.prefs.values.put("apps_per_row",9);store.save("Quiet evening",id);store.apply(id,true);
        check(c.prefs.values.get("apps_per_row").equals(9) && store.list().size()==1,"update replaces same setup");
        store.delete(id);
        check(store.list().isEmpty() && copy.exists(),"delete preserves image used by current setup");
        c.prefs.values.remove("custom_wallpaper_uri");c.prefs.values.remove("app_visibility");
        String next=store.save("No images",null);
        check(!copy.exists(),"unused private images pruned");
        for(int i=1;i<24;i++)store.save("Setup "+i,null);
        rejects(()->store.save("Beyond limit",null),"bounded setup count");
        check(store.list().size()==24,"limit does not modify registry");
        Map<String,Object> unchanged=new LinkedHashMap<>(c.prefs.values);
        File registry=new File(c.files,"saved-setups/setups.json");
        Files.write(registry.toPath(),"{broken".getBytes("UTF-8"));
        rejects(()->store.apply(next,true),"corrupt registry fails before apply");
        check(c.prefs.values.equals(unchanged),"invalid snapshot leaves all preferences unchanged");
        videoReferences(new File(args[0], "video-data"));
        System.out.println("Saved setups: "+checks+" checks passed");
    }
    private static void videoReferences(File directory) throws Exception {
        Context c = new Context(directory);
        SavedSetupStore store = new SavedSetupStore(c);
        File video = new File(directory, "wallpaper.mp4");
        Files.write(video.toPath(), new byte[]{1, 2, 3});
        String uri = Uri.fromFile(video).toString();
        c.prefs.values.put("mode", "customv");
        c.prefs.values.put("custom_video_uri", uri);
        c.prefs.values.put("apps_per_row", 7);
        String id = store.save("Video wallpaper", null);
        JSONObject values = store.list().get(0).getJSONObject("settings").getJSONObject("values");
        check(values.getString("custom_video_uri").equals(uri), "video source saved without replacement");
        check(new File(directory, "saved-setups").listFiles((dir, name) -> name.endsWith(".image")).length == 0,
                "video is referenced instead of copied as an image");
        c.prefs.values.put("mode", "custom");
        c.prefs.values.put("custom_video_uri", "file:///different-video.mp4");
        c.prefs.values.put("apps_per_row", 3);
        store.apply(id, false);
        check(c.prefs.values.get("mode").equals("customv") && c.prefs.values.get("custom_video_uri").equals(uri),
                "video mode and original source round trip with icons excluded");
        check(c.prefs.values.get("apps_per_row").equals(7), "video setup restores layout");
        Files.delete(video.toPath());
        c.prefs.values.put("mode", "custom");
        c.prefs.values.put("apps_per_row", 3);
        Map<String, Object> before = new LinkedHashMap<>(c.prefs.values);
        File blur = new File(directory, "wallpaper_blur.jpg");
        Files.write(blur.toPath(), new byte[]{4, 5, 6});
        rejects(() -> store.apply(id, true), "missing video rejects apply");
        check(c.prefs.values.equals(before) && Arrays.equals(Files.readAllBytes(blur.toPath()), new byte[]{4, 5, 6}),
                "missing video preserves all current preferences and image cache");
        String still = store.save("Still with inactive video reference", null);
        c.prefs.values.put("apps_per_row", 9);
        store.apply(still, true);
        check(c.prefs.values.get("apps_per_row").equals(3) && c.prefs.values.get("mode").equals("custom"),
                "inactive missing video does not block a different wallpaper mode");
        c.prefs.values.put("mode", "customv");
        c.prefs.values.remove("custom_video_uri");
        String absent = store.save("Absent video source", null);
        before = new LinkedHashMap<>(c.prefs.values);
        rejects(() -> store.apply(absent, true), "absent video source rejects apply");
        check(c.prefs.values.equals(before), "absent source does not mutate preferences");
        Files.write(video.toPath(), new byte[0]);
        rejects(() -> store.apply(id, true), "empty video source rejects apply");
        Files.write(video.toPath(), new byte[]{7, 8});
        store.apply(id, true);
        check(c.prefs.values.get("custom_video_uri").equals(uri), "restored source becomes applicable again");
    }
    private static String repeat(char ch,int length){char[] values=new char[length];Arrays.fill(values,ch);return new String(values);}
}
