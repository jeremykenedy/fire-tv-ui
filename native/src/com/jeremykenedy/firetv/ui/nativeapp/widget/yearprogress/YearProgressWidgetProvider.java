package com.jeremykenedy.firetv.ui.nativeapp.widget.yearprogress;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;

public final class YearProgressWidgetProvider implements WidgetProvider {
    public static final LayoutItem ITEM = new LayoutItem("year-progress", LayoutItem.Kind.WIDGET);
    @Override public LayoutItem item() { return ITEM; }
    @Override public String title() { return "Year progress"; }
    @Override public State state(Context context) { return State.READY; }
    @Override public String message(Context context) { return "Completed calendar days and days remaining in the TV's local year."; }
    @Override public int widthDp() { return 360; }
    @Override public int heightDp() { return 200; }
    @Override public View createView(Context context) { return new YearProgressView(context); }
    @Override public void configure(Activity activity) { Toast.makeText(activity, "Year progress uses the TV date and time zone.", Toast.LENGTH_SHORT).show(); }
    @Override public void onStart(View view) { ((YearProgressView) view).start(); }
    @Override public void onStop(View view) { ((YearProgressView) view).stop(); }
}
