package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class HiddenCards {
    static final String SETTING = "hidden_featured_cards";
    private static final Map<String, Card> cards = new LinkedHashMap<>();

    private HiddenCards() {}

    public static boolean visible(Context context, Cursor cursor, boolean watchNext) {
        String source = value(cursor, "package_name");
        String intent = value(cursor, "intent_uri");
        String key =
                FeaturedCardRules.key(
                        source,
                        watchNext,
                        providerId(context, source, value(cursor, "internal_provider_id"), intent),
                        value(cursor, "content_id"));
        if (key == null) return true;
        synchronized (cards) {
            cards.put(
                    FeaturedCardRules.lookup(source, watchNext, intent),
                    new Card(key, source, value(cursor, "title")));
            while (cards.size() > 2048) cards.remove(cards.keySet().iterator().next());
        }
        return !keys(context).contains(key);
    }

    private static String providerId(Context context, String source, String id, String intent) {
        if (!context.getPackageName().equals(source)) return id;
        try {
            android.content.Intent launch =
                    android.content.Intent.parseUri(
                            intent, android.content.Intent.URI_INTENT_SCHEME);
            Uri data = launch.getData();
            if (launch.getComponent() == null
                    || !(source + ".PlexItemActivity").equals(launch.getComponent().getClassName())
                    || data == null
                    || !"firetvui".equals(data.getScheme())
                    || !"plex".equals(data.getAuthority())
                    || data.getQuery() != null
                    || data.getFragment() != null
                    || !PlexValues.itemPath(data.getEncodedPath())) return id;
            String item = data.getPathSegments().get(2);
            String canonical = PlexValues.providerItemId(data.getPathSegments().get(1), item);
            return id.equals(item) || id.equals(canonical) ? canonical : id;
        } catch (Exception unavailable) {
            return id;
        }
    }

    static Card card(String source, Uri intent, boolean watchNext) {
        synchronized (cards) {
            return cards.get(
                    FeaturedCardRules.lookup(
                            source, watchNext, intent == null ? "" : intent.toString()));
        }
    }

    static boolean scan(Context context) {
        boolean available = true;
        for (String table : new String[] {"preview_program", "watch_next_program"}) {
            try (Cursor cursor =
                    context.getContentResolver()
                            .query(
                                    Uri.parse("content://android.media.tv/" + table),
                                    new String[] {
                                        "package_name",
                                        "title",
                                        "internal_provider_id",
                                        "content_id",
                                        "intent_uri"
                                    },
                                    null,
                                    null,
                                    null)) {
                if (cursor == null) {
                    available = false;
                    continue;
                }
                int count = 0;
                while (count++ < 4096 && cursor.moveToNext())
                    visible(context, cursor, "watch_next_program".equals(table));
            } catch (Exception unavailable) {
                available = false;
            }
        }
        return available;
    }

    static Set<String> keys(Context context) {
        return new HashSet<>(
                DeviceSettings.prefs(context)
                        .getStringSet(SETTING, java.util.Collections.<String>emptySet()));
    }

    static void hide(Context context, String key) {
        if (!FeaturedCardRules.validKey(key))
            throw new IllegalArgumentException("Invalid card identity.");
        Set<String> hidden = keys(context);
        if (hidden.size() >= 2048 && !hidden.contains(key))
            throw new IllegalStateException("Restore a hidden card before hiding another.");
        hidden.add(key);
        save(context, hidden);
    }

    static void restore(Context context, String key) {
        Set<String> hidden = keys(context);
        hidden.remove(key);
        save(context, hidden);
    }

    static void restoreAll(Context context) {
        save(context, java.util.Collections.<String>emptySet());
    }

    static String label(String key) {
        synchronized (cards) {
            for (Card card : cards.values()) if (card.key.equals(key)) return card.title;
        }
        return "Unavailable card (" + key.substring(0, 8) + ")";
    }

    private static void save(Context context, Set<String> hidden) {
        if (!DeviceSettings.prefs(context).edit().putStringSet(SETTING, hidden).commit())
            throw new IllegalStateException("The hidden card change could not be saved.");
    }

    private static String value(Cursor cursor, String name) {
        int index = cursor.getColumnIndex(name);
        return index < 0 || cursor.isNull(index) ? "" : cursor.getString(index);
    }

    static final class Card {
        final String key;
        final String source;
        final String title;

        Card(String key, String source, String title) {
            this.key = key;
            this.source = source;
            this.title = title;
        }
    }
}
