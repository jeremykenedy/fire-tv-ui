package com.jeremykenedy.firetv.ui.nativeapp.widget.uv;

import android.content.Context;

public final class UvPreferences {
    public static final String FILE = "native_uv";
    public static final String KEY = "zip";
    private UvPreferences() {}
    public static String read(Context context) {
        Object value = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getAll().get(KEY);
        if (value == null) return null;
        if (!(value instanceof String)) throw new IllegalArgumentException("Invalid saved ZIP code.");
        return UvForecast.zip((String) value);
    }
    public static void save(Context context, String zip) {
        if (zip != null) UvForecast.zip(zip);
        boolean saved = zip == null ? context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(KEY).commit()
                : context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, zip).commit();
        if (!saved) throw new IllegalStateException("Could not save the ZIP code. Try again.");
    }
}
