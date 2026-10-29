package com.jeremykenedy.firetv.ui;

import android.content.SharedPreferences;

public final class LauncherLayout {
    static final String COLUMNS = "apps_per_row";
    static final int DEFAULT_COLUMNS = 5;
    static final int[] PRESETS = {3, 4, 5, 6, 7, 8, 10, 12};

    private LauncherLayout() {}

    static int parseColumns(String value) {
        int columns;
        try {
            columns = Integer.parseInt(value.trim());
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Enter a whole number from 2 to 12.");
        }
        if (columns < 2 || columns > 12) {
            throw new IllegalArgumentException("Choose between 2 and 12 apps per row.");
        }
        return columns;
    }

    public static Integer columns(SharedPreferences preferences) {
        int value = preferences.getInt(COLUMNS, DEFAULT_COLUMNS);
        return Integer.valueOf(value > 0 && value <= 20 ? value : DEFAULT_COLUMNS);
    }
}
