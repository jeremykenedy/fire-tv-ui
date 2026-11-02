package com.jeremykenedy.firetv.ui.nativeapp.appearance;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Map;

public final class AppearancePreferences {
    public static final String FILE = "source_appearance";
    public static final java.util.List<String> KEYS = java.util.Collections.unmodifiableList(java.util.Arrays.asList(
            "base", "legacy_gradient", "base_frozen", "base_speed", "base_intensity", "base_brightness", "base_palette",
            "overlay", "overlay_frozen", "overlay_speed", "overlay_intensity", "overlay_opacity", "overlay_palette",
            "reduced_motion", "scene", "wallpaper_image_uri", "wallpaper_video_uri", "wallpaper_mode", "wallpaper_video_frozen"));
    public static void validateSnapshot(Map<String, ?> values) {
        for (String key : KEYS) {
            if (!values.containsKey(key)) continue;
            Object value = values.get(key);
            if (key.endsWith("_frozen") || key.equals("reduced_motion")) {
                if (!(value instanceof Boolean)) throw new IllegalArgumentException("Invalid appearance motion value.");
            } else if (key.endsWith("_speed") || key.endsWith("_intensity") || key.equals("base_brightness") || key.equals("overlay_opacity")) {
                if (!(value instanceof Integer) || (Integer)value < 0 || (Integer)value > 100) throw new IllegalArgumentException("Invalid appearance level.");
            } else {
                if (!(value instanceof String) || ((String)value).length() > 8192) throw new IllegalArgumentException("Invalid appearance selection.");
                String text=(String)value;
                if (key.equals("legacy_gradient") && !text.isEmpty() && LegacyGradients.index(text) < 0) throw new IllegalArgumentException("Unknown saved gradient.");
                if (key.equals("base") && !AppearanceOptions.known(AppearanceOptions.BASES,text).equals(text)) throw new IllegalArgumentException("Unknown background.");
                if (key.equals("overlay") && !AppearanceOptions.known(AppearanceOptions.OVERLAYS,text).equals(text)) throw new IllegalArgumentException("Unknown overlay.");
                if (key.endsWith("_palette") && !text.isEmpty() && PaletteRecipes.find(text)==null) throw new IllegalArgumentException("Unknown palette recipe.");
                if (key.equals("scene") && !text.isEmpty() && com.jeremykenedy.firetv.ui.nativeapp.holidays.HolidayScenes.find(text) == null) throw new IllegalArgumentException("Unknown scene.");
                if (key.equals("wallpaper_mode") && !text.equals("system") && !text.equals("image") && !text.equals("video")) throw new IllegalArgumentException("Unknown wallpaper source.");
            }
        }
    }
    private AppearancePreferences() {}
    public static SharedPreferences file(Context context) { return context.getSharedPreferences(FILE, Context.MODE_PRIVATE); }
    public static String wallpaperMode(Context context) {
        Object value = file(context).getAll().get("wallpaper_mode");
        return "image".equals(value) || "video".equals(value) ? (String) value : "system";
    }

    public static boolean reducedMotion(Context context) {
        return Boolean.TRUE.equals(file(context).getAll().get("reduced_motion"));
    }

    public static boolean videoFrozen(Context context) {
        return Boolean.TRUE.equals(file(context).getAll().get("wallpaper_video_frozen"));
    }

    public static void selectWallpaper(Context context, String mode) {
        if (!mode.equals("system") && !mode.equals("image") && !mode.equals("video")) {
            throw new IllegalArgumentException("Unknown wallpaper source.");
        }
        string(context, "wallpaper_mode", mode);
    }

    public static AppearanceOptions load(Context context) { synchronized(com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState.class) {
        com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction.assertReadable(context);
        Map<String, ?> values = file(context).getAll();
        return new AppearanceOptions(string(values, "base", "off"), flag(values, "base_frozen"),
                number(values, "base_speed"), number(values, "base_intensity"), number(values, "base_brightness"),
                string(values, "overlay", "off"), flag(values, "overlay_frozen"), number(values, "overlay_speed"),
                number(values, "overlay_intensity"), number(values, "overlay_opacity"), string(values, "scene", ""),
                string(values, "wallpaper_image_uri", ""), string(values, "wallpaper_video_uri", ""), string(values,"base_palette",""), string(values,"overlay_palette",""), string(values,"legacy_gradient",""));
    } }
    private static String string(Map<String, ?> values, String key, String fallback) { Object value = values.get(key); return value instanceof String ? (String) value : fallback; }
    private static int number(Map<String, ?> values, String key) { Object value = values.get(key); return value instanceof Integer ? (Integer) value : 50; }
    private static boolean flag(Map<String, ?> values, String key) { return Boolean.TRUE.equals(values.get(key)); }
    public static void string(Context context, String key, String value) { synchronized(com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState.class) { com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction.assertWritable(context); commit(file(context), key, value); } }
    public static void number(Context context, String key, int value) { if (value < 0 || value > 100) throw new IllegalArgumentException("Use a value from 0 to 100."); synchronized(com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState.class) { com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction.assertWritable(context); commit(file(context), key, value); } }
    public static void flag(Context context, String key, boolean value) { synchronized(com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState.class) { com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction.assertWritable(context); commit(file(context), key, value); } }
    static void update(Context context, String[] keys, Object[] values) { synchronized(com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState.class) {
        com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction.assertWritable(context);
        SharedPreferences prefs = file(context);
        Map<String, ?> previous = prefs.getAll();
        SharedPreferences.Editor edit = prefs.edit();
        for (int i=0;i<keys.length;i++) put(edit,keys[i],values[i]);
        if (edit.commit()) return;
        SharedPreferences.Editor rollback = prefs.edit();
        for (String key : keys) put(rollback,key,previous.get(key));
        rollback.commit();
        throw new IllegalStateException("Appearance settings could not be saved. Try again.");
    } }
    private static void commit(SharedPreferences prefs, String key, Object value) { synchronized(com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState.class) {
        Object previous = prefs.getAll().get(key);
        if (put(prefs.edit(), key, value).commit()) return;
        put(prefs.edit(), key, previous).commit();
        throw new IllegalStateException("Appearance settings could not be saved. Try again.");
    } }
    private static SharedPreferences.Editor put(SharedPreferences.Editor edit, String key, Object value) {
        if (value instanceof String) return edit.putString(key, (String) value);
        if (value instanceof Integer) return edit.putInt(key, (Integer) value);
        if (value instanceof Boolean) return edit.putBoolean(key, (Boolean) value);
        return edit.remove(key);
    }
}
