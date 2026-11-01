package com.jeremykenedy.firetv.ui.nativeapp.layout;

import java.util.Collections;
import java.util.List;

/** Only implemented, locally available sources belong in this registry. */
public final class HomeSlots {
    public static final LayoutItem CLOCK = new LayoutItem("clock", LayoutItem.Kind.WIDGET);
    private HomeSlots() {}

    public static List<LayoutItem> available() { return Collections.singletonList(CLOCK); }
    public static LayoutOptions defaults() { return LayoutOptions.defaults(320, 180); }

    public static String title(String key) {
        if (CLOCK.key().equals(key)) return "Clock";
        return key.startsWith("channel:") ? "Unavailable channel" : "Unavailable widget";
    }
}
