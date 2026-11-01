package com.jeremykenedy.firetv.ui.nativeapp.layout;

import android.content.Context;
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
