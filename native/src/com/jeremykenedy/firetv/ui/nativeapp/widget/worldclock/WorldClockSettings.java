package com.jeremykenedy.firetv.ui.nativeapp.widget.worldclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.widget.EditText;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

/** Native remote-friendly lists keep time-zone selection independent of a keyboard. */
public final class WorldClockSettings {
    private WorldClockSettings() {}

    public static void show(Activity activity) {
        final WorldClocks clocks;
        try { clocks = WorldClockPreferences.read(activity); }
        catch (IllegalArgumentException error) {
            new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle("World clocks unavailable")
                    .setMessage("Saved clocks could not be read. Clear only the world-clock list to start again.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Clear clocks", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            if (save(activity, WorldClocks.empty())) show(activity);
                        }
                    }).show();
            return;
        }
        List<String> rows = new ArrayList<>();
        for (WorldClocks.Entry entry : clocks.entries) rows.add(entry.label + "  (" + entry.zone + ")");
        if (clocks.entries.size() < WorldClocks.MAX_CLOCKS) rows.add("Add clock");
        new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle("World clocks (" + clocks.entries.size() + "/6)")
                .setItems(rows.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int index) {
                        if (index == clocks.entries.size()) regions(activity, clocks, -1);
                        else actions(activity, clocks, index);
                    }
                }).setNegativeButton("Done", null).show();
    }

    private static void actions(Activity activity, WorldClocks clocks, int index) {
        List<String> actions = new ArrayList<>();
        actions.add("Edit label");
        actions.add("Change time zone");
        actions.add("Remove clock");
        if (index > 0) actions.add("Move earlier");
        if (index + 1 < clocks.entries.size()) actions.add("Move later");
        new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle(clocks.entries.get(index).label)
                .setItems(actions.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String action = actions.get(which);
                        if (action.equals("Edit label")) label(activity, clocks, index, clocks.entries.get(index).zone);
                        else if (action.equals("Change time zone")) regions(activity, clocks, index);
                        else {
                            WorldClocks next = action.equals("Remove clock") ? clocks.remove(index)
                                    : clocks.move(index, index + (action.equals("Move earlier") ? -1 : 1));
                            if (save(activity, next)) show(activity);
                        }
                    }
                }).setNegativeButton("Back", back(activity, clocks, index, null)).show();
    }

    private static List<String> zones() {
        List<String> zones = new ArrayList<>();
        for (String zone : TimeZone.getAvailableIDs()) if (zone.contains("/") || zone.equals("UTC")) zones.add(zone);
        Collections.sort(zones);
        return zones;
    }

    private static void regions(Activity activity, WorldClocks clocks, int index) {
        List<String> regions = new ArrayList<>();
        for (String zone : zones()) {
            String region = zone.contains("/") ? zone.substring(0, zone.indexOf('/')) : zone;
            if (!regions.contains(region)) regions.add(region);
        }
        new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle("Choose a region")
                .setItems(regions.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        zone(activity, clocks, index, regions.get(which));
                    }
                })
                .setNegativeButton("Back", back(activity, clocks, index, null)).show();
    }

    private static void zone(Activity activity, WorldClocks clocks, int index, String region) {
        List<String> choices = new ArrayList<>();
        for (String zone : zones()) if (zone.equals(region) || zone.startsWith(region + "/")) choices.add(zone);
        new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle(region + " time zones")
                .setItems(choices.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        label(activity, clocks, index, choices.get(which));
                    }
                })
                .setNegativeButton("Back", back(activity, clocks, index, region)).show();
    }

    private static void label(Activity activity, WorldClocks clocks, int index, String zone) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint("Clock label (1 to 40 characters)");
        input.setText(index < 0 ? zone.substring(zone.lastIndexOf('/') + 1).replace('_', ' ')
                : clocks.entries.get(index).label);
        input.selectAll();
        AlertDialog dialog = new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle(zone).setView(input)
                .setNegativeButton("Cancel", back(activity, clocks, index, null))
                .setPositiveButton("Save", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        try {
                            WorldClocks.Entry entry = new WorldClocks.Entry(zone, input.getText().toString());
                            WorldClocks next = index < 0 ? clocks.add(entry) : clocks.replace(index, entry);
                            if (save(activity, next)) { dialog.dismiss(); show(activity); }
                        } catch (IllegalArgumentException error) { input.setError(error.getMessage()); }
                    }
                });
            }
        });
        dialog.show();
    }

    private static DialogInterface.OnClickListener back(final Activity activity, final WorldClocks clocks,
            final int index, final String region) {
        return new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (region == null) show(activity); else regions(activity, clocks, index);
            }
        };
    }

    private static boolean save(Activity activity, WorldClocks clocks) {
        try { WorldClockPreferences.save(activity, clocks); return true; }
        catch (IllegalStateException error) {
            new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle("World clocks were not saved")
                    .setMessage(error.getMessage()).setPositiveButton("OK", null).show();
            return false;
        }
    }
}
