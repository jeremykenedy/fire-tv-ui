package com.jeremykenedy.firetv.ui.nativeapp.widget.radar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputFilter;
import android.widget.EditText;

public final class RadarSettings {
    private RadarSettings() {}
    public static void show(final Activity activity) {
        String current;
        try { RadarSource source = RadarPreferences.load(activity); current = source == null ? "Choose a supported region or station." : "Selected: " + source.label; }
        catch (IllegalStateException invalid) { current = invalid.getMessage(); }
        new AlertDialog.Builder(activity).setTitle("NWS radar")
                .setItems(new String[]{current, "View complete radar image", "Choose region", "Enter station code", "Clear radar area"}, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int item) {
                        if (item == 1) RadarView.show(activity);
                        else if (item == 2) region(activity);
                        else if (item == 3) station(activity);
                        else if (item == 4) save(activity, "");
                        else new AlertDialog.Builder(activity).setTitle("Radar coverage")
                                .setMessage("Official NWS standard still images cover the selected U.S. region or radar station. A station can be offline. This is not an alert service. The complete source image includes its own legend and product time. No GPS or paid account is used.")
                                .setPositiveButton("Done", null).show();
                    }
                }).setNegativeButton("Back", null).show();
    }
    private static void region(final Activity activity) {
        String[] names = new String[RadarSource.REGIONS.size()];
        for (int i = 0; i < names.length; i++) names[i] = RadarSource.REGIONS.get(i).label;
        new AlertDialog.Builder(activity).setTitle("Radar region").setItems(names, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int index) { save(activity, RadarSource.REGIONS.get(index).id); }
        }).setNegativeButton("Cancel", null).show();
    }
    private static void station(final Activity activity) {
        final EditText code = new EditText(activity);
        code.setSingleLine(true); code.setHint("Station code, for example KRTX");
        code.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        code.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(4)});
        final AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("NWS radar station")
                .setView(code).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface owner) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new android.view.View.OnClickListener() {
                    @Override public void onClick(android.view.View view) {
                        String id = "station:" + code.getText().toString().trim().toUpperCase(java.util.Locale.US);
                        if (RadarSource.find(id) == null) { code.setError("Enter a station code from the NWS standard-image directory."); return; }
                        if (save(activity, id)) dialog.dismiss();
                    }
                }); code.requestFocus();
            }
        }); dialog.show();
    }
    private static boolean save(Activity activity, String id) {
        try { RadarPreferences.save(activity, id); return true; }
        catch (IllegalStateException failure) {
            new AlertDialog.Builder(activity).setTitle("Radar area not saved").setMessage(failure.getMessage()).setPositiveButton("OK", null).show();
            return false;
        }
    }
}
