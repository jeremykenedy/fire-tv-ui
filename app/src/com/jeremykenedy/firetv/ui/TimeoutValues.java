package com.jeremykenedy.firetv.ui;

/** Android stores these timers as signed milliseconds. */
public final class TimeoutValues {
    public static final int MAX_MINUTES = 35791;

    private TimeoutValues() {
    }

    public static int milliseconds(String minutes) {
        if (minutes == null || !minutes.matches("[0-9]{1,5}")) {
            throw new IllegalArgumentException("Enter a whole number of minutes.");
        }
        int value = Integer.parseInt(minutes);
        if (value < 1 || value > MAX_MINUTES) {
            throw new IllegalArgumentException("Choose between 1 and " + MAX_MINUTES + " minutes.");
        }
        return value * 60000;
    }

    public static String label(int milliseconds) {
        if (milliseconds <= 0) {
            return "Disabled";
        }
        if (milliseconds % 60000 == 0) {
            return (milliseconds / 60000) + " minutes";
        }
        return (milliseconds / 1000) + " seconds";
    }

    public static long launcherIdleDelay(int milliseconds) {
        return milliseconds > 0 ? milliseconds : Long.MAX_VALUE;
    }

    public static boolean isIdleDue(long elapsedMilliseconds, int delayMilliseconds) {
        return delayMilliseconds > 0 && elapsedMilliseconds >= delayMilliseconds;
    }

    public static int deepSleepAutoWakeValue(boolean enabled) {
        return enabled ? 0 : 1;
    }

    public static int activeDeepSleepTimeout(int configuredTimeout) {
        if (configuredTimeout < 0) {
            throw new IllegalArgumentException("The deep sleep timer cannot be negative.");
        }
        return configuredTimeout;
    }
}
