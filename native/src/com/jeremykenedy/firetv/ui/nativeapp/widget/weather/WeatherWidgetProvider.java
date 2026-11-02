package com.jeremykenedy.firetv.ui.nativeapp.widget.weather;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;

public final class WeatherWidgetProvider implements WidgetProvider {
    public static final LayoutItem ITEM = new LayoutItem("weather-hourly", LayoutItem.Kind.WIDGET);
    @Override public LayoutItem item() { return ITEM; }
    @Override public String title() { return "Hourly weather"; }
    @Override public State state(Context context) {
        try { return WeatherPreferences.load(context).configured ? State.READY : State.SETUP_REQUIRED; }
        catch (IllegalStateException invalid) { return State.UNAVAILABLE; }
    }
    @Override public String message(Context context) {
        return "NWS hourly forecasts for a manually selected, supported U.S. location. The widget reports loading, empty, offline and stale data states. No device location permission is used.";
    }
    @Override public int widthDp() { return 440; }
    @Override public int heightDp() { return 300; }
    @Override public View createView(Context context) { return new WeatherView(context); }
    public View createView(Context context, WeatherOptions options) {
        if (options == null) throw new IllegalArgumentException("Instance weather choices are required.");
        return new WeatherView(context, options);
    }
    @Override public void configure(Activity activity) { activity.startActivity(new Intent(activity, WeatherSettingsActivity.class)); }
    @Override public void onStart(View view) { if (view instanceof WeatherView) ((WeatherView) view).start(); }
    @Override public void onStop(View view) { if (view instanceof WeatherView) ((WeatherView) view).stop(); }
}
