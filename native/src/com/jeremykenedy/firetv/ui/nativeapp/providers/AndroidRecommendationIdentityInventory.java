package com.jeremykenedy.firetv.ui.nativeapp.providers;

import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import com.jeremykenedy.firetv.ui.nativeapp.migration.PresentationMappingContext;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exact observed legacy-to-native relationships. Call off the UI thread. */
public final class AndroidRecommendationIdentityInventory {
    private static final int MAX_ROWS = 4096;
    private AndroidRecommendationIdentityInventory() {}

    public static void collect(Context context, Map<String, PresentationMappingContext.Feed> feeds,
            Map<String, PresentationMappingContext.Item> items) throws IOException {
        TvRecommendations.Result current = TvRecommendations.read(context);
        if (current.status != TvRecommendations.Status.READY && current.status != TvRecommendations.Status.EMPTY)
            throw new IOException("TV recommendation identities could not be read completely.");
        Map<String, TvRecommendations.Feed> nativeFeeds = new LinkedHashMap<>();
        for (TvRecommendations.Feed feed : current.feeds) nativeFeeds.put(feed.item.id, feed);
        Map<String, PresentationMappingContext.Feed> foundFeeds = new LinkedHashMap<>();
        Map<String, PresentationMappingContext.Item> foundItems = new LinkedHashMap<>();
        Map<Long, String> channelOwners = new LinkedHashMap<>();
        try {
            try (Cursor rows = context.getContentResolver().query(TvContract.Channels.CONTENT_URI,
                    new String[]{"_id", "package_name", "internal_provider_id", "display_name"}, null, null, null)) {
                if (rows == null) throw new IOException("TV channel identities are unavailable.");
                int count = 0;
                while (rows.moveToNext()) {
                    if (++count > MAX_ROWS) throw new IOException("Too many TV channel identities to verify.");
                    String source = text(rows, "package_name");
                    long channel = rows.getLong(rows.getColumnIndexOrThrow("_id"));
                    if (!RecommendationKeys.validPackage(source) || channel <= 0) continue;
                    String nativeKey = RecommendationKeys.feed(source, false, channel);
                    if (!nativeFeeds.containsKey(nativeKey)) continue;
                    if (channelOwners.put(channel, source) != null) throw new IOException("Duplicate TV channel identity.");
                    addFeed(foundFeeds, LegacyRecommendationIdentities.feed(source, false,
                            text(rows, "internal_provider_id"), rows.getString(rows.getColumnIndexOrThrow("display_name"))), source, nativeKey);
                }
            }
            readPrograms(context, TvContract.PreviewPrograms.CONTENT_URI, false, channelOwners,
                    nativeFeeds, foundFeeds, foundItems);
            readPrograms(context, TvContract.WatchNextPrograms.CONTENT_URI, true, channelOwners,
                    nativeFeeds, foundFeeds, foundItems);
        } catch (RuntimeException unavailable) {
            throw new IOException("TV recommendation identity inventory could not be verified.", unavailable);
        }
        // Validate the complete batch before mutating the caller's inventory.
        Map<String, PresentationMappingContext.Feed> combinedFeeds = new LinkedHashMap<>(feeds);
        Map<String, PresentationMappingContext.Item> combinedItems = new LinkedHashMap<>(items);
        for (Map.Entry<String, PresentationMappingContext.Feed> entry : foundFeeds.entrySet()) {
            PresentationMappingContext.Feed feed = entry.getValue();
            addFeed(combinedFeeds, entry.getKey(), feed.source, feed.nativeKey);
        }
        for (Map.Entry<String, PresentationMappingContext.Item> entry : foundItems.entrySet()) {
            PresentationMappingContext.Item item = entry.getValue();
            addItem(combinedItems, entry.getKey(), item.source, item.nativeKey, item.title, item.aliasSource, item.aliasKey);
        }
        feeds.putAll(foundFeeds); items.putAll(foundItems);
    }

