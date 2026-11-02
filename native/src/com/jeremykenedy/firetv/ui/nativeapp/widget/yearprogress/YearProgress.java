package com.jeremykenedy.firetv.ui.nativeapp.widget.yearprogress;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

final class YearProgress {
    final int year, elapsedDays, daysInYear, remainingDays;
    private YearProgress(LocalDate date) {
        year = date.getYear();
        elapsedDays = date.getDayOfYear() - 1;
        daysInYear = date.lengthOfYear();
        remainingDays = daysInYear - elapsedDays;
    }
    static YearProgress at(Instant instant, ZoneId zone) {
        return new YearProgress(instant.atZone(zone).toLocalDate());
    }
    double fraction() { return (double) elapsedDays / daysInYear; }
    static long untilNextDay(Instant instant, ZoneId zone) {
        LocalDate date = instant.atZone(zone).toLocalDate();
        return Math.max(1, date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - instant.toEpochMilli());
    }
}
