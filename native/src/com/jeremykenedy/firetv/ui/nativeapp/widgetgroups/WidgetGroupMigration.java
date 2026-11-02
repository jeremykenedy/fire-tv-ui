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
}
