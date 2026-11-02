package com.jeremykenedy.firetv.ui.nativeapp.layout;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LayoutPreferences {
    private static final String FILE = "native_layout";
    private LayoutPreferences() {}

    public static LayoutOptions load(Context context, LayoutOptions defaults) {
        Map<String, ?> stored = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getAll();
        Map<String, Object> values = new LinkedHashMap<>(defaults.backupValues());
        for (String key : values.keySet()) if (stored.containsKey(key)) values.put(key, stored.get(key));
        try { return LayoutOptions.fromBackup(values); }
        catch (IllegalArgumentException invalid) { return defaults; }
    }

    public static boolean widgetsEnabled(Context context, LayoutOptions options) {
        if (!options.includes(LayoutItem.Kind.WIDGET)) return false;
        if (context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getAll().containsKey("mode")) return true;
        for (String key : options.order) {
            if (key.startsWith("widget:") && !options.hidden.contains(key)) return true;
        }
        return false;
    }

    public static LayoutOptions.Mode toggleWidgetsMode(Context context, LayoutOptions options) {
        boolean cards = options.includes(LayoutItem.Kind.CHANNEL_CARD);
        return widgetsEnabled(context, options) ? (cards ? LayoutOptions.Mode.CARDS_ONLY : LayoutOptions.Mode.NONE)
                : (cards ? LayoutOptions.Mode.BOTH : LayoutOptions.Mode.WIDGETS_ONLY);
    }

    public static void restore(Context context, Map<String, ?> backup) {
        save(context, LayoutOptions.fromBackup(backup));
    }

    public static void save(Context context, LayoutOptions options) {
        write(context, options.backupValues());
    }

    /** Discovery records new slots without turning absent presentation defaults into explicit choices. */
    public static void saveDiscovery(Context context, LayoutOptions before, LayoutOptions discovered) {
        Map<String, Object> changed = new LinkedHashMap<>();
        Map<String, Object> values = discovered.backupValues();
        if (!before.order.equals(discovered.order)) changed.put("order", values.get("order"));
        if (!before.hidden.equals(discovered.hidden)) changed.put("hidden", values.get("hidden"));
        if (!changed.isEmpty()) write(context, changed);
    }

    private static void write(Context context, Map<String, Object> values) {
        SharedPreferences preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        Map<String, ?> previous = preferences.getAll();
        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, Object> entry : values.entrySet()) put(editor, entry.getKey(), entry.getValue());
        if (editor.commit()) return;
        SharedPreferences.Editor rollback = preferences.edit();
        for (String key : values.keySet()) {
            if (previous.containsKey(key)) put(rollback, key, previous.get(key));
            else rollback.remove(key);
        }
        rollback.commit();
        throw new IllegalStateException("The layout could not be saved. Please try again.");
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
