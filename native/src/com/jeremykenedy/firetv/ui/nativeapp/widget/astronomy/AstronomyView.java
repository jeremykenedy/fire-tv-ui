package com.jeremykenedy.firetv.ui.nativeapp.widget.astronomy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class AstronomyView extends LinearLayout {
    private boolean started;
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { render(); }
    };
    public AstronomyView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setLayoutDirection(LAYOUT_DIRECTION_LOCALE);
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
        if (started) { getContext().unregisterReceiver(changes); started = false; }
    }
    public boolean isRefreshing() { return started; }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }

    private void render() {
        removeAllViews();
        text("Sun and moon", 24, Color.WHITE);
        try {
            AstronomyLocation location = AstronomyPreferences.read(getContext());
            if (location == null) { text("Set a location in Widgets settings.", 18, 0xffc8bfdc); return; }
            long now = System.currentTimeMillis();
            Locale locale = getResources().getConfiguration().getLocales().get(0);
            TimeZone zone = TimeZone.getTimeZone(location.zone);
            Astronomy.SunDay sun = Astronomy.sunDay(now, location.latitude, location.longitude, zone);
            Astronomy.Moon moon = Astronomy.moon(now);
            text(location.label, 20, Color.WHITE);
            String datePattern = DateFormat.getBestDateTimePattern(locale, "EEE MMM d");
            text(format(now, zone, locale, datePattern) + "  " + location.zone, 15, 0xffc8bfdc);
            String pattern = DateFormat.getBestDateTimePattern(locale, DateFormat.is24HourFormat(getContext()) ? "Hm" : "hm");
            text("Sunrise  " + event(sun.sunrise, sun.state, zone, locale, pattern), 20, Color.WHITE);
            text("Sunset  " + event(sun.sunset, sun.state, zone, locale, pattern), 20, Color.WHITE);
            if (sun.state == Astronomy.SunState.ABOVE_HORIZON) text("Sun stays above the horizon today", 16, 0xffc8bfdc);
            else if (sun.state == Astronomy.SunState.BELOW_HORIZON) text("Sun stays below the horizon today", 16, 0xffc8bfdc);
            else if (sun.state == Astronomy.SunState.TRANSITION) text("Near polar transition; some events unavailable", 15, 0xffc8bfdc);
            text(moon.phase(), 20, Color.WHITE);
            text(String.format(locale, "Moon approximately %d%% illuminated", Math.round(moon.illumination * 100)), 17, 0xffc8bfdc);
            text("Calculated times; clear sea-level horizon", 15, 0xffc8bfdc);
        } catch (IllegalArgumentException error) {
            text("Calculations unavailable", 20, Color.WHITE);
            text("Review location and TV date in Widgets settings.", 16, 0xffc8bfdc);
        }
    }
    private static String event(Long when, Astronomy.SunState state, TimeZone zone, Locale locale, String pattern) {
        return when == null ? (state == Astronomy.SunState.TRANSITION ? "Unavailable" : "No event today")
                : format(when, zone, locale, pattern);
    }
    private static String format(long when, TimeZone zone, Locale locale, String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, locale);
        format.setTimeZone(zone);
        return format.format(new Date(when));
    }
    private void text(String value, int size, int color) {
        TextView text = new TextView(getContext());
        text.setText(value); text.setTextSize(size); text.setTextColor(color);
        text.setSingleLine(true); text.setEllipsize(TextUtils.TruncateAt.END); text.setTextDirection(TEXT_DIRECTION_LOCALE);
        addView(text, new LayoutParams(-1, -2));
    }
}
