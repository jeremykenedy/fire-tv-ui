package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FeedOrder {
    static final String SETTING = "featured_feed_order";
    private static final String GROUP = FeaturedCardRules.key("fire-tv-ui", false, "top-level-feeds", "");

    private static volatile List<String> current = Collections.emptyList();

    private FeedOrder() {}

    static Set<String> saved(Context context) {
        return DeviceSettings.prefs(context).getStringSet(SETTING, Collections.<String>emptySet());
    }

    public static String key(Context context, String source, long channel) {
        return channel < 0 ? FeaturedCardRules.key(source, true, "feed:watch-next", "")
                : CardOrder.feed(context, source, channel);
    }

    public static ArrayList<Object> order(Context context, List<?> cards, List<String> keys) {
        ArrayList<Object> result = new ArrayList<>();
        if (cards.size() != keys.size()) { result.addAll(cards); return result; }
        Set<String> saved = saved(context);
        List<String> positions = CardOrderRules.ordered(keys, saved, GROUP);
        current = new ArrayList<>(positions);
        if (saved.isEmpty()) { result.addAll(cards); return result; }
        for (String position : positions)
            for (int i = 0; i < cards.size(); i++)
                if (position.equals(keys.get(i))) result.add(cards.get(i));
        for (int i = 0; i < cards.size(); i++)
            if (keys.get(i) == null) result.add(cards.get(i));
        return result;
    }

    static List<Feed> read(Context context) {
        Map<String, Feed> feeds = new LinkedHashMap<>();
        try (Cursor rows = context.getContentResolver().query(TvContract.Channels.CONTENT_URI,
                new String[]{"_id", "package_name", "type", "display_name"}, null, null, null)) {
            if (rows == null) throw new IllegalStateException("Card feeds could not be read.");
            while (rows.moveToNext()) {
                if (!TvContract.Channels.TYPE_PREVIEW.equals(rows.getString(2))) continue;
                long id = rows.getLong(0);
                String source = rows.getString(1);
                if (ChannelCards.read(context, source, new long[]{id}).isEmpty()) continue;
                String key = key(context, source, id);
                if (key != null) feeds.put(key, new Feed(key, appName(context, source) + ": " + rows.getString(3)));
                if (feeds.size() >= 256) break;
            }
        }
        try (Cursor rows = context.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI,
                new String[]{"package_name"}, null, null, null)) {
            if (rows != null) while (rows.moveToNext()) {
                String source = rows.getString(0);
                String key = key(context, source, -1);
                if (key != null) feeds.put(key, new Feed(key, appName(context, source) + ": System Watch Next"));
            }
        }
        List<String> published = new ArrayList<>();
        for (String key : current) if (feeds.containsKey(key) && !published.contains(key)) published.add(key);
        for (String key : feeds.keySet()) if (!published.contains(key)) published.add(key);
        final List<String> ordered = CardOrderRules.ordered(published, saved(context), GROUP);
        List<Feed> result = new ArrayList<>();
        for (String key : ordered) result.add(feeds.get(key));
        return result;
    }

    public static String appName(Context context, String source) {
        try { return context.getPackageManager().getApplicationLabel(
                context.getPackageManager().getApplicationInfo(source, 0)).toString(); }
        catch (Exception unavailable) { return "Unavailable app"; }
    }

    static void move(Context context, List<Feed> feeds, Feed chosen, int direction) {
        List<String> keys = new ArrayList<>();
        for (Feed feed : feeds) keys.add(feed.key);
        Set<String> next = CardOrderRules.move(saved(context), GROUP, keys, chosen.key, direction);
        if (!DeviceSettings.prefs(context).edit().putStringSet(SETTING, next).commit())
            throw new IllegalStateException("The feed order could not be saved.");
    }

    static void reset(Context context) {
        if (!DeviceSettings.prefs(context).edit().remove(SETTING).commit())
            throw new IllegalStateException("The feed order could not be reset.");
    }

    static final class Feed {
        final String key, label;
        Feed(String key, String label) { this.key = key; this.label = label; }
    }
}
