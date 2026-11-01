package com.jeremykenedy.firetv.ui.nativeapp.focus;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Map;

public final class FocusPreferences {
    private static final String FILE = "native_focus";
    private static final String[] KEYS = {"duration_millis", "growth_percent", "border_enabled", "border_rgb", "border_opacity"};

    private FocusPreferences() {}

    public static FocusOptions load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        FocusOptions defaults = FocusOptions.defaults();
        return new FocusOptions(number(prefs, KEYS[0], defaults.durationMillis, 500),
                number(prefs, KEYS[1], defaults.growthPercent, FocusOptions.MAX_GROWTH),
                enabled(prefs), number(prefs, KEYS[3], defaults.borderRgb, 0xffffff),
                number(prefs, KEYS[4], defaults.borderOpacity, 100));
    }

    private static int number(SharedPreferences prefs, String key, int fallback, int max) {
        try {
            int value = prefs.getInt(key, fallback);
            return value >= 0 && value <= max ? value : fallback;
        } catch (ClassCastException invalid) { return fallback; }
    }

    private static boolean enabled(SharedPreferences prefs) {
        try { return prefs.getBoolean(KEYS[2], true); }
        catch (ClassCastException invalid) { return true; }
    }

    public static void save(Context context, FocusOptions options) {
        SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        Map<String, ?> previous = prefs.getAll();
        if (prefs.edit().putInt(KEYS[0], options.durationMillis)
                .putInt(KEYS[1], options.growthPercent)
                .putBoolean(KEYS[2], options.borderEnabled)
                .putInt(KEYS[3], options.borderRgb)
                .putInt(KEYS[4], options.borderOpacity).commit()) return;
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
