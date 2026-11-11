package com.jeremykenedy.firetv.ui.nativeapp.appgrid;

import android.content.Context;
import android.content.SharedPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState;
import com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class AppGridPreferences {
    private AppGridPreferences() {}
    public static AppGridOptions load(Context context) {
        synchronized (PresentationState.class) {
            SharedPreferences prefs = context.getSharedPreferences("native_app_grid", Context.MODE_PRIVATE);
            try {
                return new AppGridOptions(prefs.getInt("columns", 6), prefs.getInt("rows", 1),
                        prefs.getBoolean("horizontal", false), split(prefs.getString("order", "")),
                        new LinkedHashSet<>(split(prefs.getString("hidden", ""))),
                        new LinkedHashSet<>(split(prefs.getString("favorites", ""))));
            } catch (RuntimeException malformed) { return AppGridOptions.defaults(); }
        }
    }
    private static List<String> split(String value) {
        return value.isEmpty() ? Collections.<String>emptyList() : Arrays.asList(value.split("\n", -1));
    }
    public static void saveDiscoveredOrder(Context context, List<String> available) {
        synchronized (PresentationState.class) {
            HolidayPresetTransaction.assertWritable(context);
            SharedPreferences prefs = context.getSharedPreferences("native_app_grid", Context.MODE_PRIVATE);
            Map<String, ?> previous = prefs.getAll();
            Object stored = previous.get("order");
            if (previous.containsKey("order") && !(stored instanceof String))
                throw new IllegalStateException("Saved app order could not be read. Its stored data has been kept.");
            String order = stored == null ? "" : (String) stored;
            final String discovered;
            try {
                AppGridOptions current = new AppGridOptions(5, 0, false, split(order),
                        Collections.<String>emptySet(), Collections.<String>emptySet());
                discovered = String.join("\n", current.discover(available).order);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("Saved app order could not be read. Its stored data has been kept.", invalid);
            }
            if (order.equals(discovered)) return;
            if (prefs.edit().putString("order", discovered).commit()) return;
            SharedPreferences.Editor restore = prefs.edit();
            if (stored == null) restore.remove("order"); else restore.putString("order", order);
            restore.commit();
            throw new IllegalStateException("App order could not be saved. Please try again.");
        }
    }

    public static void save(Context context, AppGridOptions value) {
        synchronized (PresentationState.class) {
            HolidayPresetTransaction.assertWritable(context);
            SharedPreferences prefs = context.getSharedPreferences("native_app_grid", Context.MODE_PRIVATE);
            Map<String, ?> before = prefs.getAll();
            if (prefs.edit().putInt("columns", value.columns).putInt("rows", value.rows)
                    .putBoolean("horizontal", value.horizontal).putString("order", String.join("\n", value.order))
                    .putString("hidden", String.join("\n", value.hidden))
                    .putString("favorites", String.join("\n", value.favorites)).commit()) return;
            SharedPreferences.Editor restore = prefs.edit();
            for (String key : Arrays.asList("columns", "rows", "horizontal", "order", "hidden", "favorites")) {
                Object previous = before.get(key);
                if (previous == null) restore.remove(key);
                else if (previous instanceof Integer) restore.putInt(key, (Integer) previous);
                else if (previous instanceof Boolean) restore.putBoolean(key, (Boolean) previous);
                else if (previous instanceof String) restore.putString(key, (String) previous);
                else throw new IllegalStateException("Saved app choices have an unsupported type. They were kept.");
            }
            boolean restored = restore.commit() && before.equals(prefs.getAll());
            throw new IllegalStateException(restored ? "App choices could not be saved. Previous choices were kept."
                    : "App choice recovery could not be verified. Reopen Home before editing them.");
        }
    }
}
