package com.jeremykenedy.firetv.ui.nativeapp.widgetgroups;

import android.content.Context;
import android.content.SharedPreferences;

/** Owns only the grouped-layout key and never clears other Home settings. */
public final class WidgetGroupsStore {
    private static final Object LOCK = new Object();
    private final SharedPreferences preferences;
    public WidgetGroupsStore(Context context) {
        preferences = context.getSharedPreferences("native_layout", Context.MODE_PRIVATE);
    }
    public String raw() {
        Object value = preferences.getAll().get(WidgetGroupMigration.KEY);
        if (value == null && !preferences.contains(WidgetGroupMigration.KEY)) return null;
        if (!(value instanceof String)) throw new IllegalStateException("Saved widget groups could not be read. Their data was kept.");
        return (String) value;
    }
    public WidgetGroups load() {
        String raw = raw();
        if (raw == null) return null;
        try { return WidgetGroups.decode(raw); }
        catch (IllegalArgumentException invalid) { throw new IllegalStateException("Saved widget groups could not be read. Their data was kept.", invalid); }
    }
    public boolean save(String expected, WidgetGroups value) {
        if (value == null) throw new IllegalArgumentException("Missing widget groups.");
        synchronized (LOCK) {
            String current = raw();
            if (!(current == null ? expected == null : current.equals(expected))) return false;
            String next = value.encode();
            if (next.equals(current)) return true;
            if (preferences.edit().putString(WidgetGroupMigration.KEY, next).commit()) return true;
            // SharedPreferences can change its in-memory value even when commit fails.
            if (next.equals(raw())) {
                SharedPreferences.Editor rollback = preferences.edit();
                if (current == null) rollback.remove(WidgetGroupMigration.KEY); else rollback.putString(WidgetGroupMigration.KEY, current);
                if (!rollback.commit()) throw new IllegalStateException("Widget save recovery could not be verified.");
                String restored = raw();
                if (!(current == null ? restored == null : current.equals(restored)))
                    throw new IllegalStateException("Widget save recovery could not be verified.");
            }
            return false;
        }
    }
}
