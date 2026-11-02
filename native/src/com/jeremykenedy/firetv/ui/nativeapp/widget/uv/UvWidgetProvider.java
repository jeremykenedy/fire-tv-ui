package com.jeremykenedy.firetv.ui.nativeapp.widget.uv;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;

public final class UvWidgetProvider implements WidgetProvider {
    public static final LayoutItem ITEM = new LayoutItem("uv-forecast", LayoutItem.Kind.WIDGET);
    @Override public LayoutItem item() { return ITEM; }
    @Override public String title() { return "UV forecast"; }
    @Override public State state(Context context) {
        try { return UvPreferences.read(context) == null ? State.SETUP_REQUIRED : State.READY; }
        catch (IllegalArgumentException invalid) { return State.UNAVAILABLE; }
    }
    @Override public String message(Context context) { return "EPA hourly UV forecast for a manually selected U.S. ZIP code."; }
    @Override public int widthDp() { return 460; }
    @Override public int heightDp() { return 320; }
    @Override public View createView(Context context) { return new UvView(context); }
    @Override public void configure(Activity activity) { UvSettings.show(activity); }
    @Override public void onStart(View view) { ((UvView) view).start(); }
    @Override public void onStop(View view) { ((UvView) view).stop(); }
}
