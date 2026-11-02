package com.jeremykenedy.firetv.ui.nativeapp.widget.calendar;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;

public final class CalendarWidgetProvider implements WidgetProvider {
    public static final LayoutItem ITEM = new LayoutItem("calendar", LayoutItem.Kind.WIDGET);
    @Override public LayoutItem item() { return ITEM; }
    @Override public String title() { return "Calendar"; }
    @Override public State state(Context context) {
        try {
            if (!CalendarSource.available(context)) return State.UNAVAILABLE;
            if (!CalendarSource.permitted(context) || CalendarPreferences.load(context).selected.isEmpty()) return State.SETUP_REQUIRED;
            return State.READY;
        } catch (RuntimeException unreadable) { return State.UNAVAILABLE; }
    }
    @Override public String message(Context context) {
        try {
            if (!CalendarSource.available(context)) return "No supported Calendar provider is available on this TV. No account or sync service is assumed.";
            if (!CalendarSource.permitted(context)) return "Open Calendar settings to choose whether to allow read-only calendar access.";
            if (CalendarPreferences.load(context).selected.isEmpty()) return "Select the calendars this launcher may display. None are selected automatically.";
            return "Reads selected calendars while visible. Event details are hidden on Home unless you choose to show them.";
        } catch (RuntimeException unreadable) { return "Saved calendar choices could not be read. Stored data has been kept."; }
    }
    @Override public int widthDp() { return 440; }
    @Override public int heightDp() { return 260; }
    @Override public View createView(Context context) { return new CalendarView(context); }
    @Override public void configure(Activity activity) { activity.startActivity(new Intent(activity, CalendarActivity.class)); }
    @Override public void onStart(View view) { if (view instanceof CalendarView) ((CalendarView) view).start(); }
    @Override public void onStop(View view) { if (view instanceof CalendarView) ((CalendarView) view).stop(); }
}
