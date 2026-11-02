package com.jeremykenedy.firetv.ui.nativeapp.widget.radar;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Map;

public final class RadarPreferences {
    public static final String FILE = "native_radar";
    private RadarPreferences() {}
    public static RadarSource load(Context context) {
        Object saved = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getAll().get("source");
        if (saved == null || saved.equals("")) return null;
        RadarSource source = saved instanceof String ? RadarSource.find((String) saved) : null;
        if (source == null) throw new IllegalStateException("Saved radar area is unreadable. Choose a region or station again.");
        return source;
    }
    public static void validateSnapshot(Map<String, ?> values) {
        if (!values.containsKey("source")) return;
        Object value = values.get("source");
        if (!(value instanceof String) || (!value.equals("") && RadarSource.find((String) value) == null)) throw new IllegalArgumentException("Unknown radar area");
    }
    public static void save(Context context, String id) {
        if (id == null || (!id.isEmpty() && RadarSource.find(id) == null)) throw new IllegalArgumentException("Choose a supported radar region or station.");
        SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        Object before = prefs.getAll().get("source");
        if (prefs.edit().putString("source", id).commit()) return;
        SharedPreferences.Editor rollback = prefs.edit();
        if (before instanceof String) rollback.putString("source", (String) before);
        else if (before instanceof Integer) rollback.putInt("source", (Integer) before);
        else if (before instanceof Boolean) rollback.putBoolean("source", (Boolean) before);
        else if (before instanceof Long) rollback.putLong("source", (Long) before);
        else if (before instanceof Float) rollback.putFloat("source", (Float) before);
        else if (before instanceof java.util.Set) {
            @SuppressWarnings("unchecked") java.util.Set<String> values = (java.util.Set<String>) before;
            rollback.putStringSet("source", values);
        } else rollback.remove("source");
        rollback.commit(); throw new IllegalStateException("Radar area could not be saved. Try again.");
    }
}
