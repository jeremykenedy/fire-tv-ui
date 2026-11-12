package com.jeremykenedy.firetv.ui.nativeapp.widgetgroups;

import com.jeremykenedy.firetv.ui.nativeapp.migration.LegacyJson;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Legacy group ordering and per-widget identities, including unknown widget records. */
public final class WidgetGroups {
    public static final int MAX_GROUPS = 128;
    public static final int MAX_WIDGETS = 256;
    public final List<List<Entry>> groups;

    public static final class Entry {
        public final String id, type, size;
        public final Map<String, Object> data;
        private final Map<String, Object> original;
        private Entry(Map<String, Object> values) {
            Object rawId = values.get("id"), rawType = values.get("type"), rawSize = values.get("size"), rawData = values.get("data");
            if (!(rawId instanceof String) || !((String) rawId).matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
                    || !(rawType instanceof String) || ((String) rawType).isEmpty() || ((String) rawType).length() > 128
                    || !(rawSize instanceof String) || !(((String) rawSize).equals("Big") || ((String) rawSize).equals("Small"))
                    || !(rawData instanceof Map)) throw invalid();
            id = (String) rawId; type = (String) rawType; size = (String) rawSize;
            original = immutableMap(values);
            @SuppressWarnings("unchecked") Map<String, Object> typed = (Map<String, Object>) original.get("data");
            data = typed;
        }
        public boolean isKnown() { return type.equals("DigitalClock") || type.equals("Weather") || type.equals("YearProgress"); }
        public boolean hasOnlyKnownFields() {
            return original.keySet().equals(new LinkedHashSet<String>(java.util.Arrays.asList("id", "type", "size", "data")));
        }
        public boolean isRenderable() {
            return (type.equals("DigitalClock") || type.equals("YearProgress")) && data.isEmpty()
                    && hasOnlyKnownFields();
        }
    }

    private WidgetGroups(List<List<Entry>> groups) {
        List<List<Entry>> copy = new ArrayList<>();
        for (List<Entry> group : groups) copy.add(Collections.unmodifiableList(new ArrayList<>(group)));
        this.groups = Collections.unmodifiableList(copy);
    }

    public static WidgetGroups decode(String json) {
        Map<String, Object> root = LegacyJson.object("{\"groups\":" + json + "}");
        if (root.size() != 1) throw invalid();
        Object value = root.get("groups");
        if (!(value instanceof List)) throw invalid();
        List<?> rawGroups = (List<?>) value;
        if (rawGroups.size() > MAX_GROUPS) throw invalid();
        List<List<Entry>> result = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        int count = 0;
        for (Object rawGroup : rawGroups) {
            if (!(rawGroup instanceof List)) throw invalid();
            List<?> rawEntries = (List<?>) rawGroup;
            if (rawEntries.size() > 2) throw invalid();
            List<Entry> entries = new ArrayList<>();
            for (Object rawEntry : rawEntries) {
                if (!(rawEntry instanceof Map) || ++count > MAX_WIDGETS) throw invalid();
                @SuppressWarnings("unchecked") Map<String, Object> values = (Map<String, Object>) rawEntry;
                Entry entry = new Entry(values);
                if (!ids.add(entry.id) || (rawEntries.size() > 1 && entry.size.equals("Big"))) throw invalid();
                entries.add(entry);
            }
            result.add(entries);
        }
        return new WidgetGroups(result);
    }

    public int displayColumnCount() { return Math.max(4, groups.size()); }

    public WidgetGroups withDisplayColumns() {
        if (groups.size() >= 4) return this;
        List<List<Entry>> copy = mutable();
        while (copy.size() < 4) copy.add(new ArrayList<Entry>());
        return new WidgetGroups(copy);
    }

    public WidgetGroups remove(String id) {
        List<List<Entry>> copy = mutable();
        for (List<Entry> group : copy) for (int i = 0; i < group.size(); i++) if (group.get(i).id.equals(id)) {
            group.remove(i); return new WidgetGroups(copy);
        }
        throw new IllegalArgumentException("Widget no longer exists.");
    }

