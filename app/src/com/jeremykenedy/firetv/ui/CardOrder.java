package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.media.tv.TvContract;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CardOrder {
    private CardOrder() {}

    static Set<String> saved(Context context) {
        return DeviceSettings.prefs(context).getStringSet(CardOrderRules.SETTING,
                Collections.<String>emptySet());
    }

    static String feed(Context context, String source, long channel) {
        if (channel < 0) return null;
        try (Cursor row = context.getContentResolver().query(TvContract.buildChannelUri(channel),
                new String[]{"package_name", "internal_provider_id", "display_name"}, null, null, null)) {
            if (row == null || !row.moveToFirst() || !source.equals(row.getString(0))) return null;
            String stable = row.getString(1);
            if (stable == null || stable.isEmpty()) stable = "name:" + row.getString(2);
            return FeaturedCardRules.key(source, false, "feed:" + stable, "");
        } catch (Exception unavailable) { return null; }
    }

    static List<HiddenCards.Card> sorted(Context context, List<HiddenCards.Card> cards) {
        List<HiddenCards.Card> result = new ArrayList<>(cards);
        final Set<String> order = saved(context);
        final Map<Long, String> feeds = new HashMap<>();
        for (HiddenCards.Card card : result)
            if (!feeds.containsKey(card.channel))
                feeds.put(card.channel, feed(context, card.source, card.channel));
        Collections.sort(result, new java.util.Comparator<HiddenCards.Card>() {
            @Override public int compare(HiddenCards.Card left, HiddenCards.Card right) {
                if (left.channel != right.channel) return Long.compare(left.channel, right.channel);
                String group = feeds.get(left.channel);
                if (group == null) return 0;
                return Integer.compare(CardOrderRules.rank(order, group, left.key),
                        CardOrderRules.rank(order, group, right.key));
            }
        });
        return result;
    }

    static void move(Context context, List<HiddenCards.Card> cards, HiddenCards.Card chosen, int direction) {
        String group = feed(context, chosen.source, chosen.channel);
        if (group == null) throw new IllegalStateException("This feed has no stable identity for ordering.");
        List<String> keys = new ArrayList<>();
        for (HiddenCards.Card card : cards)
            if (card.channel == chosen.channel && card.key != null) keys.add(card.key);
        Set<String> next = CardOrderRules.move(saved(context), group, keys, chosen.key, direction);
        if (!DeviceSettings.prefs(context).edit().putStringSet(CardOrderRules.SETTING, next).commit())
            throw new IllegalStateException("The card order could not be saved.");
    }

    public static Cursor prepare(Context context, Cursor original) {
        if (original == null || saved(context).isEmpty() || original.getCount() > CardOrderRules.LIMIT)
            return original;
        try {
            String[] columns = original.getColumnNames();
            final Map<Long, String> feeds = new HashMap<>();
            List<Row> rows = new ArrayList<>();
            Set<String> order = saved(context);
            while (original.moveToNext()) {
                Object[] values = new Object[columns.length];
                for (int i = 0; i < columns.length; i++) {
                    switch (original.getType(i)) {
                        case Cursor.FIELD_TYPE_NULL: values[i] = null; break;
                        case Cursor.FIELD_TYPE_INTEGER: values[i] = original.getLong(i); break;
                        case Cursor.FIELD_TYPE_FLOAT: values[i] = original.getDouble(i); break;
                        case Cursor.FIELD_TYPE_BLOB: values[i] = original.getBlob(i); break;
                        default: values[i] = original.getString(i);
                    }
                }
                HiddenCards.Card card = HiddenCards.identify(context, original, false);
                int rank = Integer.MAX_VALUE;
                if (card != null) {
                    if (!feeds.containsKey(card.channel))
                        feeds.put(card.channel, feed(context, card.source, card.channel));
                    String group = feeds.get(card.channel);
                    if (group != null) rank = CardOrderRules.rank(order, group, card.key);
                }
                rows.add(new Row(values, rank));
            }
            Collections.sort(rows, new java.util.Comparator<Row>() {
                @Override public int compare(Row left, Row right) { return Integer.compare(left.rank, right.rank); }
            });
            MatrixCursor sorted = new MatrixCursor(columns, rows.size());
            for (Row row : rows) sorted.addRow(row.values);
            original.close();
            return sorted;
        } catch (Exception unavailable) {
            original.moveToPosition(-1);
            return original;
        }
    }

    private static final class Row {
        final Object[] values;
        final int rank;
        Row(Object[] values, int rank) { this.values = values; this.rank = rank; }
    }
}
