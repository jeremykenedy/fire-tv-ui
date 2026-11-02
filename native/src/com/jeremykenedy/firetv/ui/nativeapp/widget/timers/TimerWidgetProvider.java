package com.jeremykenedy.firetv.ui.nativeapp.widget.timers;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;

public final class TimerWidgetProvider implements WidgetProvider {
    @Override public LayoutItem item() { return new LayoutItem("timer", LayoutItem.Kind.WIDGET); }
    @Override public String title() { return "Timer"; }
    @Override public State state(Context context) { return TimerPreferences.timer(context) == null ? State.EMPTY : State.READY; }
    @Override public String message(Context context) { return "Local timer. Optional sound plays only when Home is visible. Standby alarms are unsupported; the TV will not wake."; }
    @Override public int widthDp() { return 360; }
    @Override public int heightDp() { return 220; }
    @Override public View createView(Context context) { return new LocalTimeWidgetView(context, true); }
    @Override public void configure(Activity activity) { TimerDialogs.timerActions(activity); }
    @Override public void onStart(View view) { ((LocalTimeWidgetView) view).start(); }
    @Override public void onStop(View view) { ((LocalTimeWidgetView) view).stop(); }
}
