package com.jeremykenedy.firetv.ui.nativeapp.widget.reminders;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public final class RemindersOptions {
    public static final int MAX_ITEMS = 64;
    public static final int MAX_LABEL = 128;
    public enum Repeat { ONCE, DAILY, WEEKLY }
    public final boolean enabled;
    public final boolean summaryOnly;
    public final boolean allowExport;
    public final List<Item> items;

    public static final class Item {
        public final String id;
        public final String label;
        public final LocalDate date;
        public final LocalTime time;
        public final ZoneId zone;
        public final Repeat repeat;
        public final boolean completed;
        public final long snoozeUntil;
        public Item(String id, String label, LocalDate date, LocalTime time, String zone, Repeat repeat, boolean completed, long snoozeUntil) {
            if (id == null || !id.matches("[a-zA-Z0-9-]{1,64}")) throw invalid("Invalid reminder identity.");
            if (label == null || label.trim().isEmpty() || label.length() > MAX_LABEL) throw invalid("Enter 1 to 128 label characters.");
            for (int i = 0; i < label.length(); i++) {
                char c = label.charAt(i);
                if (Character.isISOControl(c)) throw invalid("Use a single-line reminder label.");
                if (Character.isHighSurrogate(c)) {
                    if (i + 1 >= label.length() || !Character.isLowSurrogate(label.charAt(i + 1))) throw invalid("Invalid label text.");
                    i++;
                } else if (Character.isLowSurrogate(c)) throw invalid("Invalid label text.");
            }
            if (date == null || date.getYear() < 2020 || date.getYear() > 2100 || time == null || time.getSecond() != 0 || time.getNano() != 0)
                throw invalid("Use a date from 2020 through 2100 and a time in whole minutes.");
            if (zone == null || !ZoneId.getAvailableZoneIds().contains(zone)) throw invalid("Choose an IANA time zone, such as America/Los_Angeles.");
            if (repeat == null || completed && repeat != Repeat.ONCE) throw invalid("Invalid reminder recurrence.");
            if (snoozeUntil < 0 || snoozeUntil > Instant.parse("2101-01-01T00:00:00Z").toEpochMilli() || completed && snoozeUntil != 0)
                throw invalid("Invalid snooze time.");
            this.id = id; this.label = label; this.date = date; this.time = time; this.zone = ZoneId.of(zone);
            this.repeat = repeat; this.completed = completed; this.snoozeUntil = snoozeUntil;
        }
        public long scheduledAt() { return date.atTime(time).atZone(zone).toInstant().toEpochMilli(); }
        public long dueAt() { return Math.max(scheduledAt(), snoozeUntil); }
        public boolean due(long now) { return !completed && dueAt() <= now; }
        public Item complete(long now) {
            if (completed) return this;
            if (repeat == Repeat.ONCE) return new Item(id, label, date, time, zone.getId(), repeat, true, 0);
            int days = repeat == Repeat.DAILY ? 1 : 7;
            LocalDate next = date.plusDays(days);
            LocalDate current = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
            if (next.isBefore(current)) {
                long elapsed = java.time.temporal.ChronoUnit.DAYS.between(next, current);
                next = next.plusDays((elapsed / days) * days);
            }
            while (next.atTime(time).atZone(zone).toInstant().toEpochMilli() <= now) next = next.plusDays(days);
            return new Item(id, label, next, time, zone.getId(), repeat, false, 0);
        }
        public Item snooze(long now) {
            if (completed) throw invalid("This reminder is completed. Edit it to schedule it again.");
            if (!due(now)) throw invalid("Only due reminders can be snoozed.");
            return new Item(id, label, date, time, zone.getId(), repeat, false, Math.addExact(now, 3600000L));
        }
        public String status(long now) {
            if (completed) return "Completed";
            String dateTime = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm z").format(Instant.ofEpochMilli(dueAt()).atZone(zone));
            return (due(now) ? "Due · " : snoozeUntil > scheduledAt() ? "Snoozed until " : "Next · ") + dateTime;
        }
    }
    public RemindersOptions(boolean enabled, boolean summaryOnly, boolean allowExport, List<Item> items) {
        if (items == null || items.size() > MAX_ITEMS) throw invalid("Keep at most 64 reminders.");
        HashSet<String> ids = new HashSet<>();
        for (Item item : items) if (item == null || !ids.add(item.id)) throw invalid("Duplicate reminder identity.");
        this.enabled = enabled; this.summaryOnly = summaryOnly; this.allowExport = allowExport;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
    }
    public static RemindersOptions defaults() { return new RemindersOptions(false, true, false, Collections.<Item>emptyList()); }
    public RemindersOptions settings(boolean enabled, boolean summaryOnly, boolean allowExport) { return new RemindersOptions(enabled, summaryOnly, allowExport, items); }
    public Item find(String id) { for (Item item : items) if (item.id.equals(id)) return item; return null; }
    public RemindersOptions add(String label, LocalDate date, LocalTime time, String zone, Repeat repeat) {
        List<Item> next = new ArrayList<>(items); next.add(new Item(UUID.randomUUID().toString(), label, date, time, zone, repeat, false, 0));
        return new RemindersOptions(enabled, summaryOnly, allowExport, next);
    }
    public RemindersOptions edit(String id, String label, LocalDate date, LocalTime time, String zone, Repeat repeat) {
        return replace(new Item(require(id).id, label, date, time, zone, repeat, false, 0));
    }
    public RemindersOptions complete(String id, long now) { return replace(require(id).complete(now)); }
    public RemindersOptions snooze(String id, long now) { return replace(require(id).snooze(now)); }
    private RemindersOptions replace(Item item) {
        List<Item> next = new ArrayList<>(items); next.set(index(item.id), item);
        return new RemindersOptions(enabled, summaryOnly, allowExport, next);
    }
    public RemindersOptions remove(String id) {
        List<Item> next = new ArrayList<>(items); next.remove(index(id)); return new RemindersOptions(enabled, summaryOnly, allowExport, next);
    }
    private Item require(String id) { Item item = find(id); if (item == null) throw invalid("This reminder was removed."); return item; }
    private int index(String id) { require(id); for (int i = 0; i < items.size(); i++) if (items.get(i).id.equals(id)) return i; throw invalid("Missing reminder."); }
    public int dueCount(long now) { int count = 0; for (Item item : items) if (item.due(now)) count++; return count; }
    public String homeText(long now) {
        if (!enabled) return "Reminders disabled. Open to enable local reminders.";
        if (items.isEmpty()) return "No reminders yet. Open to add one.";
        int complete = 0; Item next = null;
        for (Item item : items) {
            if (item.completed) complete++;
            else if (next == null || item.dueAt() < next.dueAt()) next = item;
        }
        String summary = dueCount(now) + " due · " + (items.size() - complete) + " active · " + complete + " completed";
        if (summaryOnly) return summary + "\nDetails hidden on Home";
        if (next == null) return summary;
        return summary + "\n" + next.label + "\n" + next.status(now);
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
