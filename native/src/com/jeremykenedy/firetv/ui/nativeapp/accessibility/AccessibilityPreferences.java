package com.jeremykenedy.firetv.ui.nativeapp.accessibility;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Map;

public final class AccessibilityPreferences {
    public static final String FILE = "source_accessibility";
    private AccessibilityPreferences() {}

    public static SharedPreferences file(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static int textPercent(Context context) {
        Object value = file(context).getAll().get("text_percent");
        return value instanceof Integer && validPercent((Integer) value) ? (Integer) value : 100;
    }

    public static boolean highContrast(Context context) {
        return Boolean.TRUE.equals(file(context).getAll().get("high_contrast"));
    }

    public static void save(Context context, int percent, boolean contrast) {
        if (!validPercent(percent)) throw new IllegalArgumentException("Choose Default, Larger or Largest text.");
        SharedPreferences prefs = file(context);
        Map<String, ?> previous = prefs.getAll();
        if (prefs.edit().putInt("text_percent", percent).putBoolean("high_contrast", contrast).commit()) return;
        SharedPreferences.Editor rollback = prefs.edit();
        restore(rollback, "text_percent", previous.get("text_percent"));
        restore(rollback, "high_contrast", previous.get("high_contrast"));
        rollback.commit();
        throw new IllegalStateException("Readability settings could not be saved. Try again.");
    }

    public static void validateSnapshot(Map<String, ?> values) {
        Object percent = values.get("text_percent");
        if (values.containsKey("text_percent") && (!(percent instanceof Integer) || !validPercent((Integer) percent))) {
            throw new IllegalArgumentException("Invalid text size.");
        }
        if (values.containsKey("high_contrast") && !(values.get("high_contrast") instanceof Boolean)) {
            throw new IllegalArgumentException("Invalid contrast setting.");
        }
    }

    private static boolean validPercent(int value) { return value == 100 || value == 110 || value == 120; }

    private static void restore(SharedPreferences.Editor edit, String key, Object value) {
        if (value instanceof Integer) edit.putInt(key, (Integer) value);
        else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
        else if (value instanceof String) edit.putString(key, (String) value);
        else if (value instanceof Long) edit.putLong(key, (Long) value);
        else if (value instanceof Float) edit.putFloat(key, (Float) value);
        else if (value instanceof java.util.Set) {
            @SuppressWarnings("unchecked") java.util.Set<String> values = (java.util.Set<String>) value;
            edit.putStringSet(key, values);
        }
        else edit.remove(key);
    }
}
