package com.jeremykenedy.firetv.ui.nativeapp.cards;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CardOptions {
    public static final int DEFAULT_SECONDS = 8;
    public static final int MAX_SECONDS = 86400;
    public final int globalSeconds;
    public final Map<String, Integer> feedSeconds;
    private final Map<String, HiddenItem> hidden;

    public static final class HiddenItem {
        public final String source;
        public final String key;
        public final String title;
        private HiddenItem(String source, String key, String title) {
            this.source = source; this.key = key; this.title = title;
        }
    }

    private CardOptions(int seconds, Map<String, Integer> timers, Map<String, HiddenItem> hidden) {
        validateSeconds(seconds);
        if (timers.size() > 256 || hidden.size() > 512) throw new IllegalArgumentException("Too many saved card settings.");
        globalSeconds = seconds;
        feedSeconds = Collections.unmodifiableMap(new LinkedHashMap<>(timers));
        this.hidden = Collections.unmodifiableMap(new LinkedHashMap<>(hidden));
    }

    public static CardOptions defaults() {
        return new CardOptions(DEFAULT_SECONDS, Collections.<String, Integer>emptyMap(), Collections.<String, HiddenItem>emptyMap());
    }

    public int timerSeconds(String feed) {
        Integer seconds = feedSeconds.get(feed);
        return seconds == null ? globalSeconds : seconds;
    }

    public CardOptions withGlobalSeconds(int seconds) { return new CardOptions(seconds, feedSeconds, hidden); }

    public CardOptions withFeedSeconds(String feed, int seconds) {
        identity(feed); validateSeconds(seconds);
        Map<String, Integer> copy = new LinkedHashMap<>(feedSeconds);
        copy.put(feed, seconds);
        return new CardOptions(globalSeconds, copy, hidden);
    }

    public CardOptions useGlobal(String feed) {
        Map<String, Integer> copy = new LinkedHashMap<>(feedSeconds);
        copy.remove(feed);
        return new CardOptions(globalSeconds, copy, hidden);
    }

    public boolean isHidden(String source, String key) { return hidden.containsKey(hiddenKey(source, key)); }

    public CardOptions hide(String source, String key, String title) {
        String id = hiddenKey(source, key);
        if (title == null || title.length() > 512) throw new IllegalArgumentException("Invalid item title.");
        Map<String, HiddenItem> copy = new LinkedHashMap<>(hidden);
        copy.put(id, new HiddenItem(source, key, title));
        return new CardOptions(globalSeconds, feedSeconds, copy);
    }

    public CardOptions restore(String source, String key) {
        Map<String, HiddenItem> copy = new LinkedHashMap<>(hidden);
        copy.remove(hiddenKey(source, key));
        return new CardOptions(globalSeconds, feedSeconds, copy);
    }

    public List<HiddenItem> hiddenItems() { return Collections.unmodifiableList(new ArrayList<>(hidden.values())); }

    public Map<String, Object> backupValues() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("global_seconds", globalSeconds);
        StringBuilder timers = new StringBuilder();
        for (Map.Entry<String, Integer> entry : feedSeconds.entrySet()) {
            if (timers.length() > 0) timers.append('\n');
            timers.append(entry.getKey()).append('\t').append(entry.getValue());
        }
        result.put("feed_seconds", timers.toString());
        StringBuilder items = new StringBuilder();
        for (HiddenItem item : hidden.values()) {
            if (items.length() > 0) items.append('\n');
            items.append(item.source).append('\t').append(item.key).append('\t')
                    .append(Base64.getEncoder().encodeToString(item.title.getBytes(StandardCharsets.UTF_8)));
        }
        result.put("hidden_items", items.toString());
        return result;
    }

    public static CardOptions fromBackup(Map<String, ?> values) {
        if (values == null || values.size() != 3 || !(values.get("global_seconds") instanceof Integer)
                || !(values.get("feed_seconds") instanceof String) || !(values.get("hidden_items") instanceof String))
            throw new IllegalArgumentException("Invalid card settings.");
        CardOptions result = defaults().withGlobalSeconds((Integer) values.get("global_seconds"));
        String timers = (String) values.get("feed_seconds"), items = (String) values.get("hidden_items");
        if (timers.length() > 60000 || items.length() > 1500000) throw new IllegalArgumentException("Card settings are too large.");
        if (!timers.isEmpty()) for (String line : timers.split("\n", -1)) {
            String[] parts = line.split("\t", -1);
            if (parts.length != 2 || result.feedSeconds.containsKey(parts[0]) || !parts[1].matches("0|[1-9][0-9]{0,4}"))
                throw new IllegalArgumentException("Invalid channel timer.");
            result = result.withFeedSeconds(parts[0], Integer.parseInt(parts[1]));
        }
        if (!items.isEmpty()) for (String line : items.split("\n", -1)) {
            String[] parts = line.split("\t", -1);
            if (parts.length != 3 || result.isHidden(parts[0], parts[1])) throw new IllegalArgumentException("Invalid hidden item.");
            result = result.hide(parts[0], parts[1], new String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8));
        }
        return result;
    }

    public static String timerLabel(int seconds) { return seconds == 0 ? "Manual only" : "Every " + seconds + " seconds"; }

    private static void validateSeconds(int seconds) {
        if (seconds < 0 || seconds > MAX_SECONDS) throw new IllegalArgumentException("Choose Manual or whole seconds from 1 to 86400.");
    }

    private static String hiddenKey(String source, String key) {
        if (source == null || !source.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+") || source.length() > 200)
            throw new IllegalArgumentException("Invalid card source.");
        identity(key);
        return source + "\t" + key;
    }

    private static void identity(String value) {
        if (value == null || value.length() > 200 || !value.matches("[A-Za-z0-9.:_/-]+"))
            throw new IllegalArgumentException("This card has no stable identity.");
    }
}
