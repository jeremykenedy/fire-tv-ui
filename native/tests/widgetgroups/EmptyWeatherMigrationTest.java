package com.jeremykenedy.firetv.ui.nativeapp.widget.weather;

import com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroupMigration;
import com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroups;
import com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WeatherGroupOptions;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EmptyWeatherMigrationTest {
    public static void main(String[] args) throws Exception {
        File directory = Files.createTempDirectory("empty-weather-migration-").toFile();
        try {
            for (String unit : new String[]{"C", "F"}) for (String size : new String[]{"Small", "Big"}) {
                String raw = "[[{\"id\":\"11111111-1111-4111-8111-111111111111\",\"type\":\"Weather\",\"size\":\"" + size
                        + "\",\"data\":{\"location\":\"\",\"unit\":\"" + unit + "\"}}]]";
                Map<String, Object> source = new LinkedHashMap<>(); source.put("widgets_config", raw); source.put("enable_widgets", true);
                Map<String, Object> patch = WidgetGroupMigration.supportedPatch(source, Collections.emptyMap());
                check(raw.equals(patch.get(WidgetGroupMigration.KEY)), "Migration retains exact empty Weather record and unit.");
                WidgetGroups groups = WidgetGroups.decode((String) patch.get(WidgetGroupMigration.KEY));
                WidgetGroups.Entry entry = groups.groups.get(0).get(0);
                WeatherOptions options = WeatherGroupOptions.read(entry);
                int[] connections = {0};
                WeatherRepository repository = new WeatherRepository(directory, new WeatherHttp(url -> { connections[0]++; throw new IOException("Fixture only"); }));
                for (int refresh = 0; refresh < 3; refresh++) {
                    check(repository.read(options, System.currentTimeMillis(), new WeatherRequest()).state == WeatherForecast.State.SETUP_REQUIRED,
                            "Empty legacy location remains explicitly unconfigured.");
                }
                check(connections[0] == 0 && options.metric == unit.equals("C") && options.label.isEmpty(),
                        "Empty location never starts a network request or changes the saved unit.");
                WeatherOptions selected = WeatherOptions.location("Explicit selection", "45.52", "-122.68", options.metric, false);
                WidgetGroups configured = WeatherGroupOptions.save(groups, entry, selected);
                WeatherOptions saved = WeatherGroupOptions.read(configured.groups.get(0).get(0));
                repository.read(saved, System.currentTimeMillis(), new WeatherRequest());
                check(connections[0] == 1 && saved.metric == options.metric && entry.id.equals(configured.groups.get(0).get(0).id)
                        && unit.equals(configured.groups.get(0).get(0).data.get("unit")) && "".equals(configured.groups.get(0).get(0).data.get("location")),
                        "Only explicit setup starts a request and retains the legacy UUID, location and unit.");
            }
        } finally { check(directory.delete(), "Isolated weather cache was not empty."); }
        System.out.println("Enabled empty Weather migration and explicit network opt-in passed.");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
