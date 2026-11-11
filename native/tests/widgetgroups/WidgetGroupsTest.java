package com.jeremykenedy.firetv.ui.nativeapp.widgetgroups;

import com.jeremykenedy.firetv.ui.nativeapp.widget.weather.WeatherOptions;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WidgetGroupsTest {
    private static final String A = "00000000-0000-4000-8000-000000000001";
    private static final String B = "00000000-0000-4000-8000-000000000002";
    private static final String C = "00000000-0000-4000-8000-000000000003";
    private static final String SOURCE = "[[],[{\"id\":\"" + A + "\",\"type\":\"Weather\",\"size\":\"Big\",\"data\":{\"unit\":\"F\",\"location\":\"\"}}],"
            + "[{\"id\":\"" + B + "\",\"type\":\"DigitalClock\",\"size\":\"Small\",\"data\":{}},"
            + "{\"id\":\"" + C + "\",\"type\":\"DigitalClock\",\"size\":\"Small\",\"data\":{}}]]";
    public static void main(String[] args) {
        LegacyYearProgressModel observed = LegacyYearProgressModel.at(java.time.LocalDate.of(2026, 11, 2));
        check(observed.percent == 83 && observed.filledDots == 43 && LegacyYearProgressModel.DOTS == 52,
                "The observed November fixture has 83 percent and 43 filled dots of 52.");
        WidgetGroups groups = WidgetGroups.decode(SOURCE);
        check(groups.groups.size() == 3 && groups.groups.get(0).isEmpty(), "Empty group and order are retained.");
        check(groups.displayColumnCount() == 4 && groups.withDisplayColumns().groups.size() == 4
                && groups.withDisplayColumns().groups.get(3).isEmpty() && groups.groups.size() == 3,
                "Original four-column display pads absent columns without changing saved records.");
        check(WidgetGroups.decode("[]").withDisplayColumns().groups.size() == 4, "Explicit empty groups show four editable columns.");
        check(groups.groups.get(2).size() == 2 && !groups.groups.get(2).get(0).id.equals(groups.groups.get(2).get(1).id), "Duplicate clock types retain independent ids.");
        check(!groups.groups.get(1).get(0).isRenderable() && groups.groups.get(2).get(0).isRenderable(), "Weather is explicitly unavailable until per-instance location works.");
        check(WidgetGroups.decode(groups.encode()).encode().equals(groups.encode()), "Group serialization round trips.");
        WidgetGroups removed = groups.remove(B);
        check(removed.groups.get(2).size() == 1 && removed.groups.get(2).get(0).id.equals(C), "Removing one duplicate preserves the other.");
        check(groups.groups.get(2).size() == 2, "Editing leaves the input immutable.");
        WidgetGroups reordered = groups.moveGroup(2, 0);
        check(reordered.groups.get(0).get(0).id.equals(B) && reordered.groups.get(1).isEmpty(), "Moving groups preserves empty positions and member order.");
        WidgetGroups appended = groups.appendEmptyGroup();
        check(appended.groups.size() == 4 && appended.removeEmptyGroup(3).groups.size() == 3, "Empty group editing works.");
        String newId = "00000000-0000-4000-8000-000000000004";
        String record = "{\"id\":\"" + newId + "\",\"type\":\"YearProgress\",\"size\":\"Small\",\"data\":{}}";
        WidgetGroups added = groups.put(0, 0, record);
        check(added.groups.get(0).get(0).id.equals(newId) && added.groups.get(2).size() == 2, "Adding to an empty group retains other groups.");
        WidgetGroups replaced = added.put(2, 0, "{\"id\":\"" + B + "\",\"type\":\"YearProgress\",\"size\":\"Small\",\"data\":{}}");
        check(replaced.groups.get(2).get(0).type.equals("YearProgress") && replaced.groups.get(2).get(1).id.equals(C), "Replacing one entry retains its neighbor.");
        reject(() -> groups.removeEmptyGroup(1));
        String unknown = "[[{\"id\":\"" + A + "\",\"type\":\"FutureWidget\",\"size\":\"Big\",\"data\":{\"setting\":[1,true,null]},\"future\":\"retained\"}]]";
        WidgetGroups future = WidgetGroups.decode(unknown);
        check(!future.groups.get(0).get(0).isKnown() && !future.groups.get(0).get(0).isRenderable(), "Unknown type remains explicit.");
        check(WidgetGroups.decode(future.encode()).groups.get(0).get(0).data.equals(future.groups.get(0).get(0).data), "Unknown data survives round trip.");
        check(future.encode().contains("future"), "Unknown record fields are preserved.");
        reject(() -> WidgetGroups.decode("[[{\"id\":\"" + A + "\",\"type\":\"DigitalClock\",\"size\":\"Big\",\"data\":{}},{\"id\":\"" + A + "\",\"type\":\"DigitalClock\",\"size\":\"Small\",\"data\":{}}]]"));
        reject(() -> WidgetGroups.decode("[[{\"id\":\"" + A + "\",\"id\":\"" + B + "\",\"type\":\"DigitalClock\",\"size\":\"Big\",\"data\":{}}]]"));
        reject(() -> WidgetGroups.decode("[],\"other\":1"));
        reject(() -> groups.put(2, 1, "{\"id\":\"" + B + "\",\"type\":\"DigitalClock\",\"size\":\"Small\",\"data\":{}}"));
        Map<String, Object> legacy = new LinkedHashMap<>(); legacy.put("widgets_config", SOURCE); legacy.put("enable_widgets", false);
        check(WidgetGroupMigration.patch(legacy, Collections.emptyMap()).get(WidgetGroupMigration.KEY).equals(SOURCE), "Migration retains exact raw data even when disabled.");
        reject(() -> WidgetGroupMigration.patch(legacy, Collections.singletonMap(WidgetGroupMigration.KEY, "[]")));
        check(WidgetGroupMigration.patch(Collections.emptyMap(), Collections.emptyMap()).isEmpty(), "Absent configuration is not invented.");
        String weathers = "[[{\"id\":\"" + A + "\",\"type\":\"Weather\",\"size\":\"Small\",\"data\":{\"unit\":\"F\",\"location\":\"Portland\"}},"
                + "{\"id\":\"" + B + "\",\"type\":\"Weather\",\"size\":\"Small\",\"data\":{\"unit\":\"C\",\"location\":\"Seattle\"}}]]";
        WidgetGroups weatherGroups = WidgetGroups.decode(weathers);
        WeatherOptions first = WeatherGroupOptions.read(weatherGroups.groups.get(0).get(0));
        check(!first.configured && first.label.equals("Portland") && !first.metric, "Legacy city stays unconfigured without guessing coordinates.");
        WeatherOptions selected = WeatherOptions.location("Portland", "45.52", "-122.68", false, false);
        WidgetGroups changedWeather = WeatherGroupOptions.save(weatherGroups, weatherGroups.groups.get(0).get(0), selected);
        WeatherOptions restored = WeatherGroupOptions.read(changedWeather.groups.get(0).get(0));
        WeatherOptions second = WeatherGroupOptions.read(changedWeather.groups.get(0).get(1));
        check(restored.configured && restored.latitude().equals("45.5200") && restored.longitude().equals("-122.6800"), "Explicit first location is saved per instance.");
        check(!second.configured && second.label.equals("Seattle") && second.metric, "Second weather choice is not overwritten.");
        check(changedWeather.groups.get(0).get(0).data.get("location").equals("Portland")
                && changedWeather.groups.get(0).get(0).data.get("unit").equals("F"), "Original legacy weather fields remain intact.");
        enabledMigration();
        System.out.println("Grouped widget identity, data, editing and migration checks passed.");
    }
    private static void enabledMigration() {
        String clock = "{\"id\":\"" + A + "\",\"type\":\"DigitalClock\",\"size\":\"Small\",\"data\":{}}";
        String year = "{\"id\":\"" + B + "\",\"type\":\"YearProgress\",\"size\":\"Small\",\"data\":{}}";
        String raw = "[[],[" + clock + "," + year + "],[],[]]";
        for (Boolean enabled : new Boolean[]{null, false, true}) for (Boolean hide : new Boolean[]{null, false, true}) {
            Map<String, Object> legacy = new LinkedHashMap<>(); legacy.put("widgets_config", raw);
            if (enabled != null) legacy.put("enable_widgets", enabled);
            if (hide != null) legacy.put("hide_watch_next", hide);
            Map<String, Object> original = new LinkedHashMap<>(legacy);
            Map<String, Object> mapped = WidgetGroupMigration.supportedPatch(legacy, Collections.emptyMap());
            String mode = Boolean.TRUE.equals(hide) ? "NONE" : Boolean.TRUE.equals(enabled) ? "WIDGETS_ONLY" : "CARDS_ONLY";
            check(mode.equals(mapped.get("mode")), "Original row and widget defaults select the equivalent native mode.");
            check(raw.equals(mapped.get(WidgetGroupMigration.KEY)) && legacy.equals(original), "Enabled migration keeps raw records and flag absence unchanged.");
            check(((String) mapped.get("hidden")).equals("widget:clock"), "Only the duplicate default clock is hidden.");
            Map<String, Object> existing = new LinkedHashMap<>(mapped);
            check(mode.equals(WidgetGroupMigration.supportedPatch(legacy, existing).get("mode")),
                    "An identical existing mode remains migratable.");
            existing.put("mode", mode.equals("NONE") ? "BOTH" : "NONE");
            reject(() -> WidgetGroupMigration.supportedPatch(legacy, existing));
        }
        Map<String, Object> enabled = new LinkedHashMap<>(); enabled.put("enable_widgets", true);
        Map<String, Object> defaults = WidgetGroupMigration.supportedPatch(enabled, Collections.emptyMap());
        check("[[],[],[],[]]".equals(defaults.get(WidgetGroupMigration.KEY)) && "WIDGETS_ONLY".equals(defaults.get("mode")),
                "Enabling widgets without a saved configuration restores the original four empty columns.");
        enabled.put("widgets_config", "");
        check(defaults.equals(WidgetGroupMigration.supportedPatch(enabled, Collections.emptyMap())) && "".equals(enabled.get("widgets_config")),
                "The original empty configuration selects four columns without rewriting the source.");
        enabled.put("widgets_config", raw);
        reject(() -> WidgetGroupMigration.supportedPatch(enabled, Collections.singletonMap("hidden", "widget:grouped-widgets")));
        reject(() -> WidgetGroupMigration.supportedPatch(enabled, Collections.singletonMap("order", "widget:clock")));
        for (Object invalidFlag : new Object[]{"true", 1}) {
            enabled.put("enable_widgets", invalidFlag);
            reject(() -> WidgetGroupMigration.supportedPatch(enabled, Collections.emptyMap()));
        }
        enabled.put("enable_widgets", true);
        enabled.put("widgets_config", SOURCE);
        reject(() -> WidgetGroupMigration.supportedPatch(enabled, Collections.emptyMap()));
        enabled.put("widgets_config", raw.replace("DigitalClock", "FutureWidget"));
        reject(() -> WidgetGroupMigration.supportedPatch(enabled, Collections.emptyMap()));
        enabled.put("widgets_config", raw.replace("\"data\":{}", "\"data\":{\"future\":true}"));
        reject(() -> WidgetGroupMigration.supportedPatch(enabled, Collections.emptyMap()));
    }
    private static void reject(Runnable action) { try { action.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("Invalid groups accepted."); }
    private static void check(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
}
