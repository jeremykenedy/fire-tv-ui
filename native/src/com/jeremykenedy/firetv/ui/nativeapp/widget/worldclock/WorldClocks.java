package com.jeremykenedy.firetv.ui.nativeapp.widget.worldclock;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/** Ordered, bounded local clock configuration. Unknown zones never silently become GMT. */
public final class WorldClocks {
    public static final int MAX_CLOCKS = 6;
    public static final int MAX_LABEL = 40;
    private static final Set<String> ZONES = new HashSet<>(Arrays.asList(TimeZone.getAvailableIDs()));
    public final List<Entry> entries;

    public static final class Entry {
        public final String zone;
        public final String label;
        public Entry(String zone, String label) {
            if (zone == null || !ZONES.contains(zone) || (!zone.contains("/") && !zone.equals("UTC")))
                throw new IllegalArgumentException("Choose a supported IANA time zone.");
            if (label == null || label.trim().isEmpty() || label.trim().length() > MAX_LABEL)
                throw new IllegalArgumentException("Enter a label of 1 to 40 characters.");
            for (int i = 0; i < label.length(); i++) if (Character.isISOControl(label.charAt(i)))
                throw new IllegalArgumentException("Labels cannot contain control characters.");
            this.zone = zone;
            this.label = label.trim();
        }
    }

    public WorldClocks(List<Entry> entries) {
        if (entries == null || entries.size() > MAX_CLOCKS)
            throw new IllegalArgumentException("Choose up to six clocks.");
        Set<String> seen = new HashSet<>();
        for (Entry entry : entries) if (entry == null || !seen.add(entry.zone))
            throw new IllegalArgumentException("That time zone is already listed.");
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public static WorldClocks empty() { return new WorldClocks(Collections.<Entry>emptyList()); }

    public WorldClocks add(Entry entry) {
        List<Entry> next = new ArrayList<>(entries);
        next.add(entry);
        return new WorldClocks(next);
    }

    public WorldClocks remove(int index) {
        List<Entry> next = new ArrayList<>(entries);
        next.remove(index);
        return new WorldClocks(next);
    }

    public WorldClocks replace(int index, Entry entry) {
        List<Entry> next = new ArrayList<>(entries);
        next.set(index, entry);
        return new WorldClocks(next);
    }

    public WorldClocks move(int index, int target) {
        List<Entry> next = new ArrayList<>(entries);
        if (target < 0 || target >= next.size()) throw new IllegalArgumentException("Invalid clock position.");
        next.add(target, next.remove(index));
        return new WorldClocks(next);
    }

    public String encode() {
        StringBuilder saved = new StringBuilder("v1");
        for (Entry entry : entries) saved.append('\n').append(entry.zone).append('\t').append(entry.label);
        return saved.toString();
    }

    public static WorldClocks decode(String saved) {
        if (saved == null || saved.length() > 2048) throw new IllegalArgumentException("Invalid saved clocks.");
        if (saved.equals("")) return empty();
        String[] rows = saved.split("\n", -1);
        if (!rows[0].equals("v1") || rows.length > MAX_CLOCKS + 1)
            throw new IllegalArgumentException("Invalid saved clocks.");
        List<Entry> entries = new ArrayList<>();
        for (int i = 1; i < rows.length; i++) {
            String[] fields = rows[i].split("\t", -1);
            if (fields.length != 2) throw new IllegalArgumentException("Invalid saved clocks.");
            entries.add(new Entry(fields[0], fields[1]));
        }
        return new WorldClocks(entries);
    }

    public static String format(Entry entry, long now, Locale locale, String pattern) {
        SimpleDateFormat formatter = new SimpleDateFormat(pattern, locale);
        formatter.setTimeZone(TimeZone.getTimeZone(entry.zone));
        return formatter.format(new Date(now));
    }
}
