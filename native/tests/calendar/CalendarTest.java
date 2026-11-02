package com.jeremykenedy.firetv.ui.nativeapp.widget.calendar;

import android.content.Context;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class CalendarTest {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("Expected rejection"); }
        catch (IllegalArgumentException | IllegalStateException expected) {}
    }
    private static long ms(String text) { return Instant.parse(text).toEpochMilli(); }
    private static CalendarData.Event event(long calendar, long id, String begin, String end, boolean day, String title) {
        return new CalendarData.Event(calendar, id, ms(begin), ms(end), day, title);
    }
    public static void main(String[] args) {
        Context context = new Context(); CalendarOptions defaults = CalendarPreferences.load(context);
        check(defaults.selected.isEmpty() && defaults.summaryOnly, "No calendar selection and private Home defaults");
        CalendarOptions options = new CalendarOptions(Arrays.asList(3L), "America/Los_Angeles", true);
        check(options.toggle(3).selected.isEmpty() && options.toggle(4).selected.equals(Arrays.asList(3L, 4L)), "Explicit independent selection");
        rejects(() -> new CalendarOptions(Arrays.asList(1L, 1L), "UTC", true));
        rejects(() -> new CalendarOptions(Arrays.asList(-1L), "UTC", true));
        rejects(() -> options.zone("not/a-zone"));
        rejects(() -> options.zone("+07:00"));
        List<Long> ids = new ArrayList<>(); for (long i = 0; i < 33; i++) ids.add(i);
        rejects(() -> new CalendarOptions(ids, "UTC", true));
        long now = ms("2026-03-07T20:00:00Z");
        check(CalendarData.endOfWindow(now, options.zone) == ms("2026-03-14T07:00:00Z"), "Seven local dates across spring DST, not seven absolute 24h durations");
        long fall = ms("2026-10-31T19:00:00Z");
        check(CalendarData.endOfWindow(fall, options.zone) == ms("2026-11-07T08:00:00Z"), "Seven local dates across fall DST");
        CalendarData.Event timed = event(3, 10, "2026-03-08T10:30:00Z", "2026-03-08T11:00:00Z", false, "Private event label");
        check(timed.when(options.zone).contains("2026-03-08 03:30 PDT"), "Timed event uses chosen zone at DST instant");
        check(timed.when("Asia/Tokyo").contains("2026-03-08 19:30 JST"), "Display zone converted independently from event instant");
        CalendarData.Event allDay = event(3, 11, "2026-03-07T00:00:00Z", "2026-03-08T00:00:00Z", true, "Private all-day label");
        check(allDay.overlaps(now, options), "All-day UTC exclusive end preserves today's calendar date after UTC midnight boundary");
        check(allDay.when("Pacific/Honolulu").equals("All day · 2026-03-07") && allDay.when("Pacific/Auckland").equals(allDay.when(options.zone)), "All-day date never shifted by display zone");
        CalendarData.Event multi = event(3, 12, "2026-03-06T00:00:00Z", "2026-03-09T00:00:00Z", true, "Trip");
        check(multi.when(options.zone).equals("All day · 2026-03-06 to 2026-03-08"), "Exclusive all-day end shown as final included date");
        CalendarData.Event ended = event(3, 13, "2026-03-07T19:00:00Z", "2026-03-07T20:00:00Z", false, "Ended");
        CalendarData.Event other = event(4, 14, "2026-03-08T10:30:00Z", "2026-03-08T11:00:00Z", false, "Other calendar");
        CalendarData.Event beyond = event(3, 15, "2026-03-14T07:00:00Z", "2026-03-14T08:00:00Z", false, "Beyond window");
        CalendarData.Event point = new CalendarData.Event(3, 16, now, now, false, "Point event");
        check(point.overlaps(now, options) && !point.overlaps(now + 1, options), "Zero-duration event included at its actual start boundary");
        List<CalendarData.Event> visible = CalendarData.visible(Arrays.asList(timed, allDay, multi, timed, ended, other, beyond, point), options, now);
        check(visible.size() == 4 && visible.get(0).eventId == 12 && visible.get(1).eventId == 11, "Selected overlapping events sorted and recurring duplicate instances deduplicated");
        CalendarData.Result result = new CalendarData.Result(CalendarData.State.READY, Collections.<CalendarData.Calendar>emptyList(), visible, now);
        check(result.homeText(options, now).contains("4 events") && !result.homeText(options, now).contains("Trip") && !result.homeText(options, now).contains("Private"), "Home summaries hide titles and schedules");
        check(result.homeText(options.privacy(false), now).contains("Trip") && result.homeText(options.privacy(false), now).contains("All day"), "Explicit detail display");
        check(!result.homeText(options, ms("2026-03-20T20:00:00Z")).contains("4 events"), "Expired cached occurrences do not stay counted");
        check(new CalendarData.Result(CalendarData.State.UNAVAILABLE, Collections.<CalendarData.Calendar>emptyList(), visible, now).homeText(options.privacy(false), now).equals("Calendar data is unavailable on this TV. No account or provider is assumed."), "Failure does not render stale private event data");
        CalendarPreferences.save(context, options);
        check(CalendarPreferences.load(context).signature().equals(options.signature()), "Exact local choices roundtrip");
        String encoded = CalendarPreferences.encode(options);
        check(!encoded.contains("Private") && !encoded.contains("Trip"), "Settings schema has no event content");
        context.prefs.failNext = true;
        rejects(() -> CalendarPreferences.save(context, options.toggle(4)));
        check(CalendarPreferences.load(context).signature().equals(options.signature()), "Failed commit restores old choices");
        rejects(() -> CalendarPreferences.decode(encoded.replace("\"summaryOnly\":true", "\"summaryOnly\":\"true\"")));
        rejects(() -> CalendarPreferences.decode(encoded.replace("[3]", "[3.5]")));
        rejects(() -> CalendarPreferences.decode(encoded.replace("[3]", "[\"3\"]")));
        rejects(() -> CalendarPreferences.decode(encoded.replace("[3]", "[9223372036854775808]")));
        rejects(() -> CalendarPreferences.decode(encoded.replace("\"version\":1", "\"version\":1,\"version\":1")));
        rejects(() -> CalendarPreferences.decode(encoded.replace("\"version\":1", "\"version\":2")));
        rejects(() -> CalendarPreferences.decode(encoded.replace("\"version\":1", "\"title\":\"Private\",\"version\":1")));
        context.prefs.values.put(CalendarPreferences.KEY, true); rejects(() -> CalendarPreferences.load(context));
        check(Boolean.TRUE.equals(context.prefs.values.get(CalendarPreferences.KEY)), "Unreadable choices retained");
        rejects(() -> event(3, 1, "2026-03-07T01:00:00Z", "2026-03-08T01:00:00Z", true, "Invalid all-day"));
        check(new CalendarData.Calendar(1, "Calendar\nName").label.equals("Calendar Name"), "Provider control characters rendered as text");
        System.out.println("Calendar selection, timezone/DST, all-day dates, privacy and strict persistence pass");
    }
}
