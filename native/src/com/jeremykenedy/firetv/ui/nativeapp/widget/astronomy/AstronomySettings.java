package com.jeremykenedy.firetv.ui.nativeapp.widget.astronomy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.View;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

public final class AstronomySettings {
    private AstronomySettings() {}
    public static void show(final Activity activity) {
        AstronomyLocation loaded = null;
        boolean invalid = false;
        try { loaded = AstronomyPreferences.read(activity); }
        catch (IllegalArgumentException error) { invalid = true; }
        final LinearLayout fields = new LinearLayout(activity);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        fields.setPadding(padding, padding, padding, padding);
        TextView privacy = new TextView(activity);
        privacy.setText("Enter a place manually. Coordinates are saved locally; no automatic location or network lookup. "
                + "Times use an unobstructed sea-level horizon and standard refraction; terrain and weather can change what you see."
                + (invalid ? " Saved location is invalid. Replace it or clear it." : ""));
        fields.addView(privacy);
        final EditText label = field(activity, fields, "Location label", loaded == null ? "" : loaded.label, false);
        final EditText latitude = field(activity, fields, "Latitude: north +, south -", loaded == null ? "" : String.valueOf(loaded.latitude), true);
        final EditText longitude = field(activity, fields, "Longitude: east +, west -", loaded == null ? "" : String.valueOf(loaded.longitude), true);
        TextView zoneTitle = new TextView(activity);
        zoneTitle.setText("IANA time zone (for example Europe/London)");
        fields.addView(zoneTitle);
        final AutoCompleteTextView zone = new AutoCompleteTextView(activity);
        zone.setSingleLine(true);
        zone.setThreshold(1);
        List<String> zones = new ArrayList<>();
        for (String id : TimeZone.getAvailableIDs()) if (id.contains("/") || id.equals("UTC")) zones.add(id);
        Collections.sort(zones);
        zone.setAdapter(new ArrayAdapter<String>(activity, android.R.layout.simple_dropdown_item_1line, zones));
        zone.setText(loaded == null ? TimeZone.getDefault().getID() : loaded.zone);
        fields.addView(zone);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(fields);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity).setTitle("Sun and moon location").setView(scroll)
                .setNegativeButton("Cancel", null).setPositiveButton("Save", null);
        if (loaded != null || invalid) builder.setNeutralButton("Clear location", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                try { AstronomyPreferences.save(activity, null); }
                catch (IllegalStateException error) { failure(activity, error.getMessage()); }
            }
        });
        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface shown) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        try {
                            AstronomyLocation location = new AstronomyLocation(Double.parseDouble(latitude.getText().toString().trim()),
                                    Double.parseDouble(longitude.getText().toString().trim()), zone.getText().toString().trim(), label.getText().toString());
                            AstronomyPreferences.save(activity, location);
                            dialog.dismiss();
                        } catch (NumberFormatException error) { latitude.setError("Enter decimal latitude and longitude using a decimal point."); }
                        catch (IllegalArgumentException error) { label.setError(error.getMessage()); }
                        catch (IllegalStateException error) { failure(activity, error.getMessage()); }
                    }
                });
            }
        });
        dialog.show();
    }

    private static EditText field(Activity activity, LinearLayout fields, String title, String value, boolean number) {
        TextView name = new TextView(activity);
        name.setText(title);
        fields.addView(name);
        EditText field = new EditText(activity);
        field.setSingleLine(true);
        if (number) field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        field.setText(value);
        fields.addView(field);
        return field;
    }
    private static void failure(Activity activity, String message) {
        new AlertDialog.Builder(activity).setTitle("Location was not saved").setMessage(message).setPositiveButton("OK", null).show();
    }
}
