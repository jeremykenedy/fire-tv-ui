package com.jeremykenedy.firetv.ui.nativeapp.layout;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import com.jeremykenedy.firetv.ui.nativeapp.widget.ClockWidgetView;

public final class ClockWidgetProvider implements WidgetProvider {
    @Override public LayoutItem item() { return HomeSlots.CLOCK; }
    @Override public boolean defaultVisible() { return true; }
    @Override public String title() { return "Clock"; }
    @Override public State state(Context context) { return State.READY; }
    @Override public String message(Context context) { return "Uses this TV's time zone and an explicitly saved launcher time format when present. Otherwise it follows the TV's 12/24-hour setting."; }
    @Override public int widthDp() { return 320; }
    @Override public int heightDp() { return 160; }
    @Override public View createView(Context context) {
        ClockWidgetView view = new ClockWidgetView(context);
        view.useManagedLifecycle();
        return view;
    }
    @Override public void configure(Activity activity) {
        new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle(title()).setMessage(message(activity))
                .setPositiveButton("Done", null).show();
    }
    @Override public void onStart(View view) { ((ClockWidgetView) view).startUpdates(); }
    @Override public void onStop(View view) { ((ClockWidgetView) view).stopUpdates(); }
}
