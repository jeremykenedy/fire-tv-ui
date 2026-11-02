package com.jeremykenedy.firetv.ui.nativeapp.widgetgroups;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.widget.weather.WeatherSettingsActivity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Explicit editing for saved groups; unavailable entries remain until the user changes them. */
public final class WidgetGroupsEditor implements WidgetGroupsView.Editor {
    private final Activity activity;
    private final WidgetGroupsStore store;
    private final Runnable changed;

    public WidgetGroupsEditor(Activity activity, Runnable changed) {
        if (activity == null || changed == null) throw new IllegalArgumentException("Missing widget editor context.");
        this.activity = activity; this.store = new WidgetGroupsStore(activity); this.changed = changed;
    }
    @Override public void open(final int groupIndex, final int entryIndex, final WidgetGroups.Entry entry) {
        final String expected;
        final WidgetGroups groups;
        try { expected = store.raw(); groups = store.load(); }
        catch (IllegalStateException unavailable) { message(unavailable.getMessage()); return; }
        if (groups == null || groupIndex < 0 || groupIndex >= groups.groups.size()) { message("Widget group is unavailable."); return; }
        final List<WidgetGroups.Entry> group = groups.groups.get(groupIndex);
        if (entry != null && (entryIndex < 0 || entryIndex >= group.size() || !entry.id.equals(group.get(entryIndex).id))) {
            message("Widget changed. Reopen its menu."); return;
        }
        if (entry == null && (entryIndex != group.size() || group.size() >= 2)) { message("Widget slot changed. Reopen its menu."); return; }
        final List<String> labels = new ArrayList<>(), actions = new ArrayList<>();
        if (entry != null && entry.type.equals("Weather")) {
            try { WeatherGroupOptions.read(entry); option(labels, actions, "Set weather location", "weather-settings"); }
            catch (IllegalArgumentException unavailable) { /* Keep unsupported data until explicitly replaced. */ }
        }
        if (entry == null && group.isEmpty()) {
            option(labels, actions, "Add clock", "clock-big");
            option(labels, actions, "Add small clock", "clock-small");
            option(labels, actions, "Add year progress", "year-big");
            option(labels, actions, "Add small year progress", "year-small");
            option(labels, actions, "Add weather", "weather-big");
            option(labels, actions, "Add small weather", "weather-small");
            option(labels, actions, "Remove empty column", "remove-column");
        } else {
            option(labels, actions, entry == null ? "Add small clock" : "Replace with clock", "clock");
            option(labels, actions, entry == null ? "Add small year progress" : "Replace with year progress", "year");
            option(labels, actions, entry == null ? "Add small weather" : "Replace with weather", "weather");
            if (entry != null) option(labels, actions, "Remove widget", "remove");
        }
        if (groupIndex > 0) option(labels, actions, "Move column earlier", "earlier");
        if (groupIndex + 1 < groups.groups.size()) option(labels, actions, "Move column later", "later");
        new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle(entry == null ? "Widget column" : entry.type + " widget")
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        try {
                            String action = actions.get(which);
                            if (action.equals("weather-settings")) {
                                activity.startActivity(new Intent(activity, WeatherSettingsActivity.class)
                                        .putExtra(WeatherSettingsActivity.EXTRA_GROUP_WIDGET_ID, entry.id));
                                return;
                            }
                            WidgetGroups next;
                            if (action.equals("remove-column")) next = groups.removeEmptyGroup(groupIndex);
                            else if (action.equals("earlier")) next = groups.moveGroup(groupIndex, groupIndex - 1);
                            else if (action.equals("later")) next = groups.moveGroup(groupIndex, groupIndex + 1);
                            else if (action.equals("remove")) next = groups.remove(entry.id);
                            else {
                                String type = action.startsWith("clock") ? "DigitalClock" : action.startsWith("weather") ? "Weather" : "YearProgress";
                                String size = action.endsWith("-big") ? "Big" : action.endsWith("-small") ? "Small"
                                        : entry == null ? "Small" : entry.size;
                                String record = "{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"" + type
                                        + "\",\"size\":\"" + size + "\",\"data\":"
                                        + (type.equals("Weather") ? "{\"unit\":\"F\",\"location\":\"\"}" : "{}") + "}";
                                next = groups.put(groupIndex, entryIndex, record);
                            }
                            if (!store.save(expected, next)) { message("Widget groups changed or could not be saved. Try again."); return; }
                            changed.run();
                        } catch (IllegalArgumentException | IllegalStateException failed) { message("Widget groups could not be saved. Check the current layout before retrying."); }
                    }
                }).setNegativeButton("Cancel", null).show();
    }
    private static void option(List<String> labels, List<String> actions, String label, String action) {
        labels.add(label); actions.add(action);
    }
    private void message(String text) { Toast.makeText(activity, text, Toast.LENGTH_LONG).show(); }
}
