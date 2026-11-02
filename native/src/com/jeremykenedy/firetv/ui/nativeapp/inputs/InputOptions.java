package com.jeremykenedy.firetv.ui.nativeapp.inputs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class InputOptions {
    public static final int MAX_INPUTS = 256;
    public final List<String> order;
    public final Set<String> hidden;

    public InputOptions(List<String> order, Set<String> hidden) {
        this.order = Collections.unmodifiableList(new ArrayList<>(validate(order)));
        this.hidden = Collections.unmodifiableSet(validate(hidden));
        Set<String> all = new LinkedHashSet<>(this.order);
        all.addAll(this.hidden);
        if (all.size() > MAX_INPUTS) throw new IllegalArgumentException("Too many saved TV inputs.");
    }

    private static LinkedHashSet<String> validate(Iterable<String> ids) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String id : ids) {
            if (!InputEntry.validId(id) || !result.add(id))
                throw new IllegalArgumentException("Invalid or duplicate TV input identity.");
            if (result.size() > MAX_INPUTS) throw new IllegalArgumentException("Too many saved TV inputs.");
        }
        return result;
    }

    public static List<String> parseOrder(String saved) {
        if (saved == null || saved.isEmpty()) return Collections.emptyList();
        List<String> ids = new ArrayList<>();
        Collections.addAll(ids, saved.split("\n", -1));
        return new ArrayList<>(validate(ids));
    }

    public String savedOrder() {
        StringBuilder result = new StringBuilder();
        for (String id : order) {
            if (result.length() > 0) result.append('\n');
            result.append(id);
        }
        return result.toString();
    }

    public List<String> arranged(List<String> available) {
        List<String> result = new ArrayList<>(order);
        for (String id : validate(available)) if (!result.contains(id)) result.add(id);
        return result;
    }

    public InputOptions hidden(String id, boolean hide) {
        if (!InputEntry.validId(id)) throw new IllegalArgumentException("Select a TV input.");
        Set<String> changed = new LinkedHashSet<>(hidden);
        if (hide) changed.add(id); else changed.remove(id);
        return new InputOptions(order, changed);
    }

    public InputOptions showAll() { return new InputOptions(order, Collections.<String>emptySet()); }

    public InputOptions move(String id, int delta, List<String> available) {
        if (delta != -1 && delta != 1) throw new IllegalArgumentException("Move one input at a time.");
        List<String> changed = arranged(available);
        int from = changed.indexOf(id);
        int to = from + delta;
        if (from < 0 || to < 0 || to >= changed.size()) return this;
        Collections.swap(changed, from, to);
        return new InputOptions(changed, hidden);
    }
}
