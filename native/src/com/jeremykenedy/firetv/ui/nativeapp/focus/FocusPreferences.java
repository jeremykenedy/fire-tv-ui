package com.jeremykenedy.firetv.ui.nativeapp.focus;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Map;

public final class FocusPreferences {
    private static final String FILE = "native_focus";
    private static final String[] KEYS = {"duration_millis", "growth_percent", "border_enabled", "border_rgb", "border_opacity",
            "fill_enabled", "fill_rgb", "fill_opacity"};

    private FocusPreferences() {}

    public static FocusOptions load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        FocusOptions defaults = FocusOptions.defaults();
        return new FocusOptions(number(prefs, KEYS[0], defaults.durationMillis, 500),
                number(prefs, KEYS[1], defaults.growthPercent, FocusOptions.MAX_GROWTH),
                enabled(prefs, KEYS[2]), number(prefs, KEYS[3], defaults.borderRgb, 0xffffff),
                number(prefs, KEYS[4], defaults.borderOpacity, 100),
                enabled(prefs, KEYS[5]), number(prefs, KEYS[6], defaults.fillRgb, 0xffffff),
                number(prefs, KEYS[7], defaults.fillOpacity, 100));
    }

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

    public static void save(Context context, FocusOptions options) {
        SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        Map<String, ?> previous = prefs.getAll();
        if (prefs.edit().putInt(KEYS[0], options.durationMillis)
                .putInt(KEYS[1], options.growthPercent)
                .putBoolean(KEYS[2], options.borderEnabled)
                .putInt(KEYS[3], options.borderRgb)
                .putInt(KEYS[4], options.borderOpacity)
                .putBoolean(KEYS[5], options.fillEnabled)
                .putInt(KEYS[6], options.fillRgb)
                .putInt(KEYS[7], options.fillOpacity).commit()) return;
        SharedPreferences.Editor rollback = prefs.edit();
        for (String key : KEYS) {
            Object value = previous.get(key);
            if (value instanceof Integer) rollback.putInt(key, (Integer) value);
            else if (value instanceof Boolean) rollback.putBoolean(key, (Boolean) value);
            else if (value instanceof String) rollback.putString(key, (String) value);
            else rollback.remove(key);
        }
        rollback.commit();
        throw new IllegalStateException("Focus settings could not be saved. Try again.");
    }
}
