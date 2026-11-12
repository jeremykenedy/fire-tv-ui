package com.jeremykenedy.firetv.ui.nativeapp.widgetgroups;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only preparation. Publication belongs to the presentation journal. */
public final class WidgetGroupMigration {
    public static final String KEY = "widget_groups";
    private static final String DEFAULT_GROUPS = "[[],[],[],[]]";
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
        if (legacy == null || nativeLayout == null) throw new IllegalArgumentException("Missing widget settings.");
        Object enabled = legacy.get("enable_widgets");
        Object hide = legacy.get("hide_watch_next");
        if (legacy.containsKey("enable_widgets") && !(enabled instanceof Boolean)) throw invalid();
        if (legacy.containsKey("hide_watch_next") && !(hide instanceof Boolean)) throw invalid();
        boolean widgets = Boolean.TRUE.equals(enabled);
        boolean rowHidden = Boolean.TRUE.equals(hide);
        Map<String, Object> source = new LinkedHashMap<>(legacy);
        if (!source.containsKey("widgets_config")) {
            if (!widgets) return Collections.emptyMap();
            source.put("widgets_config", DEFAULT_GROUPS);
        } else if ("".equals(source.get("widgets_config"))) {
            source.put("widgets_config", DEFAULT_GROUPS);
        }
        Map<String, Object> patch = new LinkedHashMap<>(patch(source, nativeLayout));
        WidgetGroups groups = WidgetGroups.decode((String) patch.get(KEY));
        if (widgets && groups.groups.size() > 4) throw invalid();
        for (java.util.List<WidgetGroups.Entry> group : groups.groups)
            for (WidgetGroups.Entry entry : group) {
                if (!entry.hasOnlyKnownFields()) throw invalid();
                if (entry.type.equals("Weather")) {
                    if (entry.data.size() != 2 || !entry.data.containsKey("location") || !entry.data.containsKey("unit")) throw invalid();
                    com.jeremykenedy.firetv.ui.nativeapp.widget.weather.WeatherOptions options = WeatherGroupOptions.read(entry);
                    if (widgets && !options.label.isEmpty()) throw invalid();
                } else if (!(entry.type.equals("DigitalClock") || entry.type.equals("YearProgress")) || !entry.data.isEmpty()) throw invalid();
            }

        // The original hidden row suppresses widgets too. Enabling widgets selects
        // the grouped layout in place of the ordinary featured-card row.
        String mode = rowHidden ? "NONE" : widgets ? "WIDGETS_ONLY" : "CARDS_ONLY";
        if (nativeLayout.containsKey("mode") && !mode.equals(nativeLayout.get("mode"))) throw invalid();
        patch.put("mode", mode);
        java.util.List<String> order = lines(nativeLayout.get("order"));
        java.util.List<String> hidden = lines(nativeLayout.get("hidden"));
        if (widgets && !rowHidden && hidden.contains("widget:grouped-widgets")) throw invalid();
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
