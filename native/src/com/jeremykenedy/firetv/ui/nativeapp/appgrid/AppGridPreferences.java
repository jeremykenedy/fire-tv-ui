package com.jeremykenedy.firetv.ui.nativeapp.appgrid;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public final class AppGridPreferences {
    private AppGridPreferences() {}
    public static AppGridOptions load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("native_app_grid", Context.MODE_PRIVATE);
        try {
            return new AppGridOptions(prefs.getInt("columns", 5), prefs.getInt("rows", 0),
                    prefs.getBoolean("horizontal", false), split(prefs.getString("order", "")),
                    new LinkedHashSet<>(split(prefs.getString("hidden", ""))),
                    new LinkedHashSet<>(split(prefs.getString("favorites", ""))));
        } catch (RuntimeException malformed) { return AppGridOptions.defaults(); }
    }
    private static List<String> split(String value) {
        return value.isEmpty() ? Collections.<String>emptyList() : Arrays.asList(value.split("\n", -1));
    }
    public static void save(Context context, AppGridOptions value) {
        SharedPreferences prefs = context.getSharedPreferences("native_app_grid", Context.MODE_PRIVATE);
        prefs.edit().putInt("columns", value.columns).putInt("rows", value.rows)
                .putBoolean("horizontal", value.horizontal).putString("order", String.join("\n", value.order))
                .putString("hidden", String.join("\n", value.hidden))
                .putString("favorites", String.join("\n", value.favorites)).apply();
    }
}
