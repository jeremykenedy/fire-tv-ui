package com.jeremykenedy.firetv.ui.nativeapp.widget.history;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class HistoryView extends LinearLayout {
    private static final ThreadPoolExecutor REQUESTS =
            new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(2));

    static {
        REQUESTS.allowCoreThreadTimeOut(true);
    }

    private final HistoryRepository repository;
    private final TextView status, event, credit;
    private final boolean detail;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BroadcastReceiver changed =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent i) {
                    refresh();
                }
            };
    private final Runnable tick =
            new Runnable() {
                @Override
                public void run() {
                    if (started) {
                        if (shown == null
                                || !shown.equals(LocalDate.now(ZoneId.systemDefault()))
                                || !repository.hasFresh(shown)) refresh();
                        handler.postDelayed(this, 60000L);
                    }
                }
            };
    private Future<?> task;
    private int generation;
    private boolean started, registered;
    private LocalDate shown;

    HistoryView(Context context, boolean detail) {
        this(context, detail, HistoryRepository.SHARED);
    }

    HistoryView(Context context, boolean detail, HistoryRepository repository) {
        super(context);
        this.repository = repository;
        this.detail = detail;
        setOrientation(VERTICAL);
        setGravity(detail ? Gravity.CENTER_VERTICAL : Gravity.TOP);
        setPadding(dp(16), dp(16), dp(16), dp(16));
        setTag("history:on-this-day");
        text("On This Day", 20, 1);
        status = text("History is loading.", 16, detail ? 2 : 1);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        event = text("", 19, detail ? Integer.MAX_VALUE : 2);
        if (!detail) event.setLayoutParams(new LayoutParams(-1, 0, 1));
        credit = text("Wikipedia contributors · CC BY-SA 4.0", 14, detail ? Integer.MAX_VALUE : 2);
        credit.setTextColor(0xffb8d2da);
    }

    private TextView text(String value, int size, int lines) {
        TextView v = new TextView(getContext());
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(0xfff1f6f8);
        v.setMaxLines(lines);
        if (lines != Integer.MAX_VALUE) v.setEllipsize(TextUtils.TruncateAt.END);
        v.setPadding(0, dp(3), 0, dp(3));
        addView(v, new LayoutParams(-1, -2));
        return v;
    }

    void start() {
        if (started) return;
        started = true;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33)
            getContext().registerReceiver(changed, filter, Context.RECEIVER_NOT_EXPORTED);
        else getContext().registerReceiver(changed, filter);
        registered = true;
        tick.run();
    }

    private void refresh() {
        if (!started) return;
        final LocalDate date = LocalDate.now(ZoneId.systemDefault());
        if (shown != null && (!shown.equals(date) || !repository.hasFresh(date))) {
            event.setText("");
            shown = null;
        }
        if (detail)
            credit.setText(
                    "Wikipedia contributors. Plain-text excerpt. CC BY-SA 4.0.\nSource: "
                            + HistoryRepository.sourceUrl(date)
                            + "\nHistory and contributors: "
                            + HistoryRepository.sourceUrl(date)
                            + "?action=history\nLicense: "
                            + HistoryRepository.LICENSE);
        if (task != null && !task.isDone()) return;
        final int token = ++generation;
        status.setText("Loading history for " + HistoryRepository.pageTitle(date) + ".");
        try {
            task =
                    REQUESTS.submit(
                            new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        final HistoryRepository.Event item = repository.get(date);
                                        handler.post(
                                                new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        if (!started || token != generation) return;
                                                        task = null;
                                                        if (!date.equals(
                                                                LocalDate.now(
                                                                        ZoneId.systemDefault()))) {
                                                            shown = null;
                                                            event.setText("");
                                                            refresh();
                                                            return;
                                                        }
                                                        shown = date;
                                                        status.setText(
                                                                HistoryRepository.pageTitle(date)
                                                                        + " · "
                                                                        + ZoneId.systemDefault()
                                                                                .getId());
                                                        event.setText(item.line);
                                                    }
                                                });
                                    } catch (IOException | RuntimeException failure) {
                                        handler.post(
                                                new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        if (!started || token != generation) return;
                                                        task = null;
                                                        shown = null;
                                                        event.setText("");
                                                        if (!date.equals(
                                                                LocalDate.now(
                                                                        ZoneId.systemDefault()))) {
                                                            refresh();
                                                            return;
                                                        }
                                                        status.setText(
                                                                "History unavailable. Check the"
                                                                    + " connection and try again"
                                                                    + " later.");
                                                    }
                                                });
                                    }
                                }
                            });
        } catch (RejectedExecutionException busy) {
            shown = null;
            event.setText("");
            status.setText("History unavailable. Try again later.");
        }
    }

    void stop() {
        started = false;
        generation++;
        handler.removeCallbacks(tick);
        if (task != null) {
            task.cancel(true);
            if (task instanceof Runnable) REQUESTS.remove((Runnable) task);
            task = null;
        }
        if (registered) {
            getContext().unregisterReceiver(changed);
            registered = false;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }
}
