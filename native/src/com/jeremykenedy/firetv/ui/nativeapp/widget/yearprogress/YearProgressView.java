package com.jeremykenedy.firetv.ui.nativeapp.widget.yearprogress;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;

public final class YearProgressView extends LinearLayout {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TextView heading, progress, remaining;
    private final ProgressBar bar;
    private boolean started;
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { refresh(); }
    };
    private final Runnable midnight = new Runnable() {
        @Override public void run() { if (started) refresh(); }
    };
    public YearProgressView(Context context) {
        super(context);
        setOrientation(VERTICAL); setGravity(Gravity.CENTER_VERTICAL); setLayoutDirection(LAYOUT_DIRECTION_LOCALE);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        setPadding(padding, padding, padding, padding);
        setTag("year-progress:view");
        heading = text(20, "year-progress:year");
        progress = text(34, "year-progress:percentage");
        bar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000); addView(bar, new LayoutParams(-1, -2));
        remaining = text(17, "year-progress:remaining");
        render();
    }
    private TextView text(int size, String tag) {
        TextView view = new TextView(getContext()); view.setTag(tag); view.setTextSize(size);
        view.setTextColor(0xfff1f6f8); view.setTextDirection(TEXT_DIRECTION_LOCALE);
        addView(view, new LayoutParams(-1, -2)); return view;
    }
    void start() {
        if (started) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_DATE_CHANGED); filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED); filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) getContext().registerReceiver(changes, filter, Context.RECEIVER_NOT_EXPORTED);
        else getContext().registerReceiver(changes, filter);
        started = true; refresh();
    }
    void stop() {
        handler.removeCallbacks(midnight);
        if (started) { getContext().unregisterReceiver(changes); started = false; }
    }
    boolean isStarted() { return started; }
    private void refresh() {
        handler.removeCallbacks(midnight); render();
        if (started) handler.postDelayed(midnight, YearProgress.untilNextDay(Instant.now(), ZoneId.systemDefault()));
    }
    private void render() {
        YearProgress value = YearProgress.at(Instant.now(), ZoneId.systemDefault());
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        NumberFormat percent = NumberFormat.getPercentInstance(locale); percent.setMaximumFractionDigits(1);
        NumberFormat number = NumberFormat.getIntegerInstance(locale); number.setGroupingUsed(false);
        heading.setText("Year progress " + number.format(value.year));
        progress.setText(percent.format(value.fraction()) + " completed");
        bar.setProgress((int) Math.round(value.fraction() * 1000));
        remaining.setText(number.format(value.remainingDays) + (value.remainingDays == 1 ? " day remaining, including today" : " days remaining, including today"));
    }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
}
