package com.jeremykenedy.firetv.ui.nativeapp.widget.calendar;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public final class CalendarOptions {
    public static final int MAX_CALENDARS = 32;
    public final List<Long> selected;
    public final String zone;
    public final boolean summaryOnly;
    public CalendarOptions(List<Long> selected, String zone, boolean summaryOnly) {
        if (selected == null || selected.size() > MAX_CALENDARS) throw new IllegalArgumentException("Select at most 32 calendars.");
        HashSet<Long> ids = new HashSet<>();
        for (Long id : selected) if (id == null || id < 0 || !ids.add(id)) throw new IllegalArgumentException("Invalid calendar selection.");
        if (zone == null || zone.length() > 80 || !ZoneId.getAvailableZoneIds().contains(zone))
            throw new IllegalArgumentException("Enter a known IANA time zone, such as America/Los_Angeles.");
        this.selected = Collections.unmodifiableList(new ArrayList<>(selected)); this.zone = zone; this.summaryOnly = summaryOnly;
    }
    public static CalendarOptions defaults() {
        String zone = ZoneId.systemDefault().getId();
        return new CalendarOptions(Collections.<Long>emptyList(), ZoneId.getAvailableZoneIds().contains(zone) ? zone : "UTC", true);
    }
    public CalendarOptions toggle(long id) {
        List<Long> next = new ArrayList<>(selected);
        if (!next.remove(id)) next.add(id);
        return new CalendarOptions(next, zone, summaryOnly);
    }
    public CalendarOptions privacy(boolean summary) { return new CalendarOptions(selected, zone, summary); }
    public CalendarOptions zone(String zone) { return new CalendarOptions(selected, zone, summaryOnly); }
    public String signature() { return selected.toString() + "/" + zone + "/" + summaryOnly; }
}
