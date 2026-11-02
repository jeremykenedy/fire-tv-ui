package com.jeremykenedy.firetv.ui.nativeapp.widgetgroups;

import java.time.LocalDate;

/** Provisional day-based rendering. The legacy rounding is only observed for one date. */
public final class LegacyYearProgressModel {
    public static final int DOTS = 52;
    public final int percent, filledDots;
    private LegacyYearProgressModel(int percent, int filledDots) { this.percent = percent; this.filledDots = filledDots; }
    public static LegacyYearProgressModel at(LocalDate date) {
        if (date == null) throw new IllegalArgumentException("Missing date.");
        int elapsed = date.getDayOfYear() - 1, days = date.lengthOfYear();
        return new LegacyYearProgressModel(elapsed * 100 / days, elapsed * DOTS / days);
    }
}
