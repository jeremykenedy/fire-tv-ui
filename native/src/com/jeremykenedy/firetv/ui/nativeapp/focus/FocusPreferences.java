package com.jeremykenedy.firetv.ui.nativeapp.focus;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Map;

public final class FocusPreferences {
    private static final String FILE = "native_focus";
    public static final String APP_STYLE = "app_focus_style";
    private static final String[] KEYS = {"duration_millis", "growth_percent", "border_enabled", "border_rgb", "border_opacity",
            "fill_enabled", "fill_rgb", "fill_opacity"};

    private FocusPreferences() {}

    public static FocusOptions load(Context context) { synchronized(com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState.class) {
        com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction.assertReadable(context);
        SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        FocusOptions defaults = FocusOptions.defaults();
        return new FocusOptions(number(prefs, KEYS[0], defaults.durationMillis, 500),
                number(prefs, KEYS[1], defaults.growthPercent, FocusOptions.MAX_GROWTH),
                enabled(prefs, KEYS[2]), number(prefs, KEYS[3], defaults.borderRgb, 0xffffff),
                number(prefs, KEYS[4], defaults.borderOpacity, 100),
                enabled(prefs, KEYS[5]), number(prefs, KEYS[6], defaults.fillRgb, 0xffffff),
                number(prefs, KEYS[7], defaults.fillOpacity, 100));
    } }

    public static boolean hasExplicitSettings(Context context) { synchronized(com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState.class) {
        com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction.assertReadable(context);
        Map<String, ?> values = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getAll();
        return FocusOptions.hasExplicitSettings(values);
    } }

    public static boolean legacyDefaultAppMode(Context context) { synchronized(com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState.class) {
        com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction.assertReadable(context);
        Map<String, ?> values = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getAll();
        Object style = values.get(APP_STYLE);
        if (style instanceof String) return "default".equals(style);
        if (style != null) return false;
        return !Boolean.FALSE.equals(values.get(FocusOptions.LEGACY_APP_DEFAULT))
                && !FocusOptions.hasExplicitSettings(values);
    } }

    public static boolean hasExplicitGrowth(Context context) {
        Object value = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getAll().get("growth_percent");
        return value instanceof Integer && (Integer) value >= 0 && (Integer) value <= FocusOptions.MAX_GROWTH;
    }

    public static String appStyle(Context context) {
        Map<String, ?> values = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getAll();
        Object value = values.get(APP_STYLE);
        if ("default".equals(value)) return "default";
        if (value != null && !"border".equals(value) && !"color".equals(value)) return "custom";
        if (value == null && legacyDefaultAppMode(context)) return "default";
        FocusOptions options = load(context);
        if (options.borderEnabled && !options.fillEnabled) return "border";
        if (options.borderEnabled && options.fillEnabled) return "color";
        return "custom";
    }

    public static void setAppStyle(Context context, String style) { synchronized(com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState.class) {
        if (!"default".equals(style) && !"border".equals(style) && !"color".equals(style))
            throw new IllegalArgumentException("Choose a supported focus style.");
        com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction.assertWritable(context);
        SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        Map<String, ?> previous = prefs.getAll();
        SharedPreferences.Editor edit = prefs.edit().putString(APP_STYLE, style);
        if (!"default".equals(style)) {
            edit.putBoolean("border_enabled", true).putBoolean("fill_enabled", "color".equals(style));
            if ("color".equals(style) && !previous.containsKey("fill_rgb")) edit.putInt("fill_rgb", 0x263a5c);
        }
        if (edit.commit()) return;
        SharedPreferences.Editor rollback = prefs.edit();
        restore(rollback, APP_STYLE, previous.get(APP_STYLE));
        restore(rollback, "border_enabled", previous.get("border_enabled"));
        restore(rollback, "fill_enabled", previous.get("fill_enabled"));
        restore(rollback, "fill_rgb", previous.get("fill_rgb"));
        rollback.commit();
        throw new IllegalStateException("Focus style could not be saved. Try again.");
    } }

    public static void saveMotion(Context context, String key, int value) { synchronized(com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState.class) {
        if (!("growth_percent".equals(key) && value >= 0 && value <= FocusOptions.MAX_GROWTH)
                && !("duration_millis".equals(key) && value >= 0 && value <= 500))
            throw new IllegalArgumentException("Choose a supported focus setting.");
        com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction.assertWritable(context);
        SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        Map<String, ?> previous = prefs.getAll();
        boolean preserveDefault = !previous.containsKey(APP_STYLE) && legacyDefaultAppMode(context);
        SharedPreferences.Editor edit = prefs.edit().putInt(key, value);
        if (preserveDefault) edit.putString(APP_STYLE, "default");
        if (edit.commit()) return;
        SharedPreferences.Editor rollback = prefs.edit();
        restore(rollback, key, previous.get(key));
        restore(rollback, APP_STYLE, previous.get(APP_STYLE));
        rollback.commit();
        throw new IllegalStateException("Focus settings could not be saved. Try again.");
    } }

    public static int appColor(Context context, boolean fill) {
        Map<String, ?> values = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getAll();
        if (fill && !values.containsKey("fill_rgb") && legacyDefaultAppMode(context)) return 0x263a5c;
        FocusOptions options = load(context);
        return fill ? options.fillRgb : options.borderRgb;
    }