    /** Updates only one record's data while retaining its identity and unknown fields. */
    public WidgetGroups updateData(String id, Map<String, Object> data) {
        if (data == null) throw invalid();
        List<List<Entry>> copy = mutable();
        for (List<Entry> group : copy) for (int i = 0; i < group.size(); i++) {
            Entry entry = group.get(i);
            if (entry.id.equals(id)) {
                Map<String, Object> changed = new LinkedHashMap<>(entry.original);
                changed.put("data", data);
                group.set(i, new Entry(changed));
                return decode(new WidgetGroups(copy).encode());
            }
        }
        throw new IllegalArgumentException("Widget no longer exists.");
    }

    public WidgetGroups moveGroup(int from, int to) {
        if (from < 0 || to < 0 || from >= groups.size() || to >= groups.size()) throw invalid();
        List<List<Entry>> copy = mutable();
        copy.add(to, copy.remove(from));
        return new WidgetGroups(copy);
    }

    /** Replaces a single slot or appends a second Small slot without discarding other records. */
    public WidgetGroups put(int groupIndex, int entryIndex, String recordJson) {
        if (groupIndex < 0 || groupIndex >= groups.size()) throw invalid();
        Entry replacement = new Entry(LegacyJson.object(recordJson));
        List<List<Entry>> copy = mutable();
        List<Entry> group = copy.get(groupIndex);
        if (entryIndex < 0 || entryIndex > group.size()) throw invalid();
        if (entryIndex == group.size()) group.add(replacement); else group.set(entryIndex, replacement);
        return decode(new WidgetGroups(copy).encode());
    }

    public WidgetGroups appendEmptyGroup() {
        if (groups.size() >= MAX_GROUPS) throw invalid();
        List<List<Entry>> copy = mutable(); copy.add(new ArrayList<Entry>());
        return new WidgetGroups(copy);
    }

    public WidgetGroups removeEmptyGroup(int index) {
        if (index < 0 || index >= groups.size() || !groups.get(index).isEmpty()) throw invalid();
        List<List<Entry>> copy = mutable(); copy.remove(index);
        return new WidgetGroups(copy);
    }

    public String encode() {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < groups.size(); i++) {
            if (i != 0) out.append(',');
            out.append('[');
            for (int j = 0; j < groups.get(i).size(); j++) {
                if (j != 0) out.append(',');
                json(out, groups.get(i).get(j).original);
            }
            out.append(']');
        }
        out.append(']');
        if (out.length() > 1024 * 1024 - 16) throw invalid();
        return out.toString();
    }

    private List<List<Entry>> mutable() {
        List<List<Entry>> copy = new ArrayList<>();
        for (List<Entry> group : groups) copy.add(new ArrayList<>(group));
        return copy;
    }

    private static Map<String, Object> immutableMap(Map<String, Object> input) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) out.put(entry.getKey(), immutable(entry.getValue()));
        return Collections.unmodifiableMap(out);
    }
    private static Object immutable(Object value) {
        if (value instanceof Map) {
            @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) value;
            return immutableMap(map);
        }
        if (value instanceof List) {
            List<Object> list = new ArrayList<>();
            for (Object item : (List<?>) value) list.add(immutable(item));
            return Collections.unmodifiableList(list);
        }
        return value;
    }
    private static void json(StringBuilder out, Object value) {
        if (value == null) { out.append("null"); return; }
        if (value instanceof Boolean || value instanceof BigDecimal || value instanceof Integer || value instanceof Long) { out.append(value); return; }
        if (value instanceof String) {
            out.append('"');
            String text = (String) value;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '"' || c == '\\') out.append('\\').append(c);
                else if (c < 32) out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                else out.append(c);
            }
            out.append('"'); return;
        }
        if (value instanceof Map) {
            out.append('{'); boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!first) out.append(','); first = false;
                json(out, entry.getKey()); out.append(':'); json(out, entry.getValue());
            }
            out.append('}'); return;
        }
        if (value instanceof List) {
            out.append('['); boolean first = true;
            for (Object item : (List<?>) value) { if (!first) out.append(','); first = false; json(out, item); }
            out.append(']'); return;
        }
        throw invalid();
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Unsupported saved widget groups. Original data was kept."); }
}
