package com.jeremykenedy.firetv.ui.nativeapp.widget.worldclock;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import com.jeremykenedy.firetv.ui.nativeapp.layout.HomeSlots;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;

public final class WorldClockWidgetProvider implements WidgetProvider {
    @Override public LayoutItem item() { return HomeSlots.WORLD_CLOCKS; }
    @Override public String title() { return "World clocks"; }
    @Override public State state(Context context) {
        try { return WorldClockPreferences.read(context).entries.isEmpty() ? State.EMPTY : State.READY; }
        catch (IllegalArgumentException error) { return State.UNAVAILABLE; }
    }
    @Override public String message(Context context) {
        State state = state(context);
        if (state == State.EMPTY) return "Choose up to six time zones in World clocks settings.";
        if (state == State.UNAVAILABLE) return "Saved clocks could not be read. Review World clocks settings.";
        return "Uses your TV's language and 12/24-hour setting, with each zone's daylight-saving rules.";
    }
    @Override public int widthDp() { return 400; }
    @Override public int heightDp() { return 440; }
    @Override public View createView(Context context) { return new WorldClockView(context); }
    @Override public void configure(Activity activity) { WorldClockSettings.show(activity); }
    @Override public void onStart(View view) { ((WorldClockView) view).start(); }
    @Override public void onStop(View view) { ((WorldClockView) view).stop(); }
}
