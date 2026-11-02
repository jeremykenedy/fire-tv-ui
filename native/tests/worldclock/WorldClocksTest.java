package com.jeremykenedy.firetv.ui.nativeapp.widget.worldclock;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.TimeZone;

public final class WorldClocksTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void invalid(Runnable work) {
        try { work.run(); throw new AssertionError("Invalid clocks accepted"); }
        catch (IllegalArgumentException expected) {}
    }
    private static long utc(String value) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.parse(value).getTime();
    }
    public static void main(String[] args) throws Exception {
        WorldClocks.Entry la = new WorldClocks.Entry("America/Los_Angeles", "Home");
        WorldClocks.Entry tokyo = new WorldClocks.Entry("Asia/Tokyo", "Tokyo");
        WorldClocks clocks = WorldClocks.empty().add(la).add(tokyo);
        check(WorldClocks.decode(clocks.encode()).entries.get(0).label.equals("Home"), "Label round trip");
        check(WorldClocks.decode(clocks.move(1, 0).encode()).entries.get(0).zone.equals(tokyo.zone), "Order persists");
        check(clocks.remove(0).entries.size() == 1 && clocks.entries.size() == 2, "Removal is isolated");
        check(clocks.replace(0, new WorldClocks.Entry(la.zone, "West coast")).entries.get(0).label.equals("West coast"), "Rename");
        check(WorldClocks.decode("").entries.isEmpty(), "Fresh state is empty");
        invalid(() -> new WorldClocks.Entry("America/NotAZone", "Fake"));
        invalid(() -> new WorldClocks.Entry("GMT+99", "Fake"));
        invalid(() -> new WorldClocks.Entry("UTC", " \t"));
        invalid(() -> new WorldClocks.Entry("UTC", "Line\nbreak"));
        invalid(() -> new WorldClocks.Entry("UTC", new String(new char[41]).replace('\0', 'a')));
        invalid(() -> clocks.add(la));
        invalid(() -> clocks.replace(1, la));
        invalid(() -> WorldClocks.decode("v2"));
        invalid(() -> WorldClocks.decode("v1\nUTC\tx\textra"));
        invalid(() -> WorldClocks.decode("v1\nUTC\tx\nUTC\ty"));
        invalid(() -> new WorldClocks(Arrays.asList(la, tokyo, new WorldClocks.Entry("UTC", "UTC"),
                new WorldClocks.Entry("Europe/London", "London"), new WorldClocks.Entry("Asia/Kolkata", "India"),
                new WorldClocks.Entry("Pacific/Auckland", "NZ"), new WorldClocks.Entry("Europe/Paris", "Paris"))));
        check(WorldClocks.format(la, utc("2026-03-08 09:59"), Locale.US, "HH:mm").equals("01:59"), "Before spring DST");
        check(WorldClocks.format(la, utc("2026-03-08 10:00"), Locale.US, "HH:mm").equals("03:00"), "Spring skips nonexistent hour");
        check(WorldClocks.format(la, utc("2026-11-01 08:59"), Locale.US, "HH:mm").equals("01:59"), "Before autumn DST");
        check(WorldClocks.format(la, utc("2026-11-01 09:00"), Locale.US, "HH:mm").equals("01:00"), "Autumn repeats hour");
        check(WorldClocks.format(tokyo, utc("2026-12-31 15:01"), Locale.JAPAN, "yyyy-MM-dd HH:mm").equals("2027-01-01 00:01"), "Zone date rollover");
        check(WorldClocks.format(la, utc("2026-03-08 10:00"), Locale.US, "h:mm a").equals("3:00 AM"), "12-hour pattern");
        check(WorldClocks.format(new WorldClocks.Entry("Asia/Kolkata", "India"), utc("2026-01-01 00:00"), Locale.US, "HH:mm").equals("05:30"), "Fractional offset");
        System.out.println("World clocks: DST transitions, rollover, formatting, validation and saved ordering passed.");
    }
}
