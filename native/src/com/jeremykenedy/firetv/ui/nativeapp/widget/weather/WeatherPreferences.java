package com.jeremykenedy.firetv.ui.nativeapp.widget.weather;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WeatherPreferences {
    public static final String FILE = "native_weather";
    private WeatherPreferences() {}
    public static WeatherOptions load(Context context) {
        Map<String, ?> saved = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getAll();
        Map<String, Object> values = new LinkedHashMap<>();
        boolean present = false;
        for (String key : WeatherOptions.defaults().backupValues().keySet()) if (saved.containsKey(key)) { values.put(key, saved.get(key)); present = true; }
        if (!present) {
            Map<String, ?> legacy = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).getAll();
            String locationKey = "status_bar_weather_loc", unitKey = "status_bar_weather_unit";
            if (!legacy.containsKey(locationKey) && !legacy.containsKey(unitKey)) return WeatherOptions.defaults();
            Object location = legacy.get(locationKey), unit = legacy.get(unitKey);
            if ((legacy.containsKey(locationKey) && !(location instanceof String))
                    || (legacy.containsKey(unitKey) && !(unit instanceof String)))
                throw new IllegalStateException("Saved legacy weather choices are unreadable. They have been kept.");
            try { return WeatherOptions.fromLegacy((String) location, (String) unit); }
            catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("Saved legacy weather choices need review. They have been kept.");
            }
        }
        try { return WeatherOptions.fromBackup(values); }
        catch (IllegalArgumentException invalid) { throw new IllegalStateException("Saved weather choices are unreadable. They have been kept."); }
    }
    public static void save(Context context, WeatherOptions options) {
        SharedPreferences preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        Map<String, ?> before = preferences.getAll(); Map<String, Object> values = options.backupValues();
        SharedPreferences.Editor edit = preferences.edit();
        for (Map.Entry<String, Object> value : values.entrySet()) put(edit, value.getKey(), value.getValue());
        if (edit.commit()) return;
        SharedPreferences.Editor restore = preferences.edit();
        for (String key : values.keySet()) if (before.containsKey(key)) put(restore, key, before.get(key)); else restore.remove(key);
        restore.commit(); throw new IllegalStateException("Weather choices could not be saved. Try again.");
    }
    public static void restore(Context context, Map<String, ?> values) { save(context, WeatherOptions.fromBackup(values)); }
    @SuppressWarnings("unchecked") private static void put(SharedPreferences.Editor edit, String key, Object value) {
        if (value instanceof String) edit.putString(key, (String) value);
        else if (value instanceof Integer) edit.putInt(key, (Integer) value);
        else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
        else if (value instanceof Long) edit.putLong(key, (Long) value);
        else if (value instanceof Float) edit.putFloat(key, (Float) value);
        else if (value instanceof java.util.Set) edit.putStringSet(key, (java.util.Set<String>) value);
    }
}
