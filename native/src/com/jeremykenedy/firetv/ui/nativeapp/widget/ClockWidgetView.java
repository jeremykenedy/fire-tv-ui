package com.jeremykenedy.firetv.ui.nativeapp.widget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextClock;
import java.util.Locale;

/** TextClock follows local time, timezone changes and the system 12/24-hour choice. */
public final class ClockWidgetView extends LinearLayout {
    public ClockWidgetView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        setPadding(padding, padding, padding, padding);
        TextClock time = new TextClock(context);
        time.setTextColor(Color.WHITE);
        time.setTextSize(44);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        addView(time, new LayoutParams(-1, -2));
        TextClock date = new TextClock(context);
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEE MMM d");
        date.setFormat12Hour(pattern);
        date.setFormat24Hour(pattern);
        date.setTextColor(0xffc8bfdc);
        date.setTextSize(18);
        addView(date, new LayoutParams(-1, -2));
    }
}
