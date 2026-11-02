package com.jeremykenedy.firetv.ui.nativeapp.layout;

import android.content.Context;
import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LayoutTest {
    public static void main(String[] args) {
        LayoutItem movies = new LayoutItem("server-a:movies", LayoutItem.Kind.CHANNEL_CARD);
        LayoutItem clock = new LayoutItem("clock", LayoutItem.Kind.WIDGET);
        LayoutItem news = new LayoutItem("news", LayoutItem.Kind.CHANNEL_CARD);
        LayoutItem sameIdWidget = new LayoutItem("news", LayoutItem.Kind.WIDGET);
        List<LayoutItem> available = Arrays.asList(movies, clock, news);
        LayoutOptions options = LayoutOptions.defaults(320, 180).discover(available);
        options = options.moveBefore(clock.key(), movies.key());
        check(options.visible(available).equals(Arrays.asList(clock, movies, news)), "Widgets and channels share one explicit order.");
        options = options.discover(Arrays.asList(news, clock));
        check(options.order.contains(movies.key()), "A temporarily missing provider keeps its saved position.");
        check(options.visible(Arrays.asList(news, clock)).equals(Arrays.asList(clock, news)), "Missing providers are not fabricated.");
        check(options.visible(Arrays.asList(news, movies, clock)).equals(Arrays.asList(clock, movies, news)), "Reappearing providers regain their order.");
        options = options.discover(Arrays.asList(news, clock, sameIdWidget));
        check(options.order.get(3).equals(sameIdWidget.key()), "New items append and widget/channel identities do not collide.");
        options = options.setVisible(movies.key(), false);
        check(!options.visible(available).contains(movies), "Visibility is independent of provider availability.");
        options = options.setVisible(movies.key(), true).resizeCards(400, 225);
        for (LayoutItem card : Arrays.asList(news, movies)) {
            check(options.widthDp(card, 70) == 400 && options.heightDp(card, 90) == 225, "Every channel card uses the global size.");
        }
        check(options.widthDp(clock, 300) == 300 && options.heightDp(clock, 72) == 72, "Widget dimensions remain independent.");
        check(LayoutOptions.fromBackup(options.backupValues()).backupValues().equals(options.backupValues()), "Backup round-trip preserves complete layout.");
        TvRecommendations.Feed feed = new TvRecommendations.Feed("com.example.tv:preview:12", "Shared movies");
        List<TvRecommendations.Feed> feeds = Collections.singletonList(feed);
        List<LayoutItem> discovered = HomeSlots.available(feeds);
        check(discovered.contains(HomeSlots.CLOCK) && discovered.get(discovered.size() - 1).equals(feed.item),
                "Real feeds join implemented widgets as stable slots.");
        check(HomeSlots.available().contains(HomeSlots.CLOCK), "The local clock baseline stays available.");
        check(HomeSlots.title(feed.item.key(), feeds).equals("Shared movies"), "Editor uses the publisher's feed title.");
        check(HomeSlots.title(feed.item.key(), Collections.<TvRecommendations.Feed>emptyList()).equals("Unavailable channel"),
                "Missing feeds keep an explicit unavailable title.");
        LayoutOptions feedLayout = HomeSlots.defaults().discover(discovered).moveBefore(feed.item.key(), HomeSlots.CLOCK.key())
                .setVisible(feed.item.key(), false);
        LayoutOptions missingFeed = feedLayout.discover(HomeSlots.available());
        check(missingFeed.order.equals(feedLayout.order) && missingFeed.hidden.equals(feedLayout.hidden),
                "A missing real feed preserves saved order and visibility.");
        check(keys(missingFeed.discover(discovered).visible(discovered)).equals(keys(HomeSlots.available())),
                "A reappearing hidden feed stays hidden.");
        Map<String, Object> legacy = new HashMap<>(feedLayout.backupValues());
        legacy.remove("mode");
        check(LayoutOptions.fromBackup(legacy).mode == LayoutOptions.Mode.BOTH, "Legacy layout backups retain both content kinds.");
        LayoutOptions cardsOnly = feedLayout.withMode(LayoutOptions.Mode.CARDS_ONLY);
        check(cardsOnly.visible(discovered).isEmpty(), "Cards-only mode preserves an individually hidden card.");
        LayoutOptions none = feedLayout.withMode(LayoutOptions.Mode.NONE);
        check(none.visible(discovered).isEmpty() && none.order.equals(feedLayout.order) && none.hidden.equals(feedLayout.hidden),
                "Hiding all content retains exact saved order and individual visibility choices.");
        check(LayoutOptions.fromBackup(none.backupValues()).mode == LayoutOptions.Mode.NONE,
                "The explicit all-content-hidden choice survives backup restoration.");
        check(none.withMode(LayoutOptions.Mode.BOTH).backupValues().equals(feedLayout.backupValues()),
                "Leaving all-content-hidden mode restores the prior saved choices.");
        LayoutOptions widgetsOnly = feedLayout.withMode(LayoutOptions.Mode.WIDGETS_ONLY);
        check(keys(widgetsOnly.visible(discovered)).equals(keys(HomeSlots.available())), "Widgets-only mode filters cards independently of hidden flags.");
        check(cardsOnly.order.equals(feedLayout.order) && cardsOnly.hidden.equals(feedLayout.hidden),
                "Mode changes keep exact saved order and hidden choices.");
        check(widgetsOnly.withMode(LayoutOptions.Mode.BOTH).backupValues().equals(feedLayout.backupValues()),
                "Returning to both restores the exact layout choices.");
        check(LayoutOptions.fromBackup(cardsOnly.backupValues()).mode == LayoutOptions.Mode.CARDS_ONLY,
                "Content mode survives backup restoration.");
        check(cardsOnly.resizeCards(400, 225).setVisible(feed.item.key(), true).moveBefore(HomeSlots.CLOCK.key(), feed.item.key())
                .discover(HomeSlots.available()).mode == LayoutOptions.Mode.CARDS_ONLY, "Every layout mutation retains content mode.");
        check(HomeSlots.widget(HomeSlots.CLOCK.key()) != null && HomeSlots.widget("widget:missing") == null,
                "Only implemented local widgets resolve through the registry.");
        LayoutOptions firstDiscovery = HomeSlots.discover(HomeSlots.defaults(), feeds);
        check(firstDiscovery.visible(discovered).equals(Arrays.asList(feed.item)),
                "Widgets start hidden, matching the existing launcher default.");
        for (WidgetProvider widget : HomeSlots.widgets()) {
            if (widget.defaultVisible()) continue;
            check(firstDiscovery.hidden.contains(widget.item().key()), "New opt-in widgets start hidden.");
            LayoutOptions chosen = firstDiscovery.setVisible(widget.item().key(), true);
            check(!HomeSlots.discover(chosen, feeds).hidden.contains(widget.item().key()),
                    "Discovery preserves an explicit Show choice for a widget.");
        }
        List<String> historical = new java.util.ArrayList<>();
        for (int index = 0; index < LayoutOptions.MAX_ITEMS; index++) historical.add("channel:historical-" + index);
        LayoutOptions full = new LayoutOptions(320, 180, historical, Collections.singletonList(historical.get(0)));
        LayoutOptions bounded = HomeSlots.discover(full, feeds);
        check(bounded.order.equals(full.order) && bounded.hidden.equals(full.hidden),
                "At capacity, discovery retains every historical identity and hidden choice without crashing.");
        check(HomeSlots.omittedCount(bounded, feeds) == HomeSlots.available(feeds).size(),
                "Capacity exposes an exact count of omitted new widgets and feeds.");
        check(full.discover(discovered).order.equals(full.order), "Generic discovery also stays bounded at saved capacity.");
        check(bounded.visible(discovered).isEmpty(), "Capacity-omitted slots are not implicitly displayed by the layout model.");
        LayoutOptions oneSpace = new LayoutOptions(320, 180, historical.subList(0, historical.size() - 1),
                Collections.<String>emptyList());
        LayoutOptions filled = HomeSlots.discover(oneSpace, feeds);
        check(filled.order.size() == LayoutOptions.MAX_ITEMS && filled.order.subList(0, historical.size() - 1).equals(oneSpace.order),
                "Discovery appends only the remaining capacity while keeping historical order.");
        List<String> hiddenAtLimit = new java.util.ArrayList<>();
        for (int index = 0; index < LayoutOptions.MAX_ITEMS; index++) hiddenAtLimit.add("widget:missing-" + index);
        LayoutOptions hiddenFull = new LayoutOptions(320, 180, Collections.singletonList(HomeSlots.CLOCK.key()), hiddenAtLimit);
        LayoutOptions hiddenBounded = HomeSlots.discover(hiddenFull, feeds);
        check(hiddenBounded.hidden.equals(hiddenFull.hidden) && hiddenBounded.order.contains(feed.item.key())
                && !hiddenBounded.order.contains(HomeSlots.WORLD_CLOCKS.key()),
                "A full hidden set retains saved choices and omits opt-in widgets that cannot be safely hidden.");
        Context discoveryContext = new Context();
        discoveryContext.prefs.values.put("unrelated", "keep");
        LayoutOptions absent = LayoutPreferences.load(discoveryContext, HomeSlots.defaults());
        LayoutOptions localDiscovered = absent.discover(Collections.singletonList(HomeSlots.CLOCK));
        LayoutPreferences.saveDiscovery(discoveryContext, absent, localDiscovered);
        check(discoveryContext.prefs.values.size() == 2 && discoveryContext.prefs.values.containsKey("order")
                && discoveryContext.prefs.values.get("unrelated").equals("keep"),
                "Discovery only writes changed order; absent dimensions, mode and unchanged hidden stay absent.");
        LayoutOptions widgetsDiscovered = HomeSlots.discover(localDiscovered, feeds);
        LayoutPreferences.saveDiscovery(discoveryContext, localDiscovered, widgetsDiscovered);
        check(discoveryContext.prefs.values.size() == 3 && discoveryContext.prefs.values.containsKey("hidden"),
                "Opt-in widget discovery persists hidden defaults without materializing presentation defaults.");
        check(LayoutPreferences.load(discoveryContext, HomeSlots.defaults()).hidden.equals(widgetsDiscovered.hidden),
                "Discovery's saved opt-in visibility survives reload.");
        discoveryContext.prefs.values.put("mode", "WIDGETS_ONLY");
        discoveryContext.prefs.values.put("card_width_dp", 450);
        Map<String, Object> beforeDiscoveryFailure = new HashMap<>(discoveryContext.prefs.values);
        discoveryContext.prefs.fail = true;
        LayoutPreferences.saveDiscovery(discoveryContext, widgetsDiscovered, widgetsDiscovered);
        try {
            LayoutPreferences.saveDiscovery(discoveryContext, widgetsDiscovered,
                    widgetsDiscovered.discover(Collections.singletonList(new LayoutItem("new-channel", LayoutItem.Kind.CHANNEL_CARD))));
            throw new AssertionError("Failed discovery write accepted.");
        } catch (IllegalStateException expected) {
            check(discoveryContext.prefs.values.equals(beforeDiscoveryFailure),
                    "Failed discovery rolls back only its changed keys and preserves exact explicit and absent values.");
        }
        Context context = new Context();
        context.prefs.values.put("unrelated", "keep");
        LayoutPreferences.save(context, options);
        check(LayoutPreferences.load(context, LayoutOptions.defaults(250, 150)).backupValues().equals(options.backupValues()), "Saved layout survives reload.");
        Map<String, Object> before = new HashMap<>(context.prefs.values);
        for (Object bad : Arrays.<Object>asList("not-a-size", -1, 9000, 123L)) {
            Map<String, Object> invalid = new HashMap<>(options.backupValues());
            invalid.put("card_width_dp", bad);
            rejected(context, invalid, before);
        }
        for (String bad : Arrays.asList("channel:a\nchannel:a", "channel:bad title", "widget:clock\n", "unknown:x")) {
            Map<String, Object> invalid = new HashMap<>(options.backupValues());
            invalid.put("order", bad);
            rejected(context, invalid, before);
        }
        for (Object badMode : Arrays.<Object>asList("CARDS", 1, true)) {
            Map<String, Object> invalid = new HashMap<>(options.backupValues());
            invalid.put("mode", badMode);
            rejected(context, invalid, before);
        }
        Map<String, Object> extra = new HashMap<>(options.backupValues());
        extra.put("unknown", true);
        rejected(context, extra, before);
        context.prefs.fail = true;
        try {
            LayoutPreferences.save(context, options.resizeCards(350, 200));
            throw new AssertionError("Failed write accepted.");
        } catch (IllegalStateException expected) {
            check(context.prefs.values.equals(before), "Failed write rolls back memory and preserves unrelated settings.");
        }
        context.prefs.values.put("card_width_dp", true);
        Map<String, Object> malformedBefore = new HashMap<>(context.prefs.values);
        try {
            LayoutPreferences.save(context, options);
            throw new AssertionError("Failed write accepted.");
        } catch (IllegalStateException expected) {
            check(context.prefs.values.equals(malformedBefore), "Rollback preserves even malformed previous values.");
        }
        context.prefs.fail = false;
        context.prefs.values.put("order", "malformed");
        LayoutOptions defaults = LayoutOptions.defaults(280, 160);
        check(LayoutPreferences.load(context, defaults) == defaults, "Corrupt persisted layout returns supplied current defaults.");
        try {
            new LayoutOptions(320, 180, Collections.nCopies(513, "widget:a"), Collections.<String>emptyList());
            throw new AssertionError("Unbounded layout accepted.");
        } catch (IllegalArgumentException expected) {}
        System.out.println("Mixed order, missing-provider retention, uniform card size, widget independence, storage and atomic backup validation passed.");
    }

    private static List<String> keys(List<LayoutItem> items) {
        List<String> keys = new java.util.ArrayList<>();
        for (LayoutItem item : items) keys.add(item.key());
        return keys;
    }

    private static void rejected(Context context, Map<String, Object> values, Map<String, Object> before) {
        try {
            LayoutPreferences.restore(context, values);
            throw new AssertionError("Malformed backup accepted.");
        } catch (IllegalArgumentException expected) {
            check(before.equals(context.prefs.values), "Invalid backup must not partially change saved settings.");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
