package com.jeremykenedy.firetv.ui.nativeapp.widget.timers;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

public final class TimerPreferences {
    public static final String FILE = "native_local_timers";
    // A private process identity keeps active timers monotonic when a TV withholds BOOT_COUNT.
    private static final int PROCESS_BOOT = -2 - new java.security.SecureRandom().nextInt(Integer.MAX_VALUE - 2);
    private TimerPreferences() {}

    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences(FILE, Context.MODE_PRIVATE); }

    public static CountdownEntry countdown(Context context) {
        SharedPreferences prefs = prefs(context);
        try {
            if (!prefs.contains("countdown_label")) return null;
            return new CountdownEntry(prefs.getString("countdown_label", ""), prefs.getString("countdown_zone", ""),
                    prefs.getLong("countdown_target", 0));
        } catch (RuntimeException error) { return null; }
    }

    public static boolean saveCountdown(Context context, CountdownEntry entry) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (entry == null) return editor.remove("countdown_label").remove("countdown_zone").remove("countdown_target").commit();
        return editor.putString("countdown_label", entry.label).putString("countdown_zone", entry.zone)
                .putLong("countdown_target", entry.targetMillis).commit();
    }

    public static TimerState timer(Context context) {
        SharedPreferences prefs = prefs(context);
        try {
            if (!prefs.contains("timer_label")) return null;
            TimerState state = new TimerState(prefs.getString("timer_label", ""), prefs.getLong("timer_duration", 0),
                    prefs.getLong("timer_remaining", 0), prefs.getBoolean("timer_running", false),
                    prefs.getBoolean("timer_sound", false), prefs.getLong("timer_elapsed_deadline", 0),
                    prefs.getLong("timer_wall_deadline", 0), prefs.getInt("timer_boot", -1));
            int currentBoot = boot(context);
            if (state.running && state.boot != currentBoot) {
                state = state.recover(android.os.SystemClock.elapsedRealtime(), System.currentTimeMillis(), currentBoot);
                if (!saveTimer(context, state)) return null;
            }
            return state;
        } catch (RuntimeException error) { return null; }
    }

    public static boolean saveTimer(Context context, TimerState timer) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (timer == null) {
            for (String key : new String[]{"label", "duration", "remaining", "running", "sound", "elapsed_deadline", "wall_deadline", "boot"}) {
                editor.remove("timer_" + key);
            }
            return editor.commit();
        }
        return editor.putString("timer_label", timer.label).putLong("timer_duration", timer.duration)
                .putLong("timer_remaining", timer.pausedRemaining).putBoolean("timer_running", timer.running)
                .putBoolean("timer_sound", timer.sound).putLong("timer_elapsed_deadline", timer.elapsedDeadline)
                .putLong("timer_wall_deadline", timer.wallDeadline).putInt("timer_boot", timer.boot).commit();
    }

    public static int boot(Context context) {
        try {
            int boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
            return boot >= 0 ? boot : PROCESS_BOOT;
        } catch (RuntimeException error) { return PROCESS_BOOT; }
    }
}
