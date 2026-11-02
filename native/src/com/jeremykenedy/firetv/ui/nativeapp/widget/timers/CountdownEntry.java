package com.jeremykenedy.firetv.ui.nativeapp.widget.timers;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

public final class CountdownEntry {
    private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
            .withResolverStyle(ResolverStyle.STRICT);
    public final String label;
    public final String zone;
    public final long targetMillis;

    public CountdownEntry(String label, String zone, long targetMillis) {
        this.label = TimerState.label(label);
        this.zone = ZoneId.of(zone).getId();
        int year = java.time.Instant.ofEpochMilli(targetMillis).atZone(ZoneId.of(zone)).getYear();
        if (year < 1970 || year > 2099) throw new IllegalArgumentException("Choose a year from 1970 through 2099.");
        this.targetMillis = targetMillis;
    }

    public static CountdownEntry parse(String label, String date, String time, String zone) {
        try {
            LocalDateTime local = LocalDateTime.parse(date.trim() + " " + time.trim(), INPUT);
            ZoneId timezone = ZoneId.of(zone.trim());
            if (timezone.getRules().getValidOffsets(local).isEmpty()) {
                throw new IllegalArgumentException("That time is skipped by daylight saving. Choose another time.");
            }
            return new CountdownEntry(label, timezone.getId(), local.atZone(timezone).toInstant().toEpochMilli());
        } catch (java.time.DateTimeException error) {
            throw new IllegalArgumentException("Enter a valid date, 24-hour time and time zone.");
        }
    }

    public String localDateTime() {
        return INPUT.format(java.time.Instant.ofEpochMilli(targetMillis).atZone(ZoneId.of(zone)));
    }

    public long remaining(long wallMillis) { return Math.max(0L, targetMillis - wallMillis); }
}
