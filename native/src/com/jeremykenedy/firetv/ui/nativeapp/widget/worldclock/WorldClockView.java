package com.jeremykenedy.firetv.ui.nativeapp.widget.worldclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.text.format.DateFormat;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

public final class WorldClockView extends LinearLayout {
    private boolean started;
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { render(); }
    };

    public WorldClockView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setLayoutDirection(LAYOUT_DIRECTION_LOCALE);
        setGravity(Gravity.CENTER_VERTICAL);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        setPadding(padding, padding, padding, padding);
        render();
    }

    public void start() {
        stop();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        getContext().registerReceiver(changes, filter);
        started = true;
        render();
    }

    public void stop() {
        if (started) {
            getContext().unregisterReceiver(changes);
            started = false;
        }
    }

    public boolean isRefreshing() { return started; }

    @Override protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private void render() {
        removeAllViews();
        try {
            WorldClocks clocks = WorldClockPreferences.read(getContext());
            if (clocks.entries.isEmpty()) {
                text("World clocks", 24, Color.WHITE);
                text("Choose time zones in Widgets settings.", 18, 0xffc8bfdc);
                return;
            }
            Locale locale = getResources().getConfiguration().getLocales().get(0);
            String time = DateFormat.getBestDateTimePattern(locale,
                    DateFormat.is24HourFormat(getContext()) ? "Hm" : "hm");
            String date = DateFormat.getBestDateTimePattern(locale, "EEE MMM d");
            long now = System.currentTimeMillis();
            for (WorldClocks.Entry entry : clocks.entries) {
                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(HORIZONTAL);
                TextView label = label(entry.label + "  ", 24, Color.WHITE);
                row.addView(label, new LayoutParams(0, -2, 1));
                TextView clock = label(WorldClocks.format(entry, now, locale, time), 24, Color.WHITE);
                row.addView(clock, new LayoutParams(-2, -2));
                row.setContentDescription(entry.label + ", " + clock.getText() + ", " + entry.zone);
                addView(row, new LayoutParams(-1, -2));
                text(WorldClocks.format(entry, now, locale, date) + "  " + entry.zone, 15, 0xffc8bfdc);
            }
        } catch (IllegalArgumentException error) {
            text("World clocks unavailable", 24, Color.WHITE);
            text("Review saved clocks in Widgets settings.", 18, 0xffc8bfdc);
        }
    }

    private void text(String value, int size, int color) {
        addView(label(value, size, color), new LayoutParams(-1, -2));
    }

    private TextView label(String value, int size, int color) {
        TextView row = new TextView(getContext());
        row.setText(value);
        row.setTextSize(size);
        row.setTextColor(color);
        row.setTextDirection(TEXT_DIRECTION_LOCALE);
        row.setSingleLine(true);
        row.setEllipsize(TextUtils.TruncateAt.END);
        return row;
    }
}
