package com.jeremykenedy.firetv.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Monotonic deadlines belong to stable feeds, independently of their title or screen position. */
final class FeaturedCardSchedule {
    private final Map<String, Long> deadlines = new HashMap<>();
    private final Map<String, Integer> intervals = new HashMap<>();

    void clear() { deadlines.clear(); intervals.clear(); }

    void sync(Map<String, Integer> active, long now) {
        deadlines.keySet().retainAll(active.keySet());
        intervals.keySet().retainAll(active.keySet());
        for (Map.Entry<String, Integer> entry : active.entrySet()) {
            String feed = entry.getKey();
            int value = entry.getValue();
            if (value <= 0 || value > FeaturedCardTiming.MAX_CUSTOM_SECONDS) {
                deadlines.remove(feed); intervals.remove(feed);
            } else if (!Integer.valueOf(value).equals(intervals.get(feed))) {
                intervals.put(feed, value);
                deadlines.put(feed, now + value * 1000L);
            }
        }
    }

    List<String> due(long now) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Long> entry : deadlines.entrySet()) {
            if (entry.getValue() <= now) {
                result.add(entry.getKey());
                entry.setValue(now + intervals.get(entry.getKey()) * 1000L);
            }
        }
        return result;
    }

    long delay(long now) {
        long next = Long.MAX_VALUE;
        for (long deadline : deadlines.values()) next = Math.min(next, Math.max(1, deadline - now));
        return next;
    }
}
