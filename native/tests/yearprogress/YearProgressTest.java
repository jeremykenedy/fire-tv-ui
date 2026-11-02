package com.jeremykenedy.firetv.ui.nativeapp.widget.yearprogress;

import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDate;

public final class YearProgressTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static YearProgress date(String value) { return YearProgress.at(LocalDate.parse(value).atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC")); }
    public static void main(String[] args) {
        YearProgress start = date("2025-01-01");
        check(start.year == 2025 && start.elapsedDays == 0 && start.remainingDays == 365 && start.fraction() == 0, "Common year begins with no completed days");
        YearProgress leap = date("2024-02-29");
        check(leap.elapsedDays == 59 && leap.daysInYear == 366 && leap.remainingDays == 307, "Leap day counts completed calendar days");
        check(date("2024-03-01").elapsedDays == 60 && date("2025-03-01").elapsedDays == 59, "March follows leap and common February");
        check(date("2024-12-31").remainingDays == 1 && date("2024-12-31").elapsedDays == 365, "Last day remains until year end");
        Instant instant = Instant.parse("2025-01-01T01:00:00Z");
        check(YearProgress.at(instant, ZoneId.of("America/Los_Angeles")).year == 2024, "Use local year, not UTC year");
        Instant spring = Instant.parse("2025-03-09T08:00:00Z");
        check(YearProgress.untilNextDay(spring, ZoneId.of("America/Los_Angeles")) == 23L * 3600000, "Next midnight handles spring DST");
        Instant fall = Instant.parse("2025-11-02T07:00:00Z");
        check(YearProgress.untilNextDay(fall, ZoneId.of("America/Los_Angeles")) == 25L * 3600000, "Next midnight handles fall DST");
        System.out.println("Year progress common, leap, local year and DST boundaries passed.");
    }
}
