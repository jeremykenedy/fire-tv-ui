package com.jeremykenedy.firetv.ui.nativeapp.widget.calendar;

import android.content.Context;
import android.graphics.Color;
import android.widget.TextView;

public final class CalendarView extends TextView {
    private CalendarLoader loader;
    public CalendarView(Context context) {
        super(context); setTextSize(19); setTextColor(Color.WHITE); setMaxLines(8);
        int pad = Math.round(16 * context.getResources().getDisplayMetrics().density); setPadding(pad, pad, pad, pad);
        setText("Calendar\nOpen settings to select calendars.");
    }
    void start() {
        if (loader != null) return;
        setText("Calendar\nChecking selected calendars…");
        loader = new CalendarLoader(getContext(), new CalendarLoader.Listener() {
            @Override public void onCalendar(CalendarOptions options, CalendarData.Result result) {
                render(options, result);
            }
        }); loader.start();
    }
    void render(CalendarOptions options, CalendarData.Result result) {
        setText("Calendar\n" + (options == null ? "Saved calendar choices are unavailable. Stored data has been kept." : result.homeText(options, System.currentTimeMillis())));
    }
    void stop() {
        if (loader != null) { loader.close(); loader = null; }
        setText("Calendar\nPaused while Home is not visible.");
    }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
}
