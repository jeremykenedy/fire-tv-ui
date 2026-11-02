package com.jeremykenedy.firetv.ui.nativeapp.widget.radar;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;

public final class RadarWidgetProvider implements WidgetProvider {
    public static final LayoutItem ITEM = new LayoutItem("weather-radar", LayoutItem.Kind.WIDGET);
    @Override public LayoutItem item() { return ITEM; }
    @Override public String title() { return "NWS radar"; }
    @Override public State state(Context context) {
        try { return RadarPreferences.load(context) == null ? State.SETUP_REQUIRED : State.READY; }
        catch (IllegalStateException invalid) { return State.UNAVAILABLE; }
    }
    @Override public String message(Context context) { return "Choose a supported NWS region or station. Complete still images retain the source legend and printed product time. This widget is not an alert service."; }
    @Override public int widthDp() { return 520; }
    @Override public int heightDp() { return 420; }
    @Override public View createView(Context context) { return new RadarView(context, false); }
    @Override public void configure(Activity activity) { RadarSettings.show(activity); }
    @Override public void onStart(View view) { if (view instanceof RadarView) ((RadarView) view).start(); }
    @Override public void onStop(View view) { if (view instanceof RadarView) ((RadarView) view).stop(); }
}
