package com.jeremykenedy.firetv.ui.nativeapp.layout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LayoutOptions {
    public static final int MAX_ITEMS = 512;
    public enum Mode { BOTH, CARDS_ONLY, WIDGETS_ONLY, NONE }
    public final Mode mode;
    public final int cardWidthDp;
    public final int cardHeightDp;
    public final List<String> order;
    public final Set<String> hidden;

    public LayoutOptions(int widthDp, int heightDp, Collection<String> order, Collection<String> hidden) {
        this(widthDp, heightDp, order, hidden, Mode.BOTH);
    }

    public LayoutOptions(int widthDp, int heightDp, Collection<String> order, Collection<String> hidden, Mode mode) {
        if (mode == null) throw new IllegalArgumentException("Invalid Home content mode.");
        this.mode = mode;
        if (widthDp < 64 || widthDp > 1024 || heightDp < 48 || heightDp > 1024)
            throw new IllegalArgumentException("Card dimensions are outside the supported range.");
        cardWidthDp = widthDp;
        cardHeightDp = heightDp;
        this.order = Collections.unmodifiableList(new ArrayList<>(keys(order)));
        this.hidden = Collections.unmodifiableSet(keys(hidden));
    }

    public static LayoutOptions defaults(int currentCardWidthDp, int currentCardHeightDp) {
        return new LayoutOptions(currentCardWidthDp, currentCardHeightDp,
                Collections.<String>emptyList(), Collections.<String>emptySet());
    }

    /** Keep missing producers in their saved positions and append newly discovered items. */
    public LayoutOptions discover(Collection<LayoutItem> available) {
        LinkedHashSet<String> next = new LinkedHashSet<>(order);
        for (LayoutItem item : available) {
            if (next.size() < MAX_ITEMS) next.add(item.key());
        }
        return new LayoutOptions(cardWidthDp, cardHeightDp, next, hidden, mode);
    }

    public List<LayoutItem> visible(Collection<LayoutItem> available) {
        Map<String, LayoutItem> pending = new LinkedHashMap<>();
        for (LayoutItem item : available) {
            if (pending.put(item.key(), item) != null) throw new IllegalArgumentException("Duplicate layout item.");
        }
        if (pending.size() > MAX_ITEMS) throw new IllegalArgumentException("Too many layout items.");
        List<LayoutItem> result = new ArrayList<>();
        for (String key : order) {
            LayoutItem item = pending.remove(key);
            if (item != null && isVisible(item)) result.add(item);
        }
        int remaining = MAX_ITEMS - order.size();
        for (LayoutItem item : pending.values()) {
            if (remaining-- <= 0) break;
            if (isVisible(item)) result.add(item);
        }
        return Collections.unmodifiableList(result);
    }

    public boolean includes(LayoutItem.Kind kind) {
        return mode == Mode.BOTH || (mode == Mode.CARDS_ONLY && kind == LayoutItem.Kind.CHANNEL_CARD)
                || (mode == Mode.WIDGETS_ONLY && kind == LayoutItem.Kind.WIDGET);
    }

    private boolean isVisible(LayoutItem item) {
        return !hidden.contains(item.key()) && includes(item.kind);
    }

    public LayoutOptions withMode(Mode value) {
        return new LayoutOptions(cardWidthDp, cardHeightDp, order, hidden, value);
    }

    /** Reorder relative to another visible item while retaining unavailable entries. */
    public LayoutOptions moveBefore(String key, String beforeKey) {
        if (!order.contains(key) || (beforeKey != null && !order.contains(beforeKey)))
            throw new IllegalArgumentException("Discover items before changing their order.");
        if (key.equals(beforeKey)) return this;
        List<String> next = new ArrayList<>(order);
        next.remove(key);
        next.add(beforeKey == null ? next.size() : next.indexOf(beforeKey), key);
        return new LayoutOptions(cardWidthDp, cardHeightDp, next, hidden, mode);
    }

    public LayoutOptions setVisible(String key, boolean visible) {
        if (!LayoutItem.validKey(key)) throw new IllegalArgumentException("Invalid layout key.");
        Set<String> next = new LinkedHashSet<>(hidden);
        if (visible) next.remove(key); else next.add(key);
        return new LayoutOptions(cardWidthDp, cardHeightDp, order, next, mode);
    }

    public LayoutOptions resizeCards(int widthDp, int heightDp) {
        return new LayoutOptions(widthDp, heightDp, order, hidden, mode);
    }

    public int widthDp(LayoutItem item, int widgetWidthDp) {
        return item.kind == LayoutItem.Kind.CHANNEL_CARD ? cardWidthDp : widgetWidthDp;
    }

    public int heightDp(LayoutItem item, int widgetHeightDp) {
        return item.kind == LayoutItem.Kind.CHANNEL_CARD ? cardHeightDp : widgetHeightDp;
    }

    public Map<String, Object> backupValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("card_width_dp", cardWidthDp);
        values.put("card_height_dp", cardHeightDp);
        values.put("order", join(order));
        values.put("hidden", join(hidden));
        values.put("mode", mode.name());
        return Collections.unmodifiableMap(values);
    }

    public static LayoutOptions fromBackup(Map<String, ?> values) {
        if (values == null || (values.size() != 4 && values.size() != 5)
                || (values.size() == 5 && !values.containsKey("mode"))
                || !(values.get("card_width_dp") instanceof Integer)
                || !(values.get("card_height_dp") instanceof Integer)
                || !(values.get("order") instanceof String) || !(values.get("hidden") instanceof String))
            throw new IllegalArgumentException("Invalid saved layout values.");
        return new LayoutOptions((Integer) values.get("card_width_dp"), (Integer) values.get("card_height_dp"),
                split((String) values.get("order")), split((String) values.get("hidden")), readMode(values));
    }

    private static Mode readMode(Map<String, ?> values) {
        if (!values.containsKey("mode")) return Mode.BOTH;
        if (!(values.get("mode") instanceof String)) throw new IllegalArgumentException("Invalid Home content mode.");
        try { return Mode.valueOf((String) values.get("mode")); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid Home content mode."); }
    }

    private static LinkedHashSet<String> keys(Collection<String> values) {
        if (values == null || values.size() > MAX_ITEMS) throw new IllegalArgumentException("Too many layout items.");
        LinkedHashSet<String> checked = new LinkedHashSet<>();
        for (String key : values) {
            if (!LayoutItem.validKey(key) || !checked.add(key)) throw new IllegalArgumentException("Invalid or duplicate layout key.");
        }
        return checked;
    }

    private static String join(Collection<String> keys) {
        StringBuilder value = new StringBuilder();
        for (String key : keys) {
            if (value.length() > 0) value.append('\n');
            value.append(key);
        }
        return value.toString();
    }

    private static Collection<String> split(String value) {
        if (value.length() > MAX_ITEMS * 209) throw new IllegalArgumentException("Saved layout is too large.");
        if (value.isEmpty()) return Collections.emptyList();
        return java.util.Arrays.asList(value.split("\n", -1));
    }
}
