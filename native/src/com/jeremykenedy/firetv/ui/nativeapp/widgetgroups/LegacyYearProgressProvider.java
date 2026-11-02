package com.jeremykenedy.firetv.ui.nativeapp.widgetgroups;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;

final class LegacyYearProgressProvider implements WidgetProvider {
    @Override public LayoutItem item() { return new LayoutItem("legacy-year-progress", LayoutItem.Kind.WIDGET); }
    @Override public String title() { return "Year"; }
    @Override public State state(Context context) { return State.READY; }
    @Override public String message(Context context) { return "Year progress uses the TV date and time zone."; }
    @Override public int widthDp() { return 320; }
    @Override public int heightDp() { return 300; }
    @Override public View createView(Context context) { return new LegacyYearProgressView(context); }
    @Override public void configure(Activity activity) { }
    @Override public void onStart(View view) { ((LegacyYearProgressView) view).start(); }
    @Override public void onStop(View view) { ((LegacyYearProgressView) view).stop(); }
}
