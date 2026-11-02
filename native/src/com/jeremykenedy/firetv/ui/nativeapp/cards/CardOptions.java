package com.jeremykenedy.firetv.ui.nativeapp.cards;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CardOptions {
    public static final int DEFAULT_SECONDS = 8;
    public static final int MAX_SECONDS = 86400;
    public final int globalSeconds;
    public final Map<String, Integer> feedSeconds;
    public final Set<String> hiddenPackages;
    private final Map<String, HiddenItem> hidden;
    private final Map<String, HiddenItem> series;
    private final Map<String, List<String>> itemOrder;

    public static final class HiddenItem {
        public final String source;
        public final String key;
        public final String title;
        private HiddenItem(String source, String key, String title) {
            this.source = source; this.key = key; this.title = title;
        }
    }

    private CardOptions(int seconds, Map<String, Integer> timers, Map<String, HiddenItem> hidden,
            Map<String, HiddenItem> series, Map<String, List<String>> order, Set<String> hiddenPackages) {
        validateSeconds(seconds);
        this.hiddenPackages = decodePackages(encodePackages(hiddenPackages));
        if (timers.size() > 256 || hidden.size() + series.size() > 512 || order.size() > 256)
            throw new IllegalArgumentException("Too many saved card settings.");
        int items = 0;
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : order.entrySet()) {
            if (entry.getValue().size() > 512) throw new IllegalArgumentException("Too many saved titles in one channel.");
            items += entry.getValue().size();
            copy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        if (items > 4096) throw new IllegalArgumentException("Too many saved title positions.");
        globalSeconds = seconds;
        feedSeconds = Collections.unmodifiableMap(new LinkedHashMap<>(timers));
        this.hidden = Collections.unmodifiableMap(new LinkedHashMap<>(hidden));
        this.series = Collections.unmodifiableMap(new LinkedHashMap<>(series));
        itemOrder = Collections.unmodifiableMap(copy);
    }

    public static CardOptions defaults() {
        return new CardOptions(DEFAULT_SECONDS, Collections.<String, Integer>emptyMap(),
                Collections.<String, HiddenItem>emptyMap(), Collections.<String, HiddenItem>emptyMap(),
                Collections.<String, List<String>>emptyMap(), Collections.<String>emptySet());
    }

    public int timerSeconds(String feed) {
        Integer seconds = feedSeconds.get(feed);
        return seconds == null ? globalSeconds : seconds;
    }

    public CardOptions withGlobalSeconds(int seconds) { return new CardOptions(seconds, feedSeconds, hidden, series, itemOrder, hiddenPackages); }

    public CardOptions withFeedSeconds(String feed, int seconds) {
        identity(feed); validateSeconds(seconds);
        Map<String, Integer> copy = new LinkedHashMap<>(feedSeconds);
        copy.put(feed, seconds);
        return new CardOptions(globalSeconds, copy, hidden, series, itemOrder, hiddenPackages);
    }

    public CardOptions useGlobal(String feed) {
        Map<String, Integer> copy = new LinkedHashMap<>(feedSeconds);
        copy.remove(feed);
        return new CardOptions(globalSeconds, copy, hidden, series, itemOrder, hiddenPackages);
    }

    public boolean isHidden(String source, String key) { return hidden.containsKey(scopedKey(source, key)); }

    public boolean isHidden(String source, String key, String seriesKey) {
        return isHidden(source, key) || isSeriesHidden(source, seriesKey);
    }

    public boolean isSeriesHidden(String source, String seriesKey) {
        return seriesKey != null && !seriesKey.isEmpty() && series.containsKey(scopedKey(source, seriesKey));
    }

    public CardOptions hide(String source, String key, String title) {
        Map<String, HiddenItem> copy = new LinkedHashMap<>(hidden);
        copy.put(scopedKey(source, key), entry(source, key, title));
        return new CardOptions(globalSeconds, feedSeconds, copy, series, itemOrder, hiddenPackages);
    }

    public CardOptions hideSeries(String source, String key, String title) {
        Map<String, HiddenItem> copy = new LinkedHashMap<>(series);
        copy.put(scopedKey(source, key), entry(source, key, title));
        return new CardOptions(globalSeconds, feedSeconds, hidden, copy, itemOrder, hiddenPackages);
    }

    public CardOptions restore(String source, String key) {
        Map<String, HiddenItem> copy = new LinkedHashMap<>(hidden);
        copy.remove(scopedKey(source, key));
        return new CardOptions(globalSeconds, feedSeconds, copy, series, itemOrder, hiddenPackages);
    }

    public CardOptions restoreSeries(String source, String key) {
        Map<String, HiddenItem> copy = new LinkedHashMap<>(series);
        copy.remove(scopedKey(source, key));
        return new CardOptions(globalSeconds, feedSeconds, hidden, copy, itemOrder, hiddenPackages);
    }

    public List<HiddenItem> hiddenItems() { return Collections.unmodifiableList(new ArrayList<>(hidden.values())); }
    public List<HiddenItem> hiddenSeries() { return Collections.unmodifiableList(new ArrayList<>(series.values())); }

    public List<String> orderedKeys(String source, String feed, List<String> available) {
        String id = scopedKey(source, feed);
        List<String> current = validatedOrder(available);
        List<String> result = new ArrayList<>();
        List<String> saved = itemOrder.get(id);
        if (saved != null) for (String key : saved) if (current.contains(key)) result.add(key);
        for (String key : current) if (!result.contains(key)) result.add(key);
        return Collections.unmodifiableList(result);
    }

    public CardOptions withItemOrder(String source, String feed, List<String> order) {
        Map<String, List<String>> copy = new LinkedHashMap<>(itemOrder);
        copy.put(scopedKey(source, feed), validatedOrder(order));
        return new CardOptions(globalSeconds, feedSeconds, hidden, series, copy, hiddenPackages);
    }

    public CardOptions discoverItems(String source, String feed, List<String> available) {
        String id = scopedKey(source, feed);
        List<String> order = new ArrayList<>();
        if (itemOrder.containsKey(id)) order.addAll(itemOrder.get(id));
        for (String key : validatedOrder(available)) if (!order.contains(key)) order.add(key);
        return withItemOrder(source, feed, order);
    }

    public CardOptions moveItemBefore(String source, String feed, String item, String before) {
        String id = scopedKey(source, feed);
        List<String> order = new ArrayList<>(itemOrder.containsKey(id) ? itemOrder.get(id) : Collections.<String>emptyList());
        if (!order.contains(item) || (before != null && !order.contains(before)))
            throw new IllegalArgumentException("Refresh this channel before changing its title order.");
        if (item.equals(before)) return this;
        order.remove(item);
        order.add(before == null ? order.size() : order.indexOf(before), item);
        return withItemOrder(source, feed, order);
    }

    public CardOptions resetItemOrder(String source, String feed) {
        Map<String, List<String>> copy = new LinkedHashMap<>(itemOrder);
        copy.remove(scopedKey(source, feed));
        return new CardOptions(globalSeconds, feedSeconds, hidden, series, copy, hiddenPackages);
    }

    public Map<String, Object> backupValues() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schema_version", 3);
        result.put("global_seconds", globalSeconds);
        StringBuilder timers = new StringBuilder();
        for (Map.Entry<String, Integer> entry : feedSeconds.entrySet()) append(timers, entry.getKey() + "\t" + entry.getValue());
        result.put("feed_seconds", timers.toString());
        result.put("hidden_items", encodeHidden(hidden));
        result.put("hidden_series", encodeHidden(series));
        StringBuilder order = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : itemOrder.entrySet()) {
            StringBuilder line = new StringBuilder(entry.getKey());
            for (String key : entry.getValue()) line.append('\t').append(key);
            append(order, line.toString());
        }
        result.put("item_order", order.toString());
        result.put("hidden_packages", encodePackages(hiddenPackages));
        return result;
    }

    public static CardOptions fromBackup(Map<String, ?> values) {
        boolean legacy = values != null && values.size() == 3 && !values.containsKey("schema_version");
        boolean version2 = values != null && Integer.valueOf(2).equals(values.get("schema_version"))
                && (values.size() == 6 || values.size() == 7 && values.containsKey("hidden_packages"));
        boolean version3 = values != null && Integer.valueOf(3).equals(values.get("schema_version")) && values.size() == 7;
        if (values == null || (!legacy && !version2 && !version3)
                || !(values.get("global_seconds") instanceof Integer) || !(values.get("feed_seconds") instanceof String)
                || !(values.get("hidden_items") instanceof String) || (!legacy && (!(values.get("hidden_series") instanceof String)
                || !(values.get("item_order") instanceof String)))
                || (values.containsKey("hidden_packages") && !(values.get("hidden_packages") instanceof String))
                || (version3 && !values.containsKey("hidden_packages"))) throw new IllegalArgumentException("Invalid card settings.");
        CardOptions result = defaults().withGlobalSeconds((Integer) values.get("global_seconds"));
        String timers = bounded(values, "feed_seconds", 60000);
        if (!timers.isEmpty()) for (String line : timers.split("\n", -1)) {
            String[] parts = line.split("\t", -1);
            if (parts.length != 2 || result.feedSeconds.containsKey(parts[0]) || !parts[1].matches("0|[1-9][0-9]{0,4}"))
                throw new IllegalArgumentException("Invalid channel timer.");
            result = result.withFeedSeconds(parts[0], Integer.parseInt(parts[1]));
        }
        result = decodeHidden(result, bounded(values, "hidden_items", 1500000), false);
        if (!legacy) {
            result = decodeHidden(result, bounded(values, "hidden_series", 1500000), true);
            String orders = bounded(values, "item_order", 1000000);
            Set<String> seen = new LinkedHashSet<>();
            if (!orders.isEmpty()) for (String line : orders.split("\n", -1)) {
                String[] parts = line.split("\t", -1);
                if (parts.length < 2 || !seen.add(scopedKey(parts[0], parts[1]))) throw new IllegalArgumentException("Invalid title order.");
                List<String> keys = new ArrayList<>();
                for (int i = 2; i < parts.length; i++) keys.add(parts[i]);
                result = result.withItemOrder(parts[0], parts[1], keys);
            }
        }
        return new CardOptions(result.globalSeconds, result.feedSeconds, result.hidden, result.series,
                result.itemOrder, decodePackages(values.containsKey("hidden_packages") ? (String) values.get("hidden_packages") : ""));
    }

    public static Set<String> decodePackages(String value) {
        if (value == null || value.length() > 411648) throw new IllegalArgumentException("Invalid hidden card packages.");
        Set<String> packages = new LinkedHashSet<>();
        if (!value.isEmpty()) for (String source : value.split("\n", -1)) {
            validatePackage(source);
            if (!packages.add(source) || packages.size() > 2048) throw new IllegalArgumentException("Invalid hidden card packages.");
        }
        return Collections.unmodifiableSet(packages);
    }

    public static String encodePackages(Set<String> packages) {
        if (packages == null || packages.size() > 2048) throw new IllegalArgumentException("Invalid hidden card packages.");
        StringBuilder result = new StringBuilder();
        for (String source : packages) { validatePackage(source); append(result, source); }
        return result.toString();
    }

    public static void validatePackage(String source) {
        if (source == null || !source.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+") || source.length() > 200)
            throw new IllegalArgumentException("Invalid card source.");
    }

    private static String bounded(Map<String, ?> values, String key, int max) {
        String result = (String) values.get(key);
        if (result.length() > max) throw new IllegalArgumentException("Card settings are too large.");
        return result;
    }

    private static CardOptions decodeHidden(CardOptions result, String text, boolean isSeries) {
        if (!text.isEmpty()) for (String line : text.split("\n", -1)) {
            String[] parts = line.split("\t", -1);
            if (parts.length != 3 || (isSeries ? result.isSeriesHidden(parts[0], parts[1]) : result.isHidden(parts[0], parts[1])))
                throw new IllegalArgumentException("Invalid hidden entry.");
            String title = new String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8);
            result = isSeries ? result.hideSeries(parts[0], parts[1], title) : result.hide(parts[0], parts[1], title);
        }
        return result;
    }

    private static String encodeHidden(Map<String, HiddenItem> hidden) {
        StringBuilder result = new StringBuilder();
        for (HiddenItem item : hidden.values()) append(result, item.source + "\t" + item.key + "\t"
                + Base64.getEncoder().encodeToString(item.title.getBytes(StandardCharsets.UTF_8)));
        return result.toString();
    }

    private static void append(StringBuilder text, String line) { if (text.length() > 0) text.append('\n'); text.append(line); }

    private static HiddenItem entry(String source, String key, String title) {
        scopedKey(source, key);
        if (title == null || title.length() > 512) throw new IllegalArgumentException("Invalid item title.");
        return new HiddenItem(source, key, title);
    }

    private static List<String> validatedOrder(List<String> values) {
        if (values == null || values.size() > 512) throw new IllegalArgumentException("Invalid title order.");
        Set<String> seen = new LinkedHashSet<>();
        for (String value : values) { identity(value); if (!seen.add(value)) throw new IllegalArgumentException("Duplicate title in order."); }
        return new ArrayList<>(seen);
    }

    public static String timerLabel(int seconds) { return seconds == 0 ? "Manual only" : "Every " + seconds + " seconds"; }

    private static void validateSeconds(int seconds) {
        if (seconds < 0 || seconds > MAX_SECONDS) throw new IllegalArgumentException("Choose Manual or whole seconds from 1 to 86400.");
    }

    private static String scopedKey(String source, String key) {
        validatePackage(source);
        identity(key);
        return source + "\t" + key;
    }

    private static void identity(String value) {
        if (value == null || value.length() > 200 || !value.matches("[A-Za-z0-9.:_/-]+"))
            throw new IllegalArgumentException("This card has no stable identity.");
    }
}
