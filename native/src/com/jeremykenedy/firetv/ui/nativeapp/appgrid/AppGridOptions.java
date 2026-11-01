package com.jeremykenedy.firetv.ui.nativeapp.appgrid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AppGridOptions {
    public final int columns;
    public final int rows;
    public final boolean horizontal;
    public final List<String> order;
    public final Set<String> hidden;
    public final Set<String> favorites;

    public AppGridOptions(int columns, int rows, boolean horizontal, List<String> order,
            Set<String> hidden, Set<String> favorites) {
        if (columns < 2 || columns > 12 || rows < 0 || rows > 8) throw new IllegalArgumentException("Grid size is outside supported bounds.");
        this.columns = columns;
        this.rows = rows;
        this.horizontal = horizontal;
        this.order = Collections.unmodifiableList(new ArrayList<>(valid(order)));
        this.hidden = Collections.unmodifiableSet(valid(hidden));
        this.favorites = Collections.unmodifiableSet(valid(favorites));
    }

    private static LinkedHashSet<String> valid(Iterable<String> keys) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String key : keys) {
            if (key == null || key.length() > 512 || !key.matches("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+") || !result.add(key)) {
                throw new IllegalArgumentException("Invalid or duplicate app identity.");
            }
        }
        if (result.size() > 2048) throw new IllegalArgumentException("Too many app identities.");
        return result;
    }

    public static AppGridOptions defaults() {
        return new AppGridOptions(5, 0, false, Collections.<String>emptyList(),
                Collections.<String>emptySet(), Collections.<String>emptySet());
    }

    public AppGridOptions discover(List<String> available) {
        List<String> all = new ArrayList<>(order);
        for (String key : available) if (!all.contains(key)) all.add(key);
        return copy(columns, rows, horizontal, all, hidden, favorites);
    }

    public List<String> visible(List<String> available) {
        List<String> result = new ArrayList<>();
        List<String> all = discover(available).order;
        for (boolean favorite : new boolean[] {true, false}) {
            for (String key : all) {
                if (available.contains(key) && !hidden.contains(key) && favorites.contains(key) == favorite) result.add(key);
            }
        }
        return result;
    }

    public AppGridOptions toggleHidden(String key) {
        Set<String> value = new LinkedHashSet<>(hidden);
        if (!value.remove(key)) value.add(key);
        return copy(columns, rows, horizontal, order, value, favorites);
    }

    public AppGridOptions toggleFavorite(String key) {
        Set<String> value = new LinkedHashSet<>(favorites);
        if (!value.remove(key)) value.add(key);
        return copy(columns, rows, horizontal, order, hidden, value);
    }

    public AppGridOptions move(String key, int delta, List<String> available) {
        List<String> visible = visible(available);
        int from = visible.indexOf(key), to = from + delta;
        if (from < 0 || to < 0 || to >= visible.size()) return this;
        String other = visible.get(to);
        if (favorites.contains(key) != favorites.contains(other)) return this;
        List<String> all = new ArrayList<>(discover(available).order);
        Collections.swap(all, all.indexOf(key), all.indexOf(other));
        return copy(columns, rows, horizontal, all, hidden, favorites);
    }

    public AppGridOptions grid(int columns, int rows, boolean horizontal) {
        return copy(columns, rows, horizontal, order, hidden, favorites);
    }

    private static AppGridOptions copy(int columns, int rows, boolean horizontal, List<String> order,
            Set<String> hidden, Set<String> favorites) {
        return new AppGridOptions(columns, rows, horizontal, order, hidden, favorites);
    }
}