    public static void saveAppColorAndOpacity(Context context, boolean fill, int argb, int opacity) { synchronized(com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState.class) {
        if (opacity < 0 || opacity > 100) throw new IllegalArgumentException("Choose an opacity from 0 through 100.");
        com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction.assertWritable(context);
        SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        Map<String, ?> previous = prefs.getAll();
        String colorKey = fill ? "fill_rgb" : "border_rgb";
        String opacityKey = fill ? "fill_opacity" : "border_opacity";
        boolean preserveDefault = !previous.containsKey(APP_STYLE) && legacyDefaultAppMode(context);
        SharedPreferences.Editor edit = prefs.edit().putInt(colorKey, argb & 0xffffff).putInt(opacityKey, opacity);
        if (preserveDefault) edit.putString(APP_STYLE, "default");
        if (edit.commit()) return;
        SharedPreferences.Editor rollback = prefs.edit();
        restore(rollback, colorKey, previous.get(colorKey));
        restore(rollback, opacityKey, previous.get(opacityKey));
        restore(rollback, APP_STYLE, previous.get(APP_STYLE));
        rollback.commit();
        throw new IllegalStateException("Focus color could not be saved. Try again.");
    } }

    public static boolean appBorder(Context context) {
        Map<String, ?> values = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getAll();
        Object value = values.get("border_enabled");
        return value instanceof Boolean ? (Boolean) value : !legacyDefaultAppMode(context) && load(context).borderEnabled;
    }

    public static void setAppBorder(Context context, boolean enabled) { synchronized(com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState.class) {
        com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction.assertWritable(context);
        SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        Map<String, ?> previous = prefs.getAll();
        boolean preserveDefault = !previous.containsKey(APP_STYLE) && legacyDefaultAppMode(context);
        SharedPreferences.Editor edit = prefs.edit().putBoolean("border_enabled", enabled);
        if (preserveDefault) edit.putString(APP_STYLE, "default");
        if (edit.commit()) return;
        SharedPreferences.Editor rollback = prefs.edit();
        restore(rollback, "border_enabled", previous.get("border_enabled"));
        restore(rollback, APP_STYLE, previous.get(APP_STYLE));
        rollback.commit();
        throw new IllegalStateException("Focus border could not be saved. Try again.");
    } }

    public static boolean iconShadow(Context context) {
        Object value = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).getAll().get("icon_shadow");
        if (value == null) return true;
        if (!(value instanceof Boolean)) throw new IllegalStateException("Saved icon shadow setting is unavailable.");
        return (Boolean) value;
    }

    public static int iconRadius(Context context) {
        Object value = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).getAll().get("icon_radius");
        if (value == null) return 16;
        if (!(value instanceof Integer) || (Integer) value < 0 || (Integer) value > 20)
            throw new IllegalStateException("Saved icon radius is unavailable.");
        return (Integer) value;
    }

    public static int iconBackgroundColor(Context context) {
        Object value = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).getAll().get("color_back_color_argb");
        return value instanceof Integer ? (Integer) value : 0xffffffff;
    }

    public static int iconBackgroundOpacity(Context context) {
        Object value = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).getAll().get("color_back_color_opacity");
        return value instanceof Integer && (Integer) value >= 0 && (Integer) value <= 100 ? (Integer) value : 0;
    }

    public static void saveIconOption(Context context, String key, Object value) { synchronized(com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState.class) {
        if (!("icon_shadow".equals(key) && value instanceof Boolean)
                && !("icon_radius".equals(key) && value instanceof Integer && (Integer) value >= 0 && (Integer) value <= 20))
            throw new IllegalArgumentException("Choose a supported icon setting.");
        com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction.assertWritable(context);
        SharedPreferences prefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE);
        Map<String, ?> previous = prefs.getAll();
        SharedPreferences.Editor edit = prefs.edit();
        restore(edit, key, value);
        if (edit.commit()) return;
        SharedPreferences.Editor rollback = prefs.edit(); restore(rollback, key, previous.get(key)); rollback.commit();
        throw new IllegalStateException("Icon setting could not be saved. Try again.");
    } }

    private static int number(SharedPreferences prefs, String key, int fallback, int max) {
        try {
            int value = prefs.getInt(key, fallback);
            return value >= 0 && value <= max ? value : fallback;
        } catch (ClassCastException invalid) { return fallback; }
    }

    private static boolean enabled(SharedPreferences prefs, String key) {
        try { return prefs.getBoolean(key, true); }
        catch (ClassCastException invalid) { return true; }
    }

    public static void save(Context context, FocusOptions options) { synchronized(com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState.class) {
        com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction.assertWritable(context);
        SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        Map<String, ?> previous = prefs.getAll();
        if (prefs.edit().putInt(KEYS[0], options.durationMillis)
                .putInt(KEYS[1], options.growthPercent)
                .putBoolean(KEYS[2], options.borderEnabled)
                .putInt(KEYS[3], options.borderRgb)
                .putInt(KEYS[4], options.borderOpacity)
                .putBoolean(KEYS[5], options.fillEnabled)
                .putInt(KEYS[6], options.fillRgb)
                .putInt(KEYS[7], options.fillOpacity)
                .remove(APP_STYLE)
                .remove(FocusOptions.LEGACY_APP_DEFAULT).commit()) return;
        SharedPreferences.Editor rollback = prefs.edit();
        for (String key : KEYS) restore(rollback, key, previous.get(key));
        restore(rollback, FocusOptions.LEGACY_APP_DEFAULT, previous.get(FocusOptions.LEGACY_APP_DEFAULT));
        restore(rollback, APP_STYLE, previous.get(APP_STYLE));
        rollback.commit();
        throw new IllegalStateException("Focus settings could not be saved. Try again.");
    } }
    private static void restore(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof String) editor.putString(key, (String) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof java.util.Set) {
            java.util.Set<String> values = new java.util.LinkedHashSet<>();
            for (Object item : (java.util.Set<?>) value) values.add((String) item);
            editor.putStringSet(key, values);
        } else editor.remove(key);
    }

}
