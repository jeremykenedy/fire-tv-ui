package com.jeremykenedy.firetv.ui;

import android.content.SharedPreferences;

public final class FeaturedCardTiming {
    static final String SETTING = "featured_rotation_seconds";
    static final int[] OPTIONS = {0, 5, 8, 15, 30};

    private FeaturedCardTiming() {}

    static int seconds(int value) {
        for (int option : OPTIONS) if (value == option) return value;
        return 8;
    }

    public static long delay(SharedPreferences preferences) {
        return milliseconds(preferences.getInt(SETTING, 8));
    }

    public static Integer setting(SharedPreferences preferences) {
        return Integer.valueOf(seconds(preferences.getInt(SETTING, 8)));
    }

    static long milliseconds(int value) {
        int seconds = seconds(value);
        return seconds == 0 ? Long.MAX_VALUE : seconds * 1000L;
    }

    static String label(int value) {
        int seconds = seconds(value);
        return seconds == 0 ? "Manual only" : "Every " + seconds + " seconds";
    }
}
