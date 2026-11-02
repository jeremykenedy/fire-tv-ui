package com.jeremykenedy.firetv.ui.nativeapp.widget.weather;

import android.app.Activity;
import android.os.Bundle;
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

public final class WeatherSettingsActivity extends Activity {
    private boolean startupBlocked;
    private EditText label;
    private EditText latitude;
    private EditText longitude;
    private TextView preview;
    private Button units;
    private Button time;
    private boolean metric;
    private boolean deviceTime;
    private WeatherLoader loader;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        WeatherOptions options;
        try { options = WeatherPreferences.load(this); }
        catch (IllegalStateException invalid) { options = WeatherOptions.defaults(); Toast.makeText(this, invalid.getMessage(), Toast.LENGTH_LONG).show(); }
        metric = saved == null ? options.metric : saved.getBoolean("metric", options.metric);
        deviceTime = saved == null ? options.deviceTime : saved.getBoolean("device_time", options.deviceTime);
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "Hourly weather", 30));
        ScrollView scroll = new ScrollView(this); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); scroll.addView(body);
        body.addView(Pages.text(this, "National Weather Service hourly forecasts for supported U.S. forecast locations. Coastal marine and worldwide coverage are not provided. Radar, air quality, pollen and UV are not included.", 17));
        body.addView(Pages.text(this, "Enter approximate coordinates manually. These coordinates are sent to api.weather.gov when saved and while the widget is visible. Device location permission is not requested.", 17));
        if (!options.configured && !options.label.isEmpty()) {
            body.addView(Pages.text(this, "Your saved location name is retained below. Enter its coordinates to check whether NWS provides an hourly forecast there. NWS does not cover every location.", 17));
        }
        label = field(body, "Location name", "weather:label", options.label, false);
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
                    WeatherPreferences.save(WeatherSettingsActivity.this, value);
                    preview.setText("Checking NWS hourly coverage for the saved location...");
                    if (loader != null) loader.load();
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
                    WeatherPreferences.save(WeatherSettingsActivity.this, new WeatherOptions(false, "", 0, 0, metric, deviceTime));
                    label.setText(""); latitude.setText(""); longitude.setText(""); updateLabels();
                    if (loader != null) loader.load();
                } catch (IllegalStateException failed) { Toast.makeText(WeatherSettingsActivity.this, failed.getMessage(), Toast.LENGTH_LONG).show(); }
            }
        });
        remove.setTag("weather:remove"); body.addView(remove);
        preview = Pages.text(this, "Choose a location to see its hourly forecast.", 17); preview.setTag("weather:forecast"); body.addView(preview);
        body.addView(Pages.text(this, "Data source: National Weather Service, api.weather.gov. NWS data is public domain unless otherwise noted. This launcher is independent of NOAA/NWS and does not imply endorsement. Forecast issue times are preserved.", 16));
        if (saved == null) label.requestFocus();
    }
    private EditText field(LinearLayout body, String title, String tag, String value, boolean numeric) {
        body.addView(Pages.text(this, title, 17));
        EditText field = new EditText(this); field.setTextColor(android.graphics.Color.WHITE); field.setSingleLine(true);
        field.setTag(tag); field.setContentDescription(title); field.setId(tag.hashCode() & 0x00ffffff);
        field.setInputType(numeric ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED : InputType.TYPE_CLASS_TEXT);
        field.setText(value); body.addView(field); return field;
    }
    private void updateLabels() {
        units.setText(metric ? "Units: Celsius and metric" : "Units: Fahrenheit and U.S.");
        time.setText(deviceTime ? "Forecast times: TV time zone" : "Forecast times: selected location time zone");
    }
    @Override protected void onSaveInstanceState(Bundle saved) {
        if (startupBlocked) { super.onSaveInstanceState(saved); return; }
        saved.putBoolean("metric", metric); saved.putBoolean("device_time", deviceTime); super.onSaveInstanceState(saved);
    }
    @Override protected void onResume() {
        if (startupBlocked) { super.onResume(); return; }
        super.onResume();
        loader = new WeatherLoader(this, new WeatherLoader.Listener() {
            @Override public void onForecast(WeatherOptions options, WeatherForecast forecast) {
                preview.setText(options == null ? forecast.message
                        : forecast.display(options, System.currentTimeMillis(), 12, DateFormat.is24HourFormat(WeatherSettingsActivity.this)));
            }
        }); loader.start();
    }
    @Override protected void onPause() {
        if (startupBlocked) { super.onPause(); return; } if (loader != null) { loader.close(); loader = null; } super.onPause(); }
}
