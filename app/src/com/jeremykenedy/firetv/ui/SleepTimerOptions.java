package com.jeremykenedy.firetv.ui;

/** Choices supported by Fire TV's one-time sleep timer. */
public final class SleepTimerOptions {
    private static final int[] MINUTES = {0, 5, 10, 15, 30, 60, 90, 120, 150, 180, 240};
    private static final String[] ACTION_IDS = {
            "qs_sleep_timer", "sleep_timer_5", "sleep_timer_10", "sleep_timer_15", "sleep_timer_30",
            "sleep_timer_60", "sleep_timer_90", "sleep_timer_120", "sleep_timer_150",
            "sleep_timer_180", "sleep_timer_240"
    };
    private static final String[] LABELS = {
            "Off", "5 minutes", "10 minutes", "15 minutes", "30 minutes", "1 hour",
            "1 hour 30 minutes", "2 hours", "2 hours 30 minutes", "3 hours", "4 hours"
    };

    private SleepTimerOptions() {
    }

    public static int count() {
        return MINUTES.length;
    }

    public static int minutesAt(int index) {
        return MINUTES[index];
    }

    public static String labelAt(int index) {
        return LABELS[index];
    }

    public static String actionIdAt(int index) {
        return ACTION_IDS[index];
    }

    public static int minutesForAction(String actionId) {
        for (int index = 0; index < ACTION_IDS.length; index++) {
            if (ACTION_IDS[index].equals(actionId)) {
                return MINUTES[index];
            }
        }
        throw new IllegalArgumentException("Unsupported Fire TV sleep timer choice.");
    }

    public static String labelForMinutes(int minutes) {
        for (int index = 0; index < MINUTES.length; index++) {
            if (MINUTES[index] == minutes) {
                return LABELS[index];
            }
        }
        throw new IllegalArgumentException("Unsupported Fire TV sleep timer duration.");
    }
}
