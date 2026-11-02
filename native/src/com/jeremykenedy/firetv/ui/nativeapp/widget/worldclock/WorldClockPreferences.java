package com.jeremykenedy.firetv.ui.nativeapp.widget.worldclock;

import android.content.Context;
import android.content.SharedPreferences;

public final class WorldClockPreferences {
    public static final String FILE = "native_world_clocks";
    public static final String KEY = "clocks";
    private WorldClockPreferences() {}

    public static WorldClocks read(Context context) {
        Object saved = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getAll().get(KEY);
        if (saved == null) return WorldClocks.empty();
        if (!(saved instanceof String)) throw new IllegalArgumentException("Invalid saved clocks.");
        return WorldClocks.decode((String) saved);
    }

    public static void save(Context context, WorldClocks clocks) {
        SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        if (!prefs.edit().putString(KEY, clocks.encode()).commit())
            throw new IllegalStateException("Could not save world clocks. Try again.");
    }
}
