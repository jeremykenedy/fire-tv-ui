package com.jeremykenedy.firetv.ui;

import android.content.SharedPreferences;
import android.app.Activity;
import java.util.WeakHashMap;

public final class LauncherLayout {
    static final String COLUMNS = "apps_per_row";
    static final int DEFAULT_COLUMNS = 5;
    static final int[] PRESETS = {3, 4, 5, 6, 7, 8, 10, 12};
    static final String DIRECTION = "app_scroll_direction";
    static final String MAX_ROWS = "app_max_rows";
    private static SharedPreferences current;
    private static final WeakHashMap<Activity, String> layouts = new WeakHashMap<>();

    private LauncherLayout() {}

    static int parseColumns(String value) {
        int columns;
        try {
            columns = Integer.parseInt(value.trim());
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Enter a whole number from 3 to 12.");
        }
        if (columns < 3 || columns > 12) {
            throw new IllegalArgumentException("Choose between 3 and 12 apps per row.");
        }
        return columns;
    }

    public static Integer columns(SharedPreferences preferences) {
        int value = preferences.getInt(COLUMNS, DEFAULT_COLUMNS);
        return Integer.valueOf(value > 0 && value <= 20 ? value : DEFAULT_COLUMNS);
    }

    public static void created(Activity activity) {
        current = DeviceSettings.prefs(activity);
        layouts.put(activity, signature(current));
    }

    public static void resumed(Activity activity) {
        current = DeviceSettings.prefs(activity);
        String selected = signature(current);
        String previous = layouts.put(activity, selected);
        if (previous != null && !previous.equals(selected)) activity.recreate();
    }

    private static String signature(SharedPreferences preferences) {
        return horizontal(preferences) + ":" + maximumRows(preferences);
    }

    static boolean horizontal(SharedPreferences preferences) {
        return "horizontal".equals(preferences.getString(DIRECTION, "vertical"));
    }

    public static boolean horizontal() {
        return current != null && horizontal(current);
    }

    public static boolean expandHomeApps() {
        return horizontal() && !current.getBoolean("show_all_apps_button", false);
    }

    public static boolean multipleHomeRows() {
        return expandHomeApps() && maximumRows(current) > 1;
    }

    public static float homeSpacer(float height) {
        return multipleHomeRows() ? 0f : height;
    }

    static int maximumRows(SharedPreferences preferences) {
        int rows = preferences.getInt(MAX_ROWS, 0);
        return rows >= 1 && rows <= 6 ? rows : 1;
    }

    public static int gridColumns(int apps, int visibleColumns) {
        if (!horizontal()) return visibleColumns;
        return horizontalColumns(apps, visibleColumns, maximumRows(current));
    }

    public static float gridWidth(float width) {
        return horizontal() ? Math.max(1f, width - 64f) : width;
    }

    static int horizontalColumns(int apps, int visibleColumns, int rows) {
        if (apps < 0 || visibleColumns < 1 || rows < 1 || rows > 6) {
            throw new IllegalArgumentException("Invalid app grid dimensions.");
        }
        return Math.max(visibleColumns, apps / rows + (apps % rows == 0 ? 0 : 1));
    }

}
