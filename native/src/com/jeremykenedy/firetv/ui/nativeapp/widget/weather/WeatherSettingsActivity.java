package com.jeremykenedy.firetv.ui.nativeapp.widget.weather;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroups;
import com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroupsStore;
import com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WeatherGroupOptions;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WeatherSettingsActivity extends Activity {
    public static final String EXTRA_GROUP_WIDGET_ID = "weather_group_widget_id";
    private boolean startupBlocked;
    private String groupId;
    private String groupSnapshot;
    private WeatherOptions groupOptions;
    private AlertDialog placeDialog;
    private EditText label;
    private EditText latitude;
    private EditText longitude;
    private TextView preview;
    private Button units;
    private Button time;
    private boolean metric;
    private boolean deviceTime;
    private WeatherLoader loader;
    private final ExecutorService placesWorker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private WeatherRequest placeRequest;
    private int placeGeneration;
    private Button findPlace;
    private TextView placeStatus;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        WeatherOptions options;
        groupId = getIntent().getStringExtra(EXTRA_GROUP_WIDGET_ID);
        try {
            if (getIntent().hasExtra(EXTRA_GROUP_WIDGET_ID)) {
                if (groupId == null) throw new IllegalStateException("Weather widget is unavailable. Its data was kept.");
                groupSnapshot = saved == null ? new WidgetGroupsStore(this).raw() : saved.getString("weather_group_snapshot");
                groupOptions = readGroupOptions(groupSnapshot);
                options = groupOptions;
            } else options = WeatherPreferences.load(this);
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            Toast.makeText(this, invalid.getMessage(), Toast.LENGTH_LONG).show();
            if (getIntent().hasExtra(EXTRA_GROUP_WIDGET_ID)) { startupBlocked = true; finish(); return; }
            options = WeatherOptions.defaults();
        }
        metric = saved == null ? options.metric : saved.getBoolean("metric", options.metric);
        deviceTime = saved == null ? options.deviceTime : saved.getBoolean("device_time", options.deviceTime);
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "Hourly weather", 30));
        ScrollView scroll = new ScrollView(this); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); scroll.addView(body);
        body.addView(Pages.text(this, "National Weather Service hourly forecasts for supported U.S. forecast locations. Coastal marine and worldwide coverage are not provided. Air quality and pollen are not included. Radar and UV are separate widgets.", 17));
        body.addView(Pages.text(this, "Search U.S. Census places by city name, or enter coordinates manually. Search sends the name to the U.S. Census Bureau. The selected coordinates are sent to api.weather.gov when saved and while the widget is visible. Device location permission is not requested.", 17));
        if (!options.configured && !options.label.isEmpty()) {
            body.addView(Pages.text(this, "Your saved location name is retained below. Search for the city or enter coordinates, then save to check NWS coverage. NWS does not cover every location.", 17));
        }
        label = field(body, "Location name", "weather:label", options.label, false);
        findPlace = Pages.button(this, "Find U.S. city or town", new View.OnClickListener() {
            @Override public void onClick(View view) { findPlace(); }
        });
        findPlace.setTag("weather:find-place"); body.addView(findPlace);
        placeStatus = Pages.text(this, "", 16); placeStatus.setTag("weather:place-status"); body.addView(placeStatus);
        latitude = field(body, "Latitude (-90 to 90)", "weather:latitude", options.configured ? options.latitude() : "", true);
        longitude = field(body, "Longitude (-180 to 180)", "weather:longitude", options.configured ? options.longitude() : "", true);
        units = Pages.button(this, "", new View.OnClickListener() { @Override public void onClick(View view) { metric = !metric; updateLabels(); } });
        units.setTag("weather:units"); body.addView(units);
        time = Pages.button(this, "", new View.OnClickListener() { @Override public void onClick(View view) { deviceTime = !deviceTime; updateLabels(); } });
        time.setTag("weather:time"); body.addView(time); updateLabels();
        Button save = Pages.button(this, "Save location and display choices", new View.OnClickListener() {
            @Override public void onClick(View view) {
                try {
                    WeatherOptions value = WeatherOptions.location(label.getText().toString().trim(), latitude.getText().toString().trim(), longitude.getText().toString().trim(), metric, deviceTime);
                    saveOptions(value);
                    preview.setText("Checking NWS hourly coverage for the saved location...");
                    startLoader();
                } catch (IllegalArgumentException invalid) { latitude.setError(invalid.getMessage()); }
                catch (IllegalStateException failed) { Toast.makeText(WeatherSettingsActivity.this, failed.getMessage(), Toast.LENGTH_LONG).show(); }
            }
        });
        save.setTag("weather:save"); body.addView(save);
        Button refresh = Pages.button(this, "Refresh forecast", new View.OnClickListener() { @Override public void onClick(View view) { if (loader != null) loader.load(); } });
        refresh.setTag("weather:refresh"); body.addView(refresh);
        body.addView(Pages.text(this, "Refresh respects the provider's cache lifetime. A saved forecast can remain visible after a failed refresh only when its cache policy permits, and is labeled stale with its original product timestamp.", 16));
        Button remove = Pages.button(this, "Remove saved weather location", new View.OnClickListener() {
            @Override public void onClick(View view) {
                try {
                    saveOptions(new WeatherOptions(false, "", 0, 0, metric, deviceTime));
                    label.setText(""); latitude.setText(""); longitude.setText(""); updateLabels();
                    startLoader();
                } catch (IllegalStateException failed) { Toast.makeText(WeatherSettingsActivity.this, failed.getMessage(), Toast.LENGTH_LONG).show(); }
            }
        });
        remove.setTag("weather:remove"); body.addView(remove);
        preview = Pages.text(this, "Choose a location to see its hourly forecast.", 17); preview.setTag("weather:forecast"); body.addView(preview);
        body.addView(Pages.text(this, "Data source: National Weather Service, api.weather.gov. NWS data is public domain unless otherwise noted. This launcher is independent of NOAA/NWS and does not imply endorsement. Forecast issue times are preserved.", 16));
        if (saved == null) label.requestFocus();
    }
    private WeatherOptions readGroupOptions(String snapshot) {
        if (snapshot == null) throw new IllegalStateException("Weather widget is unavailable. Its data was kept.");
        WidgetGroups groups = WidgetGroups.decode(snapshot);
        return WeatherGroupOptions.read(findGroupEntry(groups));
    }
    private WidgetGroups.Entry findGroupEntry(WidgetGroups groups) {
        for (List<WidgetGroups.Entry> group : groups.groups)
            for (WidgetGroups.Entry entry : group) if (entry.id.equals(groupId)) return entry;
        throw new IllegalStateException("Weather widget is unavailable. Its data was kept.");
    }
    private void saveOptions(WeatherOptions value) {
        if (groupId == null) { WeatherPreferences.save(this, value); return; }
        try {
            WidgetGroups groups = WidgetGroups.decode(groupSnapshot);
            WidgetGroups updated = WeatherGroupOptions.save(groups, findGroupEntry(groups), value);
            if (!new WidgetGroupsStore(this).save(groupSnapshot, updated))
                throw new IllegalStateException("Widgets changed while editing. Reopen this weather widget and try again.");
            groupSnapshot = updated.encode();
            groupOptions = value;
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("Weather widget could not be saved. Its data was kept.", invalid);
        }
    }
    private EditText field(LinearLayout body, String title, String tag, String value, boolean numeric) {
        body.addView(Pages.text(this, title, 17));
        EditText field = new EditText(this); field.setTextColor(android.graphics.Color.WHITE); field.setSingleLine(true);
        field.setTag(tag); field.setContentDescription(title); field.setId(tag.hashCode() & 0x00ffffff);
        field.setInputType(numeric ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED : InputType.TYPE_CLASS_TEXT);
        field.setText(value); body.addView(field); return field;
    }
    private void findPlace() {
        final String query = label.getText().toString().trim();
        try { WeatherPlaces.url(query, 4); }
        catch (Exception invalid) { label.setError("Enter a city name and optional two-letter state, such as Portland, OR."); return; }
        cancelPlaceSearch();
        final int generation = ++placeGeneration;
        final WeatherRequest request = new WeatherRequest(); placeRequest = request;
        findPlace.setEnabled(false);
        placeStatus.setText("Searching U.S. Census places...");
        placesWorker.execute(new Runnable() { @Override public void run() {
            try {
                final List<WeatherPlaces.Place> results = searchPlaces(query, request);
                main.post(new Runnable() { @Override public void run() {
                    if (generation != placeGeneration || isFinishing()) return;
                    placeRequest = null; findPlace.setEnabled(true);
                    if (results.isEmpty()) {
                        placeStatus.setText("No matching Census place found. Enter coordinates manually."); return;
                    }
                    String[] choices = new String[results.size()];
                    for (int i = 0; i < choices.length; i++) choices[i] = results.get(i).description();
                    placeStatus.setText("Choose a place, or cancel to keep your current choices.");
                    placeDialog = new AlertDialog.Builder(WeatherSettingsActivity.this).setTitle("Choose a Census place")
                            .setItems(choices, new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface dialog, int which) {
                                    WeatherPlaces.Place selected = results.get(which);
                                    label.setText(selected.label); latitude.setText(selected.latitude); longitude.setText(selected.longitude);
                                    placeStatus.setText("Review and save. NWS coverage is checked after saving.");
                                }
                            }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface dialog, int which) { placeStatus.setText("Search cancelled. Choices are unchanged."); }
                            }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override public void onCancel(DialogInterface dialog) { placeStatus.setText("Search cancelled. Choices are unchanged."); }
                            }).show();
                }});
            } catch (final Exception failure) {
                main.post(new Runnable() { @Override public void run() {
                    if (generation != placeGeneration || isFinishing()) return;
                    placeRequest = null; findPlace.setEnabled(true);
                    placeStatus.setText("Place search unavailable. Saved choices are unchanged; enter coordinates manually.");
                }});
            }
        }});
    }
    protected List<WeatherPlaces.Place> searchPlaces(String query, WeatherRequest request) throws Exception {
        return WeatherPlaces.search(query, new WeatherHttp(), request);
    }
    private void cancelPlaceSearch() {
        placeGeneration++;
        if (placeDialog != null) { placeDialog.dismiss(); placeDialog = null; }
        if (placeRequest != null) {
            placeRequest.cancel(); placeRequest = null;
            if (placeStatus != null) placeStatus.setText("Search cancelled. Choices are unchanged.");
        }
        if (findPlace != null) findPlace.setEnabled(true);
    }
    private void updateLabels() {
        units.setText(metric ? "Units: Celsius and metric" : "Units: Fahrenheit and U.S.");
        time.setText(deviceTime ? "Forecast times: TV time zone" : "Forecast times: selected location time zone");
    }
    @Override protected void onSaveInstanceState(Bundle saved) {
        if (startupBlocked) { super.onSaveInstanceState(saved); return; }
        if (groupId != null) saved.putString("weather_group_snapshot", groupSnapshot);
        saved.putBoolean("metric", metric); saved.putBoolean("device_time", deviceTime); super.onSaveInstanceState(saved);
    }
    @Override protected void onResume() {
        if (startupBlocked) { super.onResume(); return; }
        super.onResume();
        startLoader();
    }
    private void startLoader() {
        if (loader != null) loader.close();
        WeatherLoader.Listener listener = new WeatherLoader.Listener() {
            @Override public void onForecast(WeatherOptions options, WeatherForecast forecast) {
                preview.setText(options == null ? forecast.message
                        : forecast.display(options, System.currentTimeMillis(), 12, DateFormat.is24HourFormat(WeatherSettingsActivity.this)));
            }
        };
        loader = groupId == null ? new WeatherLoader(this, listener)
                : new WeatherLoader(this, listener, new WeatherRepository(new File(getCacheDir(), "weather-hourly")), groupOptions);
        loader.start();
    }
    @Override protected void onPause() {
        if (startupBlocked) { super.onPause(); return; } if (loader != null) { loader.close(); loader = null; } cancelPlaceSearch(); super.onPause(); }
    @Override protected void onDestroy() { cancelPlaceSearch(); placesWorker.shutdownNow(); super.onDestroy(); }
}
