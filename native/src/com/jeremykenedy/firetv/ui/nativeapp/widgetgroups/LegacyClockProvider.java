package com.jeremykenedy.firetv.ui.nativeapp.widgetgroups;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;

final class LegacyClockProvider implements WidgetProvider {
    @Override public LayoutItem item() { return new LayoutItem("legacy-clock", LayoutItem.Kind.WIDGET); }
    @Override public String title() { return "Clock"; }
    @Override public State state(Context context) { return State.READY; }
    @Override public String message(Context context) { return "Uses the TV date, time zone and saved clock format."; }
    @Override public int widthDp() { return 212; }
    @Override public int heightDp() { return 270; }
    @Override public View createView(Context context) { return new LegacyClockView(context); }
    @Override public void configure(Activity activity) { }
    @Override public void onStart(View view) { ((LegacyClockView) view).start(); }
    @Override public void onStop(View view) { ((LegacyClockView) view).stop(); }
}
