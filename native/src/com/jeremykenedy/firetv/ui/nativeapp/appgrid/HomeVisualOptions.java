package com.jeremykenedy.firetv.ui.nativeapp.appgrid;

import android.content.Context;
import android.content.SharedPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction;
import com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState;
import java.util.Map;
import java.util.LinkedHashMap;

/** Retained Home presentation choices shared by Home and its settings. */
public final class HomeVisualOptions {
    private HomeVisualOptions() {}

    public static boolean isMini(Context context) { return read(context, "mini_mode", false); }
    public static boolean isBlurOnScroll(Context context) { return read(context, "blur_on_scroll", true); }
    public static void setMini(Context context, boolean enabled) {
        synchronized (PresentationState.class) {
            HolidayPresetTransaction.assertWritable(context);
            SharedPreferences prefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE);
            Map<String, ?> values = prefs.getAll();
            Object previous = values.get("mini_mode"), dock = values.get("keep_dock_bottom");
            if (values.containsKey("mini_mode") && !(previous instanceof Boolean)
                    || enabled && values.containsKey("keep_dock_bottom") && !(dock instanceof Boolean))
                throw new IllegalStateException("Saved Home setting could not be read. Its value was kept.");
            SharedPreferences.Editor edit = prefs.edit().putBoolean("mini_mode", enabled);
            if (enabled) edit.putBoolean("keep_dock_bottom", true);
            if (edit.commit()) return;
            SharedPreferences.Editor restore = prefs.edit();
            restoreBoolean(restore, "mini_mode", previous);
            if (enabled) restoreBoolean(restore, "keep_dock_bottom", dock);
            restore.commit();
            throw new IllegalStateException("Home setting could not be saved. Try again.");
        }
    }
    public static boolean isDockBottom(Context context) { return read(context, "keep_dock_bottom", false); }
    public static void setDockBottom(Context context, boolean enabled) { save(context, "keep_dock_bottom", enabled); }
    public static boolean isFavoritesBackgroundHidden(Context context) { return read(context, "hide_favorites_background", false); }
    public static boolean isFavoritesBackgroundBlurred(Context context) { return read(context, "blur_favorites_background", false); }
    public static void setBlurOnScroll(Context context, boolean enabled) { save(context, "blur_on_scroll", enabled); }
    public static void setFavoritesBackgroundBlurred(Context context, boolean enabled) {
        save(context, "blur_favorites_background", enabled);
    }
    public static String favoritesBackgroundMode(Context context) {
        Object value = readValue(context, "favorites_bg_mode", String.class, "Default");
        if (!"Default".equals(value) && !"Hide".equals(value) && !"Color".equals(value))
            throw new IllegalStateException("Saved Favorites background could not be read. Its value was kept.");
        return (String) value;
    }
    public static int favoritesBackgroundColor(Context context, int fallback) {
        return (Integer) readValue(context, "color_favorites_dock_bg_color_argb", Integer.class, fallback);
    }
    public static int favoritesBackgroundOpacity(Context context) {
        int value = (Integer) readValue(context, "color_favorites_dock_bg_color_opacity", Integer.class, 100);
        if (value < 0 || value > 100) throw new IllegalStateException("Saved Favorites opacity is invalid. Its value was kept.");
        return value;
    }
    public static void setFavoritesBackgroundDefault(Context context) {
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("favorites_bg_mode", "Default");
        changes.put("hide_favorites_background", false);
        saveValues(context, changes);
    }
    public static void setFavoritesBackgroundHide(Context context) {
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("favorites_bg_mode", "Hide");
        changes.put("hide_favorites_background", true);
        saveValues(context, changes);
    }
    public static void setFavoritesBackgroundColor(Context context, int argb, int opacity) {
        if (opacity < 0 || opacity > 100) throw new IllegalArgumentException("Opacity must be between 0 and 100.");
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("favorites_bg_mode", "Color");
        changes.put("hide_favorites_background", false);
        changes.put("color_favorites_dock_bg_color_argb", argb);
        changes.put("color_favorites_dock_bg_color_opacity", opacity);
        saveValues(context, changes);
    }

    private static Object readValue(Context context, String key, Class<?> type, Object fallback) {
        synchronized (PresentationState.class) {
            HolidayPresetTransaction.assertReadable(context);
            Map<String, ?> values = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).getAll();
            Object value = values.get(key);
            if (values.containsKey(key) && !type.isInstance(value))
                throw new IllegalStateException("Saved Home setting could not be read. Its value was kept.");
            return value == null ? fallback : value;
        }
    }
    private static void saveValues(Context context, Map<String, Object> changes) {
        synchronized (PresentationState.class) {
            HolidayPresetTransaction.assertWritable(context);
            SharedPreferences prefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE);
            Map<String, ?> before = prefs.getAll();
            for (Map.Entry<String, Object> entry : changes.entrySet()) {
                Object previous = before.get(entry.getKey());
                if (before.containsKey(entry.getKey()) && !entry.getValue().getClass().isInstance(previous))
                    throw new IllegalStateException("Saved Home setting could not be read. Its value was kept.");
            }
            SharedPreferences.Editor edit = prefs.edit();
            for (Map.Entry<String, Object> entry : changes.entrySet()) put(edit, entry.getKey(), entry.getValue());
            if (edit.commit()) return;
            SharedPreferences.Editor restore = prefs.edit();
            for (String key : changes.keySet()) put(restore, key, before.get(key));
            restore.commit();
            throw new IllegalStateException("Home setting could not be saved. Try again.");
        }
    }
    private static void put(SharedPreferences.Editor editor, String key, Object value) {
        if (value == null) editor.remove(key);
        else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else editor.putString(key, (String) value);
    }


    private static boolean read(Context context, String key, boolean fallback) {
        synchronized (PresentationState.class) {
            HolidayPresetTransaction.assertReadable(context);
            Map<String, ?> values = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).getAll();
            Object value = values.get(key);
            if (values.containsKey(key) && !(value instanceof Boolean))
                throw new IllegalStateException("Saved Home setting could not be read. Its value was kept.");
            return value == null ? fallback : (Boolean) value;
        }
    }

    private static void save(Context context, String key, boolean enabled) {
        synchronized (PresentationState.class) {
            HolidayPresetTransaction.assertWritable(context);
            SharedPreferences prefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE);
            Map<String, ?> values = prefs.getAll();
            Object previous = values.get(key);
            if (values.containsKey(key) && !(previous instanceof Boolean))
                throw new IllegalStateException("Saved Home setting could not be read. Its value was kept.");
            if (prefs.edit().putBoolean(key, enabled).commit()) return;
            SharedPreferences.Editor restore = prefs.edit();
            restoreBoolean(restore, key, previous);
            restore.commit();
            throw new IllegalStateException("Home setting could not be saved. Try again.");
        }
    }

    private static void restoreBoolean(SharedPreferences.Editor editor, String key, Object value) {
        if (value == null) editor.remove(key); else editor.putBoolean(key, (Boolean) value);
    }
}
