package com.jeremykenedy.firetv.ui;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keeps a feed's position separate from the item currently displayed inside it. */
public final class FeaturedGroups {
    private static final Map<String, Group> feeds = new LinkedHashMap<>();
    private static final Map<String, String> feedKeys = new LinkedHashMap<>();
    private static final Map<String, String> pendingFeedKeys = new LinkedHashMap<>();
    private static final Map<Object, Group> slots = new IdentityHashMap<>();

    private FeaturedGroups() {}

    public static synchronized void prepare() {
        pendingFeedKeys.clear();
    }

    public static synchronized String feedKey(android.content.Context context, String source, long channel) {
        String lookup = source + ":" + channel;
        if (!pendingFeedKeys.containsKey(lookup)) pendingFeedKeys.put(lookup, FeedOrder.key(context, source, channel));
        return pendingFeedKeys.get(lookup);
    }

    public static String itemKey(String source, android.net.Uri intent, boolean watchNext) {
        HiddenCards.Card card = HiddenCards.card(source, intent, watchNext);
        return card == null ? FeaturedCardRules.lookup(source, watchNext,
                intent == null ? "" : intent.toString()) : card.key;
    }

    public static synchronized ArrayList<Object> group(List<?> cards, List<String> feedKeys,
            List<String> itemKeys) {
        if (cards.size() != feedKeys.size() || cards.size() != itemKeys.size())
            throw new IllegalArgumentException("Card identities do not match their feeds.");
        Map<String, Group> next = new LinkedHashMap<>();
        for (int i = 0; i < cards.size(); i++) {
            String key = feedKeys.get(i);
            if (key == null) key = "unshared:" + i;
            Group group = next.get(key);
            if (group == null) {
                group = new Group();
                next.put(key, group);
            }
            String item = itemKeys.get(i);
            if (!group.ids.contains(item)) {
                group.ids.add(item);
                group.cards.add(cards.get(i));
            }
        }
        Map<String, Group> stable = new LinkedHashMap<>();
        for (String key : feeds.keySet()) if (next.containsKey(key)) stable.put(key, next.get(key));
        for (Map.Entry<String, Group> entry : next.entrySet()) stable.put(entry.getKey(), entry.getValue());
        ArrayList<Object> result = new ArrayList<>();
        slots.clear();
        for (Map.Entry<String, Group> entry : stable.entrySet()) {
            Group group = entry.getValue();
            Group previous = feeds.get(entry.getKey());
            if (previous != null) {
                int retained = group.ids.indexOf(previous.ids.get(previous.index));
                if (retained >= 0) group.index = retained;
            }
            Object slot = group.cards.get(0);
            slots.put(slot, group);
            result.add(slot);
        }
        feeds.clear();
        feeds.putAll(stable);
        FeaturedGroups.feedKeys.clear();
        FeaturedGroups.feedKeys.putAll(pendingFeedKeys);
        return result;
    }

    public static synchronized String feedKey(String source, long channel) {
        return feedKeys.get(source + ":" + channel);
    }

    public static synchronized Object current(Object slot) {
        return current(slot, null);
    }

    public static synchronized Object current(Object slot, String feedKey) {
        Group group = slots.get(slot);
        if (group == null && feedKey != null) group = feeds.get(feedKey);
        return group == null ? slot : group.cards.get(group.index);
    }

    public static synchronized void advance() {
        for (Group group : feeds.values()) group.index = (group.index + 1) % group.cards.size();
    }

    static synchronized boolean move(String feed, int direction) {
        Group group = feeds.get(feed);
        if (group == null || group.cards.size() < 2) return false;
        group.index = Math.floorMod(group.index + direction, group.cards.size());
        return true;
    }

    private static final class Group {
        final List<Object> cards = new ArrayList<>();
        final List<String> ids = new ArrayList<>();
        int index;
    }
}
