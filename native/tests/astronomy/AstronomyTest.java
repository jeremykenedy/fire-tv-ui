package com.jeremykenedy.firetv.ui.nativeapp.widget.astronomy;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

public final class AstronomyTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static long utc(String value) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.parse(value).getTime();
    }
    private static void invalid(Runnable work) {
        try { work.run(); throw new AssertionError("Invalid value accepted"); }
        catch (IllegalArgumentException expected) {}
    }
    public static void main(String[] args) throws Exception {
        // Schlyter's 1990-04-19 00 UTC worked example, independent published numeric checks.
        long worked = utc("1990-04-19 00:00");
        check(Math.abs(Astronomy.sunLongitude(worked) - 28.6869) < .001, "Published solar longitude example");
        Astronomy.Moon moon = Astronomy.moon(worked);
        check(Math.abs(moon.longitude - 306.9484) < .01, "Published perturbed lunar longitude example: " + moon.longitude);
        check(Math.abs(moon.latitude - -0.5856) < .01, "Published perturbed lunar latitude example: " + moon.latitude);
        AstronomyLocation location = new AstronomyLocation(40.7128, -74.006, "America/New_York", "New York");
        check(AstronomyLocation.decode(location.encode()).zone.equals(location.zone), "Location round trip");
        invalid(() -> new AstronomyLocation(Double.NaN, 0, "UTC", "Invalid"));
        invalid(() -> new AstronomyLocation(0, Double.POSITIVE_INFINITY, "UTC", "Invalid"));
        invalid(() -> new AstronomyLocation(91, 0, "UTC", "Invalid"));
        invalid(() -> new AstronomyLocation(0, -181, "UTC", "Invalid"));
        invalid(() -> new AstronomyLocation(0, 0, "No/SuchZone", "Invalid"));
        invalid(() -> new AstronomyLocation(0, 0, "UTC", "Line\nbreak"));
        invalid(() -> AstronomyLocation.decode("v2\t0\t0\tUTC\tx"));
        invalid(() -> Astronomy.validateDate(Long.MAX_VALUE));
        invalid(() -> Astronomy.validateDate(Long.MIN_VALUE));
        Astronomy.SunDay autumn = Astronomy.sunDay(utc("2026-11-01 17:00"), location.latitude, location.longitude, TimeZone.getTimeZone(location.zone));
        check(autumn.end - autumn.start == 25 * 3600000L, "DST autumn day is 25 hours");
        Astronomy.SunDay spring = Astronomy.sunDay(utc("2026-03-08 16:00"), location.latitude, location.longitude, TimeZone.getTimeZone(location.zone));
        check(spring.end - spring.start == 23 * 3600000L, "DST spring day is 23 hours");
        check(autumn.sunrise >= autumn.start && autumn.sunset < autumn.end, "Events belong to configured local date");
        System.out.println("Astronomy: published examples, location validation, date bounds and DST day boundaries passed.");
    }
}
