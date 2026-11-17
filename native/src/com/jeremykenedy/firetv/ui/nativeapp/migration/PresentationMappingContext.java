package com.jeremykenedy.firetv.ui.nativeapp.migration;

import java.util.*;

/** Verified identity relationships supplied by an inventory, never inferred from labels. */
public final class PresentationMappingContext {
    public static final class Feed {
        public final String source, nativeKey;
        public Feed(String source, String nativeKey) {
            if (source == null || !source.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+") || source.length() > 200
                    || nativeKey == null || !nativeKey.matches("tv:[a-f0-9]{64}")) throw invalid();
            this.source = source; this.nativeKey = nativeKey;
        }
    }
    public static final class Item {
        public final String source, nativeKey, title, aliasSource, aliasKey;
        public final boolean series;
        public Item(String source, String nativeKey, String title, boolean series) {
            this(source, nativeKey, title, series, null, null);
        }
        public Item(String source, String nativeKey, String title, boolean series, String aliasSource, String aliasKey) {
            if (source == null || !source.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+") || source.length() > 200
                    || nativeKey == null || nativeKey.length() > 200 || !nativeKey.matches("[A-Za-z0-9.:_/-]+")
                    || title == null || title.length() > 512) throw invalid();
            if ((aliasSource == null) != (aliasKey == null) || (aliasSource != null
                    && (series || aliasSource.length() > 200 || !aliasSource.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
                    || aliasKey.length() > 200 || !aliasKey.matches("[A-Za-z0-9.:_/-]+")
                    || (aliasSource.equals(source) && aliasKey.equals(nativeKey))))) throw invalid();
            this.source = source; this.nativeKey = nativeKey; this.title = title; this.series = series;
            this.aliasSource = aliasSource; this.aliasKey = aliasKey;
        }
    }
    public final Map<String, String> components, icons, inputs;
    public final Map<String, Feed> feeds;
    public final Map<String, Item> items;
    public final Set<String> advertisedIconPacks;
    public final Set<String> ambiguousInputs;
    public final Map<String, PrivateMediaReference> privateMedia;

