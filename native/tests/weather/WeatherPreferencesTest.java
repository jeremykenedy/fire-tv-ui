package com.jeremykenedy.firetv.ui.nativeapp.widget.weather;

import android.content.Context;
import java.util.HashMap;
import java.util.Map;

public final class WeatherPreferencesTest {
    public static void main(String[] args) {
        Context context = new Context(); context.prefs.values.put("unrelated", "keep");
        check(!WeatherPreferences.load(context).configured, "No implicit location in a new profile.");
        context.legacy.values.put("status_bar_weather_loc", "Springfield");
        context.legacy.values.put("status_bar_weather_unit", "F");
        WeatherOptions inherited = WeatherPreferences.load(context);
        check(!inherited.configured && inherited.label.equals("Springfield") && !inherited.metric,
                "Legacy choices appear in setup without guessing coordinates.");
        check(context.prefs.values.size() == 1 && context.legacy.values.size() == 2,
                "Loading legacy choices does not change either preference store.");
        context.legacy.values.put("status_bar_weather_unit", "K");
        try { WeatherPreferences.load(context); throw new AssertionError("Unknown saved legacy unit accepted"); }
        catch (IllegalStateException expected) { check(context.legacy.values.get("status_bar_weather_unit").equals("K"), "Unreadable legacy data is kept."); }
        context.legacy.values.remove("status_bar_weather_unit");
        check(WeatherPreferences.load(context).metric, "Missing legacy unit uses the inherited Celsius default.");
        context.legacy.values.put("status_bar_weather_unit", 7);
        try { WeatherPreferences.load(context); throw new AssertionError("Wrong legacy unit type accepted"); }
        catch (IllegalStateException expected) { check(context.legacy.values.get("status_bar_weather_unit").equals(7), "Wrong typed legacy value is kept."); }
        context.legacy.values.put("status_bar_weather_unit", "F");
        WeatherOptions legacy = WeatherOptions.fromLegacy("Springfield", "F");
        check(!legacy.configured && legacy.label.equals("Springfield") && !legacy.metric, "Legacy name and Fahrenheit are retained without guessing a location.");
        WeatherPreferences.save(context, legacy);
        check(WeatherPreferences.load(context).backupValues().equals(legacy.backupValues()), "Partial legacy choices survive reload without enabling weather requests.");
        check(WeatherOptions.fromLegacy("Example", "C").metric && WeatherOptions.fromLegacy(null, null).metric,
                "Legacy Celsius and default unit are preserved.");
        try { WeatherOptions.fromLegacy("Example", "K"); throw new AssertionError("Unknown legacy unit accepted"); }
        catch (IllegalArgumentException expected) { }
        try { WeatherOptions.fromLegacy(" Example ", "F"); throw new AssertionError("Legacy location silently changed"); }
        catch (IllegalArgumentException expected) { }
        WeatherOptions options = WeatherOptions.location("Example", "39.7456", "-97.0892", true, true);
        WeatherPreferences.save(context, options);
        check(WeatherPreferences.load(context).backupValues().equals(options.backupValues()), "Saved units, timezone and manual location survive reload.");
        context.legacy.values.put("status_bar_weather_loc", "Different legacy city");
        check(WeatherPreferences.load(context).backupValues().equals(options.backupValues()), "Explicit native choices take precedence over legacy values.");
        Map<String,Object> before = new HashMap<>(context.prefs.values);
        context.prefs.fail = true;
        try { WeatherPreferences.save(context, WeatherOptions.defaults()); throw new AssertionError("Failed save accepted"); }
        catch (IllegalStateException expected) { check(before.equals(context.prefs.values), "Failed save restores exact owned and unrelated values."); }
        context.prefs.fail = false;
        context.prefs.values.remove("longitude_e4");
        Map<String,Object> incomplete = new HashMap<>(context.prefs.values);
        try { WeatherPreferences.load(context); throw new AssertionError("Partial location silently defaulted"); }
        catch (IllegalStateException expected) { check(incomplete.equals(context.prefs.values), "Incomplete choices are retained and cannot cause a guessed network location."); }
        Map<String,Object> invalid = new HashMap<>(options.backupValues()); invalid.put("latitude_e4", 397456L);
        try { WeatherPreferences.restore(context, invalid); throw new AssertionError("Wrong typed coordinate accepted"); }
        catch (IllegalArgumentException expected) { check(incomplete.equals(context.prefs.values), "Invalid import cannot partially alter settings."); }
        System.out.println("Weather persistence, partial-location rejection, failed-write rollback and atomic typed restore passed.");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
