package com.jeremykenedy.firetv.ui.nativeapp.widget.breathing;

import android.content.Context;
import android.content.SharedPreferences;

public final class BreathingPreferences {
    public static final String FILE = "native_breathing_break";
    private static final String PROCESS = java.util.UUID.randomUUID().toString();
    private BreathingPreferences() {}

    public static SharedPreferences preferences(Context context) { return context.getSharedPreferences(FILE, Context.MODE_PRIVATE); }

    public static BreathingSession read(Context context) {
        SharedPreferences prefs = preferences(context);
        try {
            boolean running = prefs.getBoolean("running", false) && PROCESS.equals(prefs.getString("process", ""));
            return new BreathingSession(prefs.getInt("inhale", 4), prefs.getInt("hold", 0), prefs.getInt("exhale", 6),
                    prefs.getInt("rest", 0), prefs.getInt("duration", 120), prefs.getBoolean("reduced_motion", false),
                    prefs.getLong("elapsed", 0), running ? prefs.getLong("anchor", 0) : 0, running);
        } catch (RuntimeException error) { throw new IllegalArgumentException("Saved exercise could not be read. Reset it in Breathing break settings.", error); }
    }

    public static boolean save(Context context, BreathingSession session) {
        return preferences(context).edit().putInt("inhale", session.inhale).putInt("hold", session.hold)
                .putInt("exhale", session.exhale).putInt("rest", session.rest).putInt("duration", session.duration)
                .putBoolean("reduced_motion", session.reducedMotion).putLong("elapsed", session.elapsed)
                .putLong("anchor", session.anchor).putBoolean("running", session.running).putString("process", PROCESS).commit();
    }
}