    public PresentationMappingContext(Map<String, String> components, Map<String, Feed> feeds,
            Map<String, Item> items, Map<String, String> icons) {
        this(components, feeds, items, icons, Collections.<String, String>emptyMap());
    }
    public PresentationMappingContext(Map<String, String> components, Map<String, Feed> feeds,
            Map<String, Item> items, Map<String, String> icons, Map<String, String> inputs) {
        this(components, feeds, items, icons, inputs, Collections.<String>emptySet());
    }
    public PresentationMappingContext(Map<String, String> components, Map<String, Feed> feeds,
            Map<String, Item> items, Map<String, String> icons, Map<String, String> inputs, Set<String> advertisedIconPacks) {
        this(components, feeds, items, icons, inputs, advertisedIconPacks, Collections.<PrivateMediaReference>emptyList());
    }
    public PresentationMappingContext(Map<String, String> components, Map<String, Feed> feeds,
            Map<String, Item> items, Map<String, String> icons, Map<String, String> inputs, Set<String> advertisedIconPacks,
            Collection<PrivateMediaReference> privateMedia) {
        this(components, feeds, items, icons, inputs, advertisedIconPacks, privateMedia, Collections.<String>emptySet());
    }
    public PresentationMappingContext(Map<String, String> components, Map<String, Feed> feeds,
            Map<String, Item> items, Map<String, String> icons, Map<String, String> inputs, Set<String> advertisedIconPacks,
            Collection<PrivateMediaReference> privateMedia, Set<String> ambiguousInputs) {
        if (inputs == null || ambiguousInputs == null || ambiguousInputs.size() > 256) throw invalid();
        for (String alias : ambiguousInputs)
            if (!com.jeremykenedy.firetv.ui.nativeapp.appgrid.RetainedHomeChoices.inputAlias(alias) || inputs.containsKey(alias)) throw invalid();
        this.ambiguousInputs = Collections.unmodifiableSet(new LinkedHashSet<>(ambiguousInputs));
        if (privateMedia == null || privateMedia.size() > 512) throw invalid();
        Map<String, PrivateMediaReference> verifiedMedia = new LinkedHashMap<>();
        for (PrivateMediaReference reference : privateMedia) {
            if (reference == null || verifiedMedia.put(reference.uri, reference) != null) throw invalid();
        }
        this.privateMedia = Collections.unmodifiableMap(verifiedMedia);
        if (advertisedIconPacks == null || advertisedIconPacks.size() > 256) throw invalid();
        for (String id : advertisedIconPacks) {
            if (id == null || id.length() > 200 || !id.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")) throw invalid();
        }
        this.advertisedIconPacks = Collections.unmodifiableSet(new LinkedHashSet<>(advertisedIconPacks));
        if (inputs == null || inputs.size() > 256) throw invalid();
        Set<String> inputIds = new HashSet<>();
        for (Map.Entry<String, String> entry : inputs.entrySet()) {
            String id = entry.getValue();
            if (entry.getKey() == null || !entry.getKey().startsWith("tvinput_name:") || entry.getKey().length() <= 13
                    || entry.getKey().length() > 512 || id == null || id.isEmpty() || id.length() > 2042 || !inputIds.add(id)) throw invalid();
            for (int index = 0; index < id.length(); index++) if (Character.isISOControl(id.charAt(index))) throw invalid();
        }
        this.inputs = Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
        if (components == null || components.size() > 2048 || feeds == null || feeds.size() > 512
                || items == null || items.size() > 4096 || icons == null || icons.size() > 2048) throw invalid();
        Set<String> mappedFeeds = new HashSet<>();
        for (Map.Entry<String, String> entry : components.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isEmpty() || entry.getKey().length() > 512
                    || !validComponent(entry.getValue())) throw invalid();
        }
        for (Map.Entry<String, Feed> entry : feeds.entrySet()) {
            if (!hash(entry.getKey()) || entry.getValue() == null
                    || !mappedFeeds.add(entry.getValue().nativeKey)) throw invalid();
        }
        Set<String> mappedItems = new HashSet<>();
        for (Map.Entry<String, Item> entry : items.entrySet()) {
            Item item = entry.getValue();
            if (!hash(entry.getKey()) || item == null
                    || !mappedItems.add(item.source + "\t" + item.series + "\t" + item.nativeKey)) throw invalid();
            if (item.aliasSource != null && !mappedItems.add(item.aliasSource + "\tfalse\t" + item.aliasKey)) throw invalid();
        }
        for (Map.Entry<String, String> entry : icons.entrySet()) {
            if (entry.getKey() == null || entry.getKey().length() > 4096 || !validIcon(entry.getValue())) throw invalid();
        }
        Map<String, String> canonical = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : components.entrySet()) canonical.put(entry.getKey(), canonicalComponent(entry.getValue()));
        this.components = Collections.unmodifiableMap(canonical);
        this.feeds = Collections.unmodifiableMap(new LinkedHashMap<>(feeds));
        this.items = Collections.unmodifiableMap(new LinkedHashMap<>(items));
        this.icons = Collections.unmodifiableMap(new LinkedHashMap<>(icons));
    }
    public static PresentationMappingContext empty() {
        return new PresentationMappingContext(Collections.<String, String>emptyMap(), Collections.<String, Feed>emptyMap(),
                Collections.<String, Item>emptyMap(), Collections.<String, String>emptyMap());
    }
    String component(String key) {
        if (components.containsKey(key)) return components.get(key);
        if (validComponent(key)) return canonicalComponent(key);
        throw new IllegalArgumentException("A saved app has no unique verified launch activity. Its original settings were kept. Restore the app or resolve its activity inventory before upgrading.");
    }
    String availableComponent(String key) {
        if (components.containsKey(key) || validComponent(key)) return component(key);
        if (!com.jeremykenedy.firetv.ui.nativeapp.appgrid.RetainedHomeChoices.packageName(key)) return component(key);
        for (String value : components.values())
            if (value.startsWith(key + "/")) throw new IllegalArgumentException("A saved app has multiple launch activities. Its original choice was kept.");
        return null;
    }
    String availableInput(String alias) {
        if (!com.jeremykenedy.firetv.ui.nativeapp.appgrid.RetainedHomeChoices.inputAlias(alias)) throw invalid();
        if (ambiguousInputs.contains(alias)) throw new IllegalArgumentException("A saved input label matches multiple platform inputs. Its original choice was kept.");
        return inputs.get(alias);
    }
    String tile(String key) {
        return inputs.containsKey(key) ? "input:" + inputs.get(key) : component(key);
    }
    Feed feed(String hash) {
        Feed feed = feeds.get(hash);
        if (feed == null) throw new IllegalArgumentException("A legacy feed has no verified native identity mapping.");
        return feed;
    }
    Item item(String hash) {
        Item item = items.get(hash);
        if (item == null) throw new IllegalArgumentException("A legacy item has no verified native identity mapping.");
        return item;
    }
    static boolean hash(String value) { return value != null && value.matches("[a-f0-9]{64}"); }
    private static String canonicalComponent(String value) {
        int slash = value.indexOf('/');
        String packageName = value.substring(0, slash), className = value.substring(slash + 1);
        String canonical = packageName + "/" + (className.startsWith(".") ? packageName + className : className);
        if (canonical.length() > 512) throw invalid();
        return canonical;
    }
    static boolean validComponent(String value) {
        return value != null && value.length() <= 512 && value.matches("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+");
    }
    private static boolean validIcon(String value) {
        return value != null && (value.matches("image:[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}\\.png")
                || Arrays.asList("catalog:jbl", "catalog:sonos", "catalog:stereo-receiver", "catalog:laptop",
                        "catalog:pc", "catalog:lg", "catalog:samsung", "catalog:vcr").contains(value));
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid or ambiguous migration identity inventory."); }
}
