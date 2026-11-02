package com.jeremykenedy.firetv.ui.nativeapp.layout;

import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Stable Home slots for implemented widgets and real published TV feeds. */
public final class HomeSlots {
    public static final LayoutItem CLOCK = new LayoutItem("clock", LayoutItem.Kind.WIDGET);
    private HomeSlots() {}

    public static List<LayoutItem> available() { return Collections.singletonList(CLOCK); }
    public static List<LayoutItem> available(List<TvRecommendations.Feed> feeds) {
        List<LayoutItem> items = new ArrayList<>(available());
        for (TvRecommendations.Feed feed : feeds) items.add(feed.item);
        return Collections.unmodifiableList(items);
    }

    public static String title(String key, List<TvRecommendations.Feed> feeds) {
        for (TvRecommendations.Feed feed : feeds) if (feed.item.key().equals(key)) return feed.title;
        return title(key);
    }

    public static LayoutOptions defaults() { return LayoutOptions.defaults(320, 180); }

    public static String title(String key) {
        if (CLOCK.key().equals(key)) return "Clock";
        return key.startsWith("channel:") ? "Unavailable channel" : "Unavailable widget";
    }
}
