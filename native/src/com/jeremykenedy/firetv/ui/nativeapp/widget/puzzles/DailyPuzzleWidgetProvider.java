package com.jeremykenedy.firetv.ui.nativeapp.widget.puzzles;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;

public final class DailyPuzzleWidgetProvider implements WidgetProvider {
    @Override public LayoutItem item() { return new LayoutItem("daily-puzzle", LayoutItem.Kind.WIDGET); }
    @Override public String title() { return "Daily puzzle"; }
    @Override public State state(Context context) {
        try { PuzzlePreferences.read(context, PuzzlePreferences.today()); return State.READY; }
        catch (IllegalArgumentException error) { return State.UNAVAILABLE; }
    }
    @Override public String message(Context context) { return "A local daily lights puzzle. Select to play. Uses the TV's local date; progress stays on this TV."; }
    @Override public int widthDp() { return 360; }
    @Override public int heightDp() { return 200; }
    @Override public View createView(Context context) { return new PuzzleWidgetView(context); }
    @Override public void configure(Activity activity) { PuzzleDialog.show(activity); }
    @Override public void onStart(View view) { ((PuzzleWidgetView) view).start(); }
    @Override public void onStop(View view) { ((PuzzleWidgetView) view).stop(); }
}
