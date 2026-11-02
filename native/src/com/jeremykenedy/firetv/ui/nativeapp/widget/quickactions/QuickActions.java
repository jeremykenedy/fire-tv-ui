package com.jeremykenedy.firetv.ui.nativeapp.widget.quickactions;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** User-selected app opens and HTTPS content links, never arbitrary Android intents. */
public final class QuickActions {
    public static final int MAX_ACTIONS = 12;
    public static final int MAX_BYTES = 65536;
    public final List<Entry> entries;

    public static final class Entry {
        public final String id;
        public final String label;
        public final String component;
        public final String url;

        public Entry(String id, String label, String component, String url) {
            if (id == null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
                throw new IllegalArgumentException("Invalid action identity.");
            if (label == null || label.trim().isEmpty() || label.length() > 40)
                throw new IllegalArgumentException("Enter a name of 1 through 40 characters.");
            for (int i = 0; i < label.length(); i++) if (Character.isISOControl(label.charAt(i)))
                throw new IllegalArgumentException("Names cannot contain control characters.");
            if (component == null || component.length() > 512 || !component.matches("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+"))
                throw new IllegalArgumentException("Choose an installed TV app.");
            if (url == null) throw new IllegalArgumentException("Invalid content link.");
            if (!url.isEmpty()) validateUrl(url);
            this.id = id; this.label = label.trim(); this.component = component; this.url = url;
        }

        public static Entry create(String label, String component, String url) {
            return new Entry(UUID.randomUUID().toString(), label, component, url);
        }

        public Entry edit(String label, String url) { return new Entry(id, label, component, url); }
    }

    public QuickActions(List<Entry> entries) {
        if (entries == null || entries.size() > MAX_ACTIONS) throw new IllegalArgumentException("Save up to 12 quick actions.");
        Set<String> ids = new HashSet<>();
        for (Entry entry : entries) if (entry == null || !ids.add(entry.id)) throw new IllegalArgumentException("Duplicate action identity.");
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public static QuickActions empty() { return new QuickActions(Collections.<Entry>emptyList()); }

    public QuickActions put(Entry entry, boolean add) {
        List<Entry> next = new ArrayList<>(entries);
        int index = index(entry.id);
        if (add && index == -1) next.add(entry);
        else if (!add && index >= 0) next.set(index, entry);
        else throw new IllegalArgumentException("This action changed. Reopen its settings.");
        return new QuickActions(next);
    }

    public QuickActions remove(String id) {
        List<Entry> next = new ArrayList<>(entries); next.remove(requiredIndex(id)); return new QuickActions(next);
    }

    public QuickActions move(String id, int delta) {
        int index = requiredIndex(id), destination = index + delta;
        if ((delta != -1 && delta != 1) || destination < 0 || destination >= entries.size()) return this;
        List<Entry> next = new ArrayList<>(entries); next.add(destination, next.remove(index)); return new QuickActions(next);
    }

    private int index(String id) { for (int i = 0; i < entries.size(); i++) if (entries.get(i).id.equals(id)) return i; return -1; }
    private int requiredIndex(String id) {
        int index = index(id); if (index < 0) throw new IllegalArgumentException("This action no longer exists."); return index;
    }

    public String encode() {
        StringBuilder out = new StringBuilder("v1");
        for (Entry entry : entries) out.append('\n').append(entry.id).append('\t').append(entry.label)
                .append('\t').append(entry.component).append('\t').append(entry.url);
        return out.toString();
    }

    public static QuickActions decode(String saved) {
        if (saved == null || saved.length() > MAX_BYTES) throw new IllegalArgumentException("Invalid saved quick actions.");
        String[] rows = saved.split("\n", -1);
        if (!"v1".equals(rows[0]) || rows.length > MAX_ACTIONS + 1) throw new IllegalArgumentException("Invalid saved quick actions.");
        List<Entry> entries = new ArrayList<>();
        for (int i = 1; i < rows.length; i++) {
            String[] fields = rows[i].split("\t", -1);
            if (fields.length != 4) throw new IllegalArgumentException("Invalid saved quick actions.");
            entries.add(new Entry(fields[0], fields[1], fields[2], fields[3]));
        }
        return new QuickActions(entries);
    }

    public static void validateUrl(String value) {
        if (value.length() > 2048) throw new IllegalArgumentException("Use a content link of at most 2048 characters.");
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isEmpty()
                    || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                    || uri.getPort() < -1 || uri.getPort() > 65535)
                throw new IllegalArgumentException("Only HTTPS content links without credentials or fragments are supported.");
        } catch (URISyntaxException invalid) { throw new IllegalArgumentException("Enter a valid HTTPS content link."); }
    }
}
