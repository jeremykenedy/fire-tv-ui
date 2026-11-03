package com.jeremykenedy.firetv.ui.nativeapp.widgetgroups;

import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;
import com.jeremykenedy.firetv.ui.nativeapp.layout.ClockWidgetProvider;

/** Resolves only records whose complete legacy data is understood. */
public final class WidgetGroupProviders {
    private WidgetGroupProviders() {}
    public static WidgetProvider resolve(WidgetGroups.Entry entry) {
        if (entry == null || !entry.isRenderable()) return null;
        if (entry.type.equals("DigitalClock")) return entry.size.equals("Big") ? new LegacyClockProvider() : new ClockWidgetProvider();
        if (entry.type.equals("YearProgress")) return new LegacyYearProgressProvider();
        return null;
    }
}
