package com.jeremykenedy.firetv.ui.nativeapp.widget.notes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public final class NotesOptions {
    public static final int MAX_ITEMS = 64;
    public static final int MAX_TEXT = 512;
    public final boolean configured;
    public final boolean summaryOnly;
    public final boolean allowExport;
    public final List<Item> items;

    public static final class Item {
        public final String id;
        public final String text;
        public final boolean checklist;
        public final boolean checked;
        public Item(String id, String text, boolean checklist, boolean checked) {
            if (id == null || !id.matches("[a-zA-Z0-9-]{1,64}")) throw new IllegalArgumentException("Invalid note identity.");
            if (text == null || text.trim().isEmpty() || text.length() > MAX_TEXT)
                throw new IllegalArgumentException("Enter 1 to 512 characters.");
            if (!checklist && checked) throw new IllegalArgumentException("Only checklist items can be checked.");
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (Character.isISOControl(c) && c != '\n' && c != '\t') throw new IllegalArgumentException("Invalid note text.");
                if (Character.isHighSurrogate(c)) {
                    if (i + 1 == text.length() || !Character.isLowSurrogate(text.charAt(i + 1)))
                        throw new IllegalArgumentException("Invalid note text.");
                    i++;
                } else if (Character.isLowSurrogate(c)) throw new IllegalArgumentException("Invalid note text.");
            }
            this.id = id; this.text = text; this.checklist = checklist; this.checked = checked;
        }
    }
    public NotesOptions(boolean configured, boolean summaryOnly, boolean allowExport, List<Item> items) {
        if (items == null || items.size() > MAX_ITEMS) throw new IllegalArgumentException("Keep at most 64 notes and list items.");
        HashSet<String> ids = new HashSet<>();
        for (Item item : items) if (item == null || !ids.add(item.id)) throw new IllegalArgumentException("Duplicate note identity.");
        if (!configured && !items.isEmpty()) throw new IllegalArgumentException("Notes need setup.");
        this.configured = configured; this.summaryOnly = summaryOnly; this.allowExport = allowExport;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
    }
    public static NotesOptions defaults() { return new NotesOptions(false, true, false, Collections.<Item>emptyList()); }
    public NotesOptions privacy(boolean summary, boolean export) { return new NotesOptions(true, summary, export, items); }
    public Item find(String id) { for (Item item : items) if (item.id.equals(id)) return item; return null; }
    public NotesOptions add(String text, boolean checklist) {
        List<Item> next = new ArrayList<>(items);
        next.add(new Item(UUID.randomUUID().toString(), text, checklist, false));
        return new NotesOptions(true, summaryOnly, allowExport, next);
    }
    public NotesOptions edit(String id, String text) {
        Item item = require(id); return replace(new Item(id, text, item.checklist, item.checked));
    }
    public NotesOptions toggle(String id) {
        Item item = require(id);
        if (!item.checklist) throw new IllegalArgumentException("This is a note, not a checklist item.");
        return replace(new Item(id, item.text, true, !item.checked));
    }
    private NotesOptions replace(Item value) {
        List<Item> next = new ArrayList<>(items); next.set(index(value.id), value);
        return new NotesOptions(true, summaryOnly, allowExport, next);
    }
    public NotesOptions remove(String id) {
        List<Item> next = new ArrayList<>(items); next.remove(index(id));
        return new NotesOptions(true, summaryOnly, allowExport, next);
    }
    public NotesOptions move(String id, int direction) {
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("Invalid move.");
        List<Item> next = new ArrayList<>(items); int at = index(id);
        int to = Math.max(0, Math.min(next.size() - 1, at + direction));
        Collections.swap(next, at, to); return new NotesOptions(true, summaryOnly, allowExport, next);
    }
    private int index(String id) { require(id); for (int i = 0; i < items.size(); i++) if (items.get(i).id.equals(id)) return i; throw new IllegalArgumentException(); }
    private Item require(String id) { Item item = find(id); if (item == null) throw new IllegalArgumentException("This item was removed."); return item; }
    public String summary() {
        int notes = 0, open = 0, done = 0;
        for (Item item : items) { if (!item.checklist) notes++; else if (item.checked) done++; else open++; }
        return notes + " notes · " + open + " unchecked · " + done + " checked";
    }
    public String homeText() {
        if (!configured) return "Set up local notes and lists";
        if (items.isEmpty()) return "No notes or list items yet";
        if (summaryOnly) return summary() + "\nDetails hidden on Home";
        StringBuilder text = new StringBuilder(summary());
        for (int i = 0; i < Math.min(3, items.size()); i++) {
            Item item = items.get(i); String line = item.text.replace('\n', ' ');
            int end = Math.min(80, line.length());
            if (end < line.length() && Character.isHighSurrogate(line.charAt(end - 1))) end--;
            text.append('\n').append(item.checklist ? (item.checked ? "[x] " : "[ ] ") : "")
                    .append(line.substring(0, end)).append(end < line.length() ? "…" : "");
        }
        return text.toString();
    }
}
