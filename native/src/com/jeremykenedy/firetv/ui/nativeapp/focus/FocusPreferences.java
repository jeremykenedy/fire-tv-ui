package com.jeremykenedy.firetv.ui.nativeapp.focus;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Map;

public final class FocusPreferences {
    private static final String FILE = "native_focus";
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
        return !Boolean.FALSE.equals(values.get(FocusOptions.LEGACY_APP_DEFAULT))
                && !FocusOptions.hasExplicitSettings(values);
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
                .remove(FocusOptions.LEGACY_APP_DEFAULT).commit()) return;
        SharedPreferences.Editor rollback = prefs.edit();
        for (String key : KEYS) restore(rollback, key, previous.get(key));
        restore(rollback, FocusOptions.LEGACY_APP_DEFAULT, previous.get(FocusOptions.LEGACY_APP_DEFAULT));
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
