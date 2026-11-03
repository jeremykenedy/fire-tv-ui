package com.jeremykenedy.firetv.ui.nativeapp.widgetgroups;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only preparation. Publication belongs to the presentation journal. */
public final class WidgetGroupMigration {
    public static final String KEY = "widget_groups";
    private WidgetGroupMigration() {}

    public static Map<String, Object> patch(Map<String, ?> legacy, Map<String, ?> nativeLayout) {
        if (legacy == null || nativeLayout == null) throw new IllegalArgumentException("Missing widget settings.");
        if (!legacy.containsKey("widgets_config")) return Collections.emptyMap();
        Object source = legacy.get("widgets_config");
        if (!(source instanceof String)) throw new IllegalArgumentException("Invalid saved widget groups.");
        WidgetGroups.decode((String) source);
        if (nativeLayout.containsKey(KEY) && !source.equals(nativeLayout.get(KEY)))
            throw new IllegalArgumentException("Existing widget groups conflict. Original settings were kept.");
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put(KEY, source);
        return Collections.unmodifiableMap(patch);
    }

    /** Accept only widget records with verified migration semantics. */
    public static Map<String, Object> supportedPatch(Map<String, ?> legacy, Map<String, ?> nativeLayout) {
        Map<String, Object> patch = new LinkedHashMap<>(patch(legacy, nativeLayout));
        if (patch.isEmpty()) return Collections.emptyMap();
        Object enabled = legacy.get("enable_widgets");
        if (!Boolean.FALSE.equals(enabled) || !(legacy.get("hide_watch_next") instanceof Boolean)) throw invalid();
        WidgetGroups groups = WidgetGroups.decode((String) patch.get(KEY));
        for (java.util.List<WidgetGroups.Entry> group : groups.groups)
            for (WidgetGroups.Entry entry : group) {
                if (!(entry.type.equals("DigitalClock") || entry.type.equals("YearProgress"))
                        || !entry.hasOnlyKnownFields() || !entry.data.isEmpty()) throw invalid();
            }

        java.util.List<String> order = lines(nativeLayout.get("order"));
        java.util.List<String> hidden = lines(nativeLayout.get("hidden"));
        // Discovery persists visible clocks in order, so their origin is ambiguous.
        if (order.contains("widget:clock") && !hidden.contains("widget:clock")) throw invalid();
        if (!hidden.contains("widget:clock")) {
            hidden.add("widget:clock");
            patch.put("hidden", String.join("\n", hidden));
        }
        new com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutOptions(320, 180, order, hidden);
        return Collections.unmodifiableMap(patch);
    }
    private static java.util.List<String> lines(Object value) {
        if (value == null) return new java.util.ArrayList<>();
        if (!(value instanceof String)) throw invalid();
        return new java.util.ArrayList<>(((String) value).isEmpty() ? Collections.<String>emptyList()
                : java.util.Arrays.asList(((String) value).split("\n", -1)));
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Saved widgets need further migration support. Original settings were kept.");
    }
}
