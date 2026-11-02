package com.jeremykenedy.firetv.ui.nativeapp.widget.reminders;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;

public final class RemindersWidgetProvider implements WidgetProvider {
    public static final LayoutItem ITEM = new LayoutItem("household-reminders", LayoutItem.Kind.WIDGET);
    @Override public LayoutItem item() { return ITEM; }
    @Override public String title() { return "Household reminders"; }
    @Override public State state(Context context) {
        try {
            RemindersOptions options = RemindersPreferences.load(context);
            return !options.enabled ? State.SETUP_REQUIRED : options.items.isEmpty() ? State.EMPTY : State.READY;
        } catch (IllegalStateException error) { return State.UNAVAILABLE; }
    }
    @Override public String message(Context context) {
        try {
            RemindersOptions options = RemindersPreferences.load(context);
            return !options.enabled ? "Disabled. Open to enable local reminders. No background alarms."
                    : options.items.isEmpty() ? "No reminders yet. Open to add one."
                    : "Schedules update while the launcher is open. No background alarms or TV wakeups.";
        } catch (IllegalStateException error) { return "Saved reminders could not be read. Stored data has been kept."; }
    }
    @Override public int widthDp() { return 400; }
    @Override public int heightDp() { return 220; }
    @Override public View createView(Context context) { return new ReminderView(context); }
    @Override public void configure(Activity activity) { activity.startActivity(new Intent(activity, RemindersActivity.class)); }
    @Override public void onStart(View view) { if (view instanceof ReminderView) ((ReminderView) view).start(); }
    @Override public void onStop(View view) { if (view instanceof ReminderView) ((ReminderView) view).stop(); }
    public static final class ReminderView extends TextView {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private boolean running;
        private final Runnable tick = new Runnable() {
            @Override public void run() { refresh(); if (running) handler.postDelayed(this, 30000); }
        };
        ReminderView(Context context) {
            super(context); setTextColor(Color.WHITE); setTextSize(20); setMaxLines(7);
            int pad = Math.round(16 * context.getResources().getDisplayMetrics().density); setPadding(pad, pad, pad, pad); refresh();
        }
        void start() { stop(); running = true; tick.run(); }
        void stop() { running = false; handler.removeCallbacks(tick); }
        @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
        private void refresh() {
            try { setText("Household reminders\n" + RemindersPreferences.load(getContext()).homeText(System.currentTimeMillis())); }
            catch (IllegalStateException error) { setText("Household reminders\nSaved data unavailable. Stored data has been kept."); }
        }
    }
}
