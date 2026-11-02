package com.jeremykenedy.firetv.ui.nativeapp.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextClock;
import java.util.Locale;

/** Clock follows the TV time and an optional retained launcher format choice. */
public final class ClockWidgetView extends LinearLayout {
    private final TextClock time;
    private final TextClock date;
    private final SharedPreferences preferences;
    private final SharedPreferences.OnSharedPreferenceChangeListener changed =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override public void onSharedPreferenceChanged(SharedPreferences values, String key) {
                    if ("use_24h_clock".equals(key)) applyTimeFormat();
                }
            };
    public ClockWidgetView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        setPadding(padding, padding, padding, padding);
        preferences = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE);
        time = new TextClock(context);
        applyTimeFormat();
        time.setTextColor(Color.WHITE);
        time.setTextSize(44);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        addView(time, new LayoutParams(-1, -2));
        date = new TextClock(context);
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEE MMM d");
        date.setFormat12Hour(pattern);
        date.setFormat24Hour(pattern);
        date.setTextColor(0xffc8bfdc);
        date.setTextSize(18);
        addView(date, new LayoutParams(-1, -2));
    }
    public void setCompact() {
        setPadding(0, 0, 0, 0);
        date.setVisibility(GONE);
        time.setTextSize(18);
    }

    public CharSequence timeText() { return time.getText(); }

    private void applyTimeFormat() {
        try {
            if (preferences.contains("use_24h_clock")) {
                boolean use24 = preferences.getBoolean("use_24h_clock", false);
                String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), use24 ? "Hm" : "hm");
                time.setFormat12Hour(pattern);
                time.setFormat24Hour(pattern);
                return;
            }
        } catch (ClassCastException invalid) {
            // Keep malformed saved values intact and use the device format.
        }
        time.setFormat12Hour(null);
        time.setFormat24Hour(null);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        preferences.registerOnSharedPreferenceChangeListener(changed);
        applyTimeFormat();
    }

    @Override protected void onDetachedFromWindow() {
        preferences.unregisterOnSharedPreferenceChangeListener(changed);
        super.onDetachedFromWindow();
    }

}
