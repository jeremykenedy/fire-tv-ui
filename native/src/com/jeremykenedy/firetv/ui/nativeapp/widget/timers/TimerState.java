package com.jeremykenedy.firetv.ui.nativeapp.widget.timers;

import java.util.Locale;

/** Elapsed realtime governs a running timer within one boot, including process restarts. */
public final class TimerState {
    public static final long MAX_DURATION = 7L * 24 * 60 * 60 * 1000;
    public final String label;
    public final long duration;
    public final long pausedRemaining;
    public final boolean running;
    public final boolean sound;
    public final long elapsedDeadline;
    public final long wallDeadline;
    public final int boot;

    public TimerState(String label, long duration, long pausedRemaining, boolean running, boolean sound,
                      long elapsedDeadline, long wallDeadline, int boot) {
        this.label = label(label);
        if (duration < 1000 || duration > MAX_DURATION || pausedRemaining < 0 || pausedRemaining > duration
                || (running && (elapsedDeadline < 0 || wallDeadline <= 0))) {
            throw new IllegalArgumentException("Timer duration must be 1 second through 7 days.");
        }
        this.duration = duration;
        this.pausedRemaining = pausedRemaining;
        this.running = running;
        this.sound = sound;
        this.elapsedDeadline = elapsedDeadline;
        this.wallDeadline = wallDeadline;
        this.boot = boot;
    }

    public static TimerState create(String label, long seconds, boolean sound) {
        if (seconds < 1 || seconds > MAX_DURATION / 1000) {
            throw new IllegalArgumentException("Enter 1 through 604800 seconds.");
        }
        return new TimerState(label, seconds * 1000, seconds * 1000, false, sound, 0, 0, -1);
    }

    public long remaining(long elapsed, long wall, int currentBoot) {
        if (!running) return pausedRemaining;
        // Boot identity survives process death. Restricted TVs use a private process identity instead.
        long value = boot != -1 && boot == currentBoot ? elapsedDeadline - elapsed : wallDeadline - wall;
        return Math.max(0, Math.min(duration, value));
    }

    public TimerState resume(long elapsed, long wall, int currentBoot) {
        if (running) return this;
        if (pausedRemaining == 0) return this;
        return new TimerState(label, duration, pausedRemaining, true, sound,
                elapsed + pausedRemaining, wall + pausedRemaining, currentBoot);
    }

    public TimerState pause(long elapsed, long wall, int currentBoot) {
        return new TimerState(label, duration, remaining(elapsed, wall, currentBoot), false, sound, 0, 0, currentBoot);
    }

    public TimerState reset() { return new TimerState(label, duration, duration, false, sound, 0, 0, -1); }

    public TimerState recover(long elapsed, long wall, int currentBoot) {
        if (!running || boot != -1 && boot == currentBoot) return this;
        long remaining = Math.max(0, Math.min(duration, wallDeadline - wall));
        return new TimerState(label, duration, remaining, remaining > 0, sound,
                elapsed + remaining, wall + remaining, currentBoot);
    }

    public static String label(String value) {
        if (value == null || value.trim().isEmpty() || value.trim().length() > 80) {
            throw new IllegalArgumentException("Enter a label of 1 through 80 characters.");
        }
        String clean = value.trim();
        for (int i = 0; i < clean.length(); i++) {
            if (Character.isISOControl(clean.charAt(i))) throw new IllegalArgumentException("Use a single-line label.");
        }
        return clean;
    }

    public static String format(long milliseconds) {
        long seconds = Math.max(0, (milliseconds + 999) / 1000);
        long days = seconds / 86400;
        return (days > 0 ? days + "d " : "") + String.format(Locale.getDefault(), "%02d:%02d:%02d",
                seconds / 3600 % 24, seconds / 60 % 60, seconds % 60);
    }
}
