package com.jeremykenedy.firetv.ui.nativeapp.cards;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CardPreferences {
    private static final String FILE = "native_cards";
    private CardPreferences() {}

    public static CardOptions load(Context context) {
        CardOptions defaults = CardOptions.defaults();
        Map<String, ?> stored = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getAll();
        Map<String, Object> values = new LinkedHashMap<>(defaults.backupValues());
        for (String key : values.keySet()) if (stored.containsKey(key)) values.put(key, stored.get(key));
        try { return CardOptions.fromBackup(values); }
        catch (IllegalArgumentException invalid) { return defaults; }
    }

    public static void restore(Context context, Map<String, ?> backup) {
        save(context, CardOptions.fromBackup(backup));
    }

    public static void save(Context context, CardOptions options) {
        SharedPreferences preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        Map<String, ?> previous = preferences.getAll();
        Map<String, Object> values = options.backupValues();
        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, Object> entry : values.entrySet()) put(editor, entry.getKey(), entry.getValue());
        if (editor.commit()) return;
        SharedPreferences.Editor rollback = preferences.edit();
        for (String key : values.keySet()) {
            if (previous.containsKey(key)) put(rollback, key, previous.get(key));
            else rollback.remove(key);
        }
        rollback.commit();
        throw new IllegalStateException("The card settings could not be saved. Please try again.");
    }

    @SuppressWarnings("unchecked")
    private static void put(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof String) editor.putString(key, (String) value);
        else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof java.util.Set) editor.putStringSet(key, (java.util.Set<String>) value);
        else editor.remove(key);
    }
}
