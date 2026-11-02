package com.jeremykenedy.firetv.ui.nativeapp.widget.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

public final class CalendarData {
    public enum State { READY, EMPTY, PERMISSION_REQUIRED, SETUP_REQUIRED, UNAVAILABLE, PARTIAL }
    public static final int MAX_EVENTS = 64;
    public static final int MAX_SCANNED = 2048;
    public static final int DAYS = 7;
    public static final class Calendar {
        public final long id;
        public final String label;
        public Calendar(long id, String label) {
            if (id < 0) throw new IllegalArgumentException("Invalid calendar identity.");
            this.id = id; this.label = clean(label, 120, "Unnamed calendar");
        }
    }
    public static final class Event {
        public final long calendarId;
        public final long eventId;
        public final long begin;
        public final long end;
        public final boolean allDay;
        public final String title;
        public Event(long calendarId, long eventId, long begin, long end, boolean allDay, String title) {
            if (calendarId < 0 || eventId < 0 || begin < 0 || end < begin || end > 4133980800000L) throw new IllegalArgumentException("Invalid event times.");
            if (allDay && (end == begin || Math.floorMod(begin, 86400000L) != 0 || Math.floorMod(end, 86400000L) != 0)) throw new IllegalArgumentException("Invalid all-day dates.");
            this.calendarId = calendarId; this.eventId = eventId; this.begin = begin; this.end = end; this.allDay = allDay;
            this.title = clean(title, 160, "Untitled event");
        }
        public String key() { return calendarId + ":" + eventId + ":" + begin; }
        public boolean overlaps(long now, CalendarOptions options) {
            long until = endOfWindow(now, options.zone);
            if (!options.selected.contains(calendarId)) return false;
            if (!allDay) return (end == begin ? begin >= now : end > now) && begin < until;
            LocalDate today = Instant.ofEpochMilli(now).atZone(ZoneId.of(options.zone)).toLocalDate();
            LocalDate from = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate to = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate();
            return to.isAfter(today) && from.isBefore(today.plusDays(DAYS));
        }
        public String when(String zone) {
            if (allDay) {
                LocalDate from = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate();
                LocalDate last = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1);
                return "All day · " + from + (last.equals(from) ? "" : " to " + last);
            }
            DateTimeFormatter format = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm z").withZone(ZoneId.of(zone));
            return format.format(Instant.ofEpochMilli(begin)) + " to " + format.format(Instant.ofEpochMilli(end));
        }
    }
    public static final class Result {
        public final State state;
        public final List<Calendar> calendars;
        public final List<Event> events;
        public final long checkedAt;
        public Result(State state, List<Calendar> calendars, List<Event> events, long checkedAt) {
            this.state = state; this.calendars = Collections.unmodifiableList(new ArrayList<>(calendars));
            this.events = Collections.unmodifiableList(new ArrayList<>(events)); this.checkedAt = checkedAt;
        }
        public String message() {
            switch (state) {
                case PERMISSION_REQUIRED: return "Calendar permission is needed. Choose Allow calendar access to request it.";
                case SETUP_REQUIRED: return "Choose which calendars this launcher may display.";
                case UNAVAILABLE: return "Calendar data is unavailable on this TV. No account or provider is assumed.";
                case EMPTY: return "No selected calendar events in the next seven local dates.";
                case PARTIAL: return "Calendar results are limited or a selected calendar is unavailable. Review the selection.";
                default: return "Selected calendars · next seven local dates. Read only; calendar apps control their own sync.";
            }
        }
        public String homeText(CalendarOptions options, long now) {
            if (state != State.READY && state != State.EMPTY && state != State.PARTIAL) return message();
            int count = 0; Event first = null;
            for (Event event : events) if (event.overlaps(now, options)) { count++; if (first == null) first = event; }
            String summary = (state == State.PARTIAL ? "Limited results · " : "") + count + " events · next seven local dates";
            if (options.summaryOnly) return summary + "\nDetails hidden on Home";
            return first == null ? summary : summary + "\n" + first.title + "\n" + first.when(options.zone);
        }
    }
    public static long endOfWindow(long now, String zone) {
        ZoneId id = ZoneId.of(zone);
        return Instant.ofEpochMilli(now).atZone(id).toLocalDate().plusDays(DAYS).atStartOfDay(id).toInstant().toEpochMilli();
    }
    public static List<Event> visible(List<Event> input, CalendarOptions options, long now) {
        List<Event> result = new ArrayList<>(); HashSet<String> seen = new HashSet<>();
        for (Event event : input) if (event.overlaps(now, options) && seen.add(event.key())) result.add(event);
        final ZoneId zone = ZoneId.of(options.zone);
        Collections.sort(result, new Comparator<Event>() {
            private long position(Event event) {
                if (!event.allDay) return event.begin;
                return Instant.ofEpochMilli(event.begin).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli();
            }
            @Override public int compare(Event left, Event right) {
                int at = Long.compare(position(left), position(right)); return at != 0 ? at : left.key().compareTo(right.key());
            }
        });
        return result;
    }
    static String clean(String text, int limit, String fallback) {
        if (text == null) return fallback;
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < text.length() && clean.length() < limit; i++) {
            char c = text.charAt(i);
            if (Character.isISOControl(c)) { clean.append(' '); continue; }
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1)) && clean.length() + 2 <= limit) clean.append(c).append(text.charAt(++i));
            } else if (!Character.isLowSurrogate(c)) clean.append(c);
        }
        String value = clean.toString().trim(); return value.isEmpty() ? fallback : value;
    }
}
