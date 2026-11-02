package com.jeremykenedy.firetv.ui.nativeapp.widget.timers;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;

public final class CountdownWidgetProvider implements WidgetProvider {
    @Override public LayoutItem item() { return new LayoutItem("countdown", LayoutItem.Kind.WIDGET); }
    @Override public String title() { return "Countdown"; }
    @Override public State state(Context context) { return TimerPreferences.countdown(context) == null ? State.SETUP_REQUIRED : State.READY; }
    @Override public String message(Context context) { return "Choose a label, date, 24-hour time and time zone. Past targets show Target reached."; }
    @Override public int widthDp() { return 360; }
    @Override public int heightDp() { return 220; }
    @Override public View createView(Context context) { return new LocalTimeWidgetView(context, false); }
    @Override public void configure(Activity activity) { TimerDialogs.countdown(activity); }
    @Override public void onStart(View view) { ((LocalTimeWidgetView) view).start(); }
    @Override public void onStop(View view) { ((LocalTimeWidgetView) view).stop(); }
}