    private static void readPrograms(Context context, Uri uri, boolean watchNext, Map<Long, String> channels,
            Map<String, TvRecommendations.Feed> nativeFeeds, Map<String, PresentationMappingContext.Feed> feeds,
            Map<String, PresentationMappingContext.Item> items) throws IOException {
        String[] columns = watchNext
                ? new String[]{"_id", "package_name", "internal_provider_id", "content_id", "intent_uri"}
                : new String[]{"_id", "package_name", "internal_provider_id", "content_id", "intent_uri", "channel_id"};
        try (Cursor rows = context.getContentResolver().query(uri, columns, null, null, null)) {
            if (rows == null) throw new IOException("TV program identities are unavailable.");
            int count = 0;
            while (rows.moveToNext()) {
                if (++count > MAX_ROWS) throw new IOException("Too many TV program identities to verify.");
                String source = text(rows, "package_name");
                long rowId = rows.getLong(rows.getColumnIndexOrThrow("_id"));
                long channel = watchNext ? 0 : rows.getLong(rows.getColumnIndexOrThrow("channel_id"));
                if (!RecommendationKeys.validPackage(source) || rowId <= 0
                        || (!watchNext && !source.equals(channels.get(channel)))) continue;
                String nativeFeed = RecommendationKeys.feed(source, watchNext, channel);
                TvRecommendations.Feed feed = nativeFeeds.get(nativeFeed);
                if (feed == null) continue;
                String rawProvider = text(rows, "internal_provider_id");
                if (rawProvider.length() > 2048) continue;
                String originalKey = RecommendationKeys.program(nativeFeed, rowId, rawProvider.trim());
                String plexId = OwnedPlexIdentity.canonical(context, rows, source);
                String nativeItem = plexId == null ? originalKey : "plex:" + plexId;
                TvRecommendations.Program observed = null;
                for (TvRecommendations.Program program : feed.programs) if (program.key.equals(nativeItem)) observed = program;
                if (observed == null) continue;
                if (watchNext) addFeed(feeds, LegacyRecommendationIdentities.feed(source, true, "", ""), source, nativeFeed);
                if (source.equals(context.getPackageName()) && plexId == null) continue;
                String legacyItem = LegacyRecommendationIdentities.item(source, watchNext,
                        plexId == null ? rawProvider : plexId, text(rows, "content_id"));
                if (legacyItem != null) addItem(items, legacyItem, source, nativeItem, observed.title,
                        plexId == null ? null : "com.plexapp.android", plexId == null ? null : "plex:" + plexId);
            }
        }
    }

    private static void addFeed(Map<String, PresentationMappingContext.Feed> values, String legacy,
            String source, String nativeKey) throws IOException {
        PresentationMappingContext.Feed previous = values.get(legacy);
        if (previous != null && (!previous.source.equals(source) || !previous.nativeKey.equals(nativeKey)))
            throw new IOException("A legacy TV feed identity has multiple native matches.");
        values.put(legacy, new PresentationMappingContext.Feed(source, nativeKey));
    }
    private static void addItem(Map<String, PresentationMappingContext.Item> values, String legacy,
            String source, String nativeKey, String title, String aliasSource, String aliasKey) throws IOException {
        PresentationMappingContext.Item previous = values.get(legacy);
        if (previous != null && (!previous.source.equals(source) || !previous.nativeKey.equals(nativeKey) || previous.series || !java.util.Objects.equals(previous.aliasSource, aliasSource)
                || !java.util.Objects.equals(previous.aliasKey, aliasKey)))
            throw new IOException("A legacy TV item identity has multiple native matches.");
        values.put(legacy, new PresentationMappingContext.Item(source, nativeKey, title, false, aliasSource, aliasKey));
    }
    private static String text(Cursor rows, String column) {
        int index = rows.getColumnIndexOrThrow(column);
        return rows.isNull(index) ? "" : rows.getString(index);
    }
}
