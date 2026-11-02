package com.jeremykenedy.firetv.ui.nativeapp.widget.astronomy;

import android.content.Context;

public final class AstronomyPreferences {
    public static final String FILE = "native_astronomy";
    public static final String KEY = "location";
    private AstronomyPreferences() {}
    public static AstronomyLocation read(Context context) {
        Object saved = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getAll().get(KEY);
        if (saved == null) return null;
        if (!(saved instanceof String)) throw new IllegalArgumentException("Invalid saved location.");
        return AstronomyLocation.decode((String) saved);
    }
    public static void save(Context context, AstronomyLocation location) {
        boolean saved = location == null ? context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(KEY).commit()
                : context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, location.encode()).commit();
        if (!saved) throw new IllegalStateException("Could not save the sun/moon location. Try again.");
    }
}
