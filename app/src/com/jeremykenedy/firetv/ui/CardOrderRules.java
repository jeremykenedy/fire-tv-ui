package com.jeremykenedy.firetv.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class CardOrderRules {
    static final String SETTING = "featured_card_order";
    static final int LIMIT = 4096;

    private CardOrderRules() {}

    static boolean valid(String record) {
        return record != null && record.matches("[a-f0-9]{64}:[0-9]{4}:[a-f0-9]{64}")
                && Integer.parseInt(record.substring(65, 69)) < LIMIT;
    }

    static int rank(Set<String> saved, String feed, String item) {
        for (String record : saved)
            if (valid(record) && record.startsWith(feed + ":") && record.endsWith(":" + item))
                return Integer.parseInt(record.substring(65, 69));
        return Integer.MAX_VALUE;
    }

    static List<String> ordered(List<String> published, Set<String> saved, final String feed) {
        List<String> result = new ArrayList<>();
        for (String key : published)
            if (FeaturedCardRules.validKey(key) && !result.contains(key)) result.add(key);
        final Set<String> snapshot = saved;
        Collections.sort(result, new java.util.Comparator<String>() {
            @Override public int compare(String left, String right) {
                return Integer.compare(rank(snapshot, feed, left), rank(snapshot, feed, right));
            }
        });
        return result;
    }

    static Set<String> move(Set<String> saved, String feed, List<String> published,
            String item, int direction) {
        if (!FeaturedCardRules.validKey(feed) || (direction != -1 && direction != 1))
            throw new IllegalArgumentException("Invalid card order.");
        List<String> order = ordered(published, saved, feed);
        int from = order.indexOf(item);
        if (from < 0) throw new IllegalArgumentException("This card is no longer available.");
        int to = Math.max(0, Math.min(order.size() - 1, from + direction));
        Collections.swap(order, from, to);
        Set<String> result = new HashSet<>(saved);
        for (String record : saved) if (record.startsWith(feed + ":")) result.remove(record);
        if (result.size() + order.size() > LIMIT)
            throw new IllegalStateException("There are too many saved card positions.");
        for (int i = 0; i < order.size(); i++)
            result.add(feed + ":" + String.format(java.util.Locale.ROOT, "%04d", i) + ":" + order.get(i));
        return result;
    }
}
