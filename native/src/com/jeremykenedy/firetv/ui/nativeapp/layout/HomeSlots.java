package com.jeremykenedy.firetv.ui.nativeapp.layout;

import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;
import com.jeremykenedy.firetv.ui.nativeapp.widget.worldclock.WorldClockWidgetProvider;
import com.jeremykenedy.firetv.ui.nativeapp.widget.notes.NotesWidgetProvider;
import com.jeremykenedy.firetv.ui.nativeapp.widget.reminders.RemindersWidgetProvider;
import com.jeremykenedy.firetv.ui.nativeapp.widget.quickactions.QuickActionsWidgetProvider;
import com.jeremykenedy.firetv.ui.nativeapp.widget.astronomy.AstronomyWidgetProvider;
import com.jeremykenedy.firetv.ui.nativeapp.widget.photos.PhotoWidgetProvider;
import com.jeremykenedy.firetv.ui.nativeapp.widget.breathing.BreathingWidgetProvider;
import com.jeremykenedy.firetv.ui.nativeapp.widget.timers.CountdownWidgetProvider;
import com.jeremykenedy.firetv.ui.nativeapp.widget.timers.TimerWidgetProvider;
import com.jeremykenedy.firetv.ui.nativeapp.widget.status.NetworkStatusWidgetProvider;
import com.jeremykenedy.firetv.ui.nativeapp.widget.status.TvAudioStatusWidgetProvider;
import com.jeremykenedy.firetv.ui.nativeapp.widget.puzzles.DailyPuzzleWidgetProvider;
import com.jeremykenedy.firetv.ui.nativeapp.widget.calendar.CalendarWidgetProvider;
import com.jeremykenedy.firetv.ui.nativeapp.widget.ambient.AmbientWidgetProvider;
import com.jeremykenedy.firetv.ui.nativeapp.widget.livetv.LiveTvWidgetProvider;
import com.jeremykenedy.firetv.ui.nativeapp.widget.nowplaying.NowPlayingWidgetProvider;
import com.jeremykenedy.firetv.ui.nativeapp.widget.weather.WeatherWidgetProvider;
import com.jeremykenedy.firetv.ui.nativeapp.widget.feeds.FeedWidgetProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

/** Stable Home slots for implemented widgets and real published TV feeds. */
public final class HomeSlots {
    public static final LayoutItem CLOCK = new LayoutItem("clock", LayoutItem.Kind.WIDGET);
    public static final LayoutItem WORLD_CLOCKS = new LayoutItem("world-clocks", LayoutItem.Kind.WIDGET);
    private HomeSlots() {}

    public static List<WidgetProvider> widgets() {
        List<WidgetProvider> widgets = new ArrayList<>();
        widgets.add(new ClockWidgetProvider());
        widgets.add(new WorldClockWidgetProvider());
        widgets.add(new com.jeremykenedy.firetv.ui.nativeapp.widget.yearprogress.YearProgressWidgetProvider());
        widgets.add(new NotesWidgetProvider());
        widgets.add(new RemindersWidgetProvider());
        widgets.add(new QuickActionsWidgetProvider());
        widgets.add(new AstronomyWidgetProvider());
        widgets.add(new PhotoWidgetProvider());
        widgets.add(new BreathingWidgetProvider());
        widgets.add(new CountdownWidgetProvider());
        widgets.add(new TimerWidgetProvider());
        widgets.add(new NetworkStatusWidgetProvider());
        widgets.add(new TvAudioStatusWidgetProvider());
        widgets.add(new DailyPuzzleWidgetProvider());
        widgets.add(new CalendarWidgetProvider());
        widgets.add(new AmbientWidgetProvider());
        widgets.add(new LiveTvWidgetProvider());
        widgets.add(new NowPlayingWidgetProvider());
        widgets.add(new WeatherWidgetProvider());
        widgets.add(new com.jeremykenedy.firetv.ui.nativeapp.widget.radar.RadarWidgetProvider());
        widgets.add(new com.jeremykenedy.firetv.ui.nativeapp.widget.uv.UvWidgetProvider());
        widgets.add(new FeedWidgetProvider());
        widgets.addAll(com.jeremykenedy.firetv.ui.nativeapp.widget.dailycontent.DailyContentWidgets.providers());
        return Collections.unmodifiableList(widgets);
    }

    public static WidgetProvider widget(String key) {
        for (WidgetProvider widget : widgets()) if (widget.item().key().equals(key)) return widget;
        return null;
    }

    public static List<LayoutItem> available() {
        List<LayoutItem> items = new ArrayList<>();
        for (WidgetProvider widget : widgets()) items.add(widget.item());
        return Collections.unmodifiableList(items);
    }
    public static List<LayoutItem> available(List<TvRecommendations.Feed> feeds) {
        List<LayoutItem> items = new ArrayList<>(available());
        for (TvRecommendations.Feed feed : feeds) items.add(feed.item);
        return Collections.unmodifiableList(items);
    }

    public static LayoutOptions discover(LayoutOptions options, List<TvRecommendations.Feed> feeds) {
        Set<String> order = new LinkedHashSet<>(options.order);
        Set<String> hidden = new LinkedHashSet<>(options.hidden);
        for (WidgetProvider widget : widgets()) {
            String key = widget.item().key();
            if (order.contains(key) || order.size() >= LayoutOptions.MAX_ITEMS) continue;
            if (!widget.defaultVisible() && !hidden.contains(key) && hidden.size() >= LayoutOptions.MAX_ITEMS) continue;
            order.add(key);
            if (!widget.defaultVisible()) hidden.add(key);
        }
        for (TvRecommendations.Feed feed : feeds) {
            if (order.size() < LayoutOptions.MAX_ITEMS) order.add(feed.item.key());
        }
        return new LayoutOptions(options.cardWidthDp, options.cardHeightDp, order, hidden, options.mode);
    }

    public static int omittedCount(LayoutOptions options, List<TvRecommendations.Feed> feeds) {
        int omitted = 0;
        for (LayoutItem item : available(feeds)) if (!options.order.contains(item.key())) omitted++;
        return omitted;
    }

    public static String title(String key, List<TvRecommendations.Feed> feeds) {
        for (TvRecommendations.Feed feed : feeds) if (feed.item.key().equals(key)) return feed.title;
        return title(key);
    }

    public static LayoutOptions defaults() { return LayoutOptions.defaults(320, 180); }

    public static String title(String key) {
        WidgetProvider widget = widget(key);
        if (widget != null) return widget.title();
        return key.startsWith("channel:") ? "Unavailable channel" : "Unavailable widget";
    }
}
