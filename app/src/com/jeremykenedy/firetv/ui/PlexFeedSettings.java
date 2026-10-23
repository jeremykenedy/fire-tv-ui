package com.jeremykenedy.firetv.ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

final class PlexFeedSettings {
    static final String[] KEYS = {"continue", "movies", "episodes", "playing", "history"};
    static final String[] LABELS = {
        "Continue Watching",
        "New movies",
        "New TV episodes",
        "Currently playing",
        "Recently watched"
    };
    final Set<String> enabled;
    final Set<String> users;
    final int cards;

    PlexFeedSettings(Set<String> enabled, Set<String> users, int cards) {
        if (!(cards == 4 || cards == 6 || cards == 8 || cards == 12))
            throw new IllegalArgumentException("Choose 4, 6, 8, or 12 cards per feed.");
        for (String key : enabled)
            if (!java.util.Arrays.asList(KEYS).contains(key))
                throw new IllegalArgumentException("Unknown Plex feed.");
        if (users.size() > 32) throw new IllegalArgumentException("Select up to 32 users.");
        for (String user : users)
            if (user == null || !user.matches("[0-9]{1,18}"))
                throw new IllegalArgumentException("Invalid Plex user identity.");
        this.enabled = Collections.unmodifiableSet(new LinkedHashSet<>(enabled));
        this.users = Collections.unmodifiableSet(new LinkedHashSet<>(users));
        this.cards = cards;
    }

    static PlexFeedSettings defaults() {
        return new PlexFeedSettings(
                new LinkedHashSet<>(java.util.Arrays.asList("continue", "movies", "episodes")),
                Collections.<String>emptySet(),
                8);
    }

    int limit() {
        return Math.min(cards, 24 / Math.max(1, enabled.size()));
    }

    boolean acceptsUser(String user) {
        return user != null
                && user.matches("[0-9]{1,18}")
                && (users.isEmpty() || users.contains(user));
    }

    JSONObject json() throws Exception {
        return new JSONObject()
                .put("enabled", new JSONArray(enabled))
                .put("users", new JSONArray(users))
                .put("cards", cards);
    }

    static PlexFeedSettings parse(JSONObject value) throws Exception {
        if (value == null) return defaults();
        return new PlexFeedSettings(
                strings(value.getJSONArray("enabled")),
                strings(value.getJSONArray("users")),
                value.getInt("cards"));
    }

    private static Set<String> strings(JSONArray values) throws Exception {
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i < values.length(); i++) result.add(values.getString(i));
        return result;
    }
}
