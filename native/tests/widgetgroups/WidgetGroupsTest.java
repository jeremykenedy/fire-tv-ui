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
        System.out.println("Grouped widget identity, data, editing and migration checks passed.");
    }
    private static void reject(Runnable action) { try { action.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("Invalid groups accepted."); }
    private static void check(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
}
