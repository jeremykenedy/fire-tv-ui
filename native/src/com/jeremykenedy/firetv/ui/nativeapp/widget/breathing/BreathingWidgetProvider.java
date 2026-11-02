package com.jeremykenedy.firetv.ui.nativeapp.widget.breathing;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;

public final class BreathingWidgetProvider implements WidgetProvider {
    @Override public LayoutItem item() { return new LayoutItem("breathing-break", LayoutItem.Kind.WIDGET); }
    @Override public String title() { return "Breathing break"; }
    @Override public State state(Context context) {
        try { BreathingPreferences.read(context); return State.READY; }
        catch (IllegalArgumentException error) { return State.UNAVAILABLE; }
    }
    @Override public String message(Context context) { return "Optional silent paced animation. Select to start or adjust cadence. Pauses when the widget leaves view."; }
    @Override public int widthDp() { return 360; }
    @Override public int heightDp() { return 280; }
    @Override public View createView(Context context) { return new BreathingWidgetView(context); }
    @Override public void configure(Activity activity) { BreathingDialogs.show(activity); }
    @Override public void onStart(View view) { ((BreathingWidgetView) view).start(); }
    @Override public void onStop(View view) { ((BreathingWidgetView) view).stop(); }
}
