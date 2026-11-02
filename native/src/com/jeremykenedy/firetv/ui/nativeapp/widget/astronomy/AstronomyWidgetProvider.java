package com.jeremykenedy.firetv.ui.nativeapp.widget.astronomy;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import java.util.TimeZone;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;

public final class AstronomyWidgetProvider implements WidgetProvider {
    public static final LayoutItem ITEM = new LayoutItem("sun-moon", LayoutItem.Kind.WIDGET);
    @Override public LayoutItem item() { return ITEM; }
    @Override public String title() { return "Sun and moon"; }
    @Override public State state(Context context) {
        try {
            if (AstronomyPreferences.read(context) == null) return State.SETUP_REQUIRED;
            Astronomy.validateDate(System.currentTimeMillis());
            return State.READY;
        } catch (IllegalArgumentException error) { return State.UNAVAILABLE; }
    }
    @Override public String message(Context context) {
        State state = state(context);
        if (state == State.SETUP_REQUIRED) return "Enter coordinates and time zone manually. No automatic location or network access.";
        if (state == State.UNAVAILABLE) return "Review saved location and the TV date. Calculations support 1901 through 2099.";
        AstronomyLocation location = AstronomyPreferences.read(context);
        Astronomy.SunDay sun = Astronomy.sunDay(System.currentTimeMillis(), location.latitude, location.longitude, TimeZone.getTimeZone(location.zone));
        if (sun.state == Astronomy.SunState.ABOVE_HORIZON) return "Polar day: sun stays above the horizon; no sunrise or sunset today.";
        if (sun.state == Astronomy.SunState.BELOW_HORIZON) return "Polar night: sun stays below the horizon; no sunrise or sunset today.";
        if (sun.state == Astronomy.SunState.TRANSITION) return "Near a polar transition: some solar events may be unavailable.";
        return "Calculated sunrise, sunset and approximate moon illumination. Terrain and atmospheric conditions affect observed sunrise.";
    }
    @Override public int widthDp() { return 400; }
    @Override public int heightDp() { return 300; }
    @Override public View createView(Context context) { return new AstronomyView(context); }
    @Override public void configure(Activity activity) { AstronomySettings.show(activity); }
    @Override public void onStart(View view) { ((AstronomyView) view).start(); }
    @Override public void onStop(View view) { ((AstronomyView) view).stop(); }
}
