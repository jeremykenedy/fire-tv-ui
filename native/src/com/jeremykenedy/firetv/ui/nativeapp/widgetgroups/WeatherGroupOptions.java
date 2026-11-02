package com.jeremykenedy.firetv.ui.nativeapp.widgetgroups;

import com.jeremykenedy.firetv.ui.nativeapp.widget.weather.WeatherOptions;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Per-record weather settings; never touches the singleton weather preferences. */
public final class WeatherGroupOptions {
    private static final String SAVED = "source_weather";
    private static final Set<String> LEGACY_KEYS = new LinkedHashSet<>(Arrays.asList("location", "unit", SAVED));
    private WeatherGroupOptions() {}

    public static WeatherOptions read(WidgetGroups.Entry entry) {
        if (entry == null || !entry.type.equals("Weather") || !entry.hasOnlyKnownFields()
                || !LEGACY_KEYS.containsAll(entry.data.keySet())
                || !(entry.data.get("location") instanceof String) || !(entry.data.get("unit") instanceof String)) throw invalid();
        WeatherOptions legacy = WeatherOptions.fromLegacy((String) entry.data.get("location"), (String) entry.data.get("unit"));
        if (!entry.data.containsKey(SAVED)) return legacy;
        Object saved = entry.data.get(SAVED);
        if (!(saved instanceof Map)) throw invalid();
        Map<?, ?> values = (Map<?, ?>) saved;
        if (values.size() != 6) throw invalid();
        Map<String, Object> typed = new LinkedHashMap<>();
        for (String key : Arrays.asList("configured", "label", "latitude_e4", "longitude_e4", "metric", "device_time")) {
            if (!values.containsKey(key)) throw invalid();
            Object value = values.get(key);
            if (key.equals("latitude_e4") || key.equals("longitude_e4")) {
                if (!(value instanceof BigDecimal)) throw invalid();
                try { value = ((BigDecimal) value).intValueExact(); }
                catch (ArithmeticException invalid) { throw invalid(); }
            }
            typed.put(key, value);
        }
        return WeatherOptions.fromBackup(typed);
    }

    public static WidgetGroups save(WidgetGroups groups, WidgetGroups.Entry entry, WeatherOptions selected) {
        if (groups == null || selected == null) throw invalid();
        read(entry);
        Map<String, Object> data = new LinkedHashMap<>(entry.data);
        data.put(SAVED, selected.backupValues());
        return groups.updateData(entry.id, data);
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Saved weather widget needs review. Original data was kept.");
    }
}
