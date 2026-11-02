package com.jeremykenedy.firetv.ui.nativeapp.widget.status;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Refresh work belongs to the visible view and never controls the observed device. */
public final class StatusWidgetView extends LinearLayout {
    public interface Reader { StatusReport read(Context context); }
    private final Reader reader;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final TextView state;
    private final TextView body;
    private boolean fullDetails;
    private ExecutorService worker;
    private Future<?> task;
    private Runnable delivery;
    private volatile int generation;
    private volatile boolean started;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { read(); }
    };

    public StatusWidgetView(Context context, String title, Reader reader) {
        super(context);
        this.reader = reader;
        setOrientation(VERTICAL);
        setLayoutDirection(LAYOUT_DIRECTION_LOCALE);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        setPadding(padding, padding, padding, padding);
        text(title, 24, Color.WHITE);
        state = text("Waiting for a fresh snapshot", 16, 0xffc8bfdc);
        body = text("", 18, Color.WHITE);
        body.setMaxLines(10);
        body.setEllipsize(TextUtils.TruncateAt.END);
    }

    public void start() {
        if (started) return;
        stop();
        state.setText("Reading a fresh snapshot...");
        body.setText("");
        started = true;
        worker = Executors.newSingleThreadExecutor();
        read();
    }
    public synchronized void stop() {
        started = false;
        generation++;
        ui.removeCallbacks(refresh);
        if (delivery != null) ui.removeCallbacks(delivery);
        if (task != null) task.cancel(true);
        if (worker != null) worker.shutdownNow();
        delivery = null; task = null; worker = null;
    }
    public void showFullDetails() {
        fullDetails = true;
        body.setMaxLines(Integer.MAX_VALUE);
        body.setEllipsize(null);
    }
    public boolean isRefreshing() { return started; }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }

    private void read() {
        if (!started) return;
        final int expected = generation;
        task = worker.submit(new Runnable() {
            @Override public void run() {
                StatusReport loaded;
                try { loaded = reader.read(getContext().getApplicationContext()); }
                catch (SecurityException denied) { loaded = new StatusReport(StatusReport.Availability.PERMISSION_DENIED, "Android denied access to this status."); }
                catch (RuntimeException unavailable) { loaded = new StatusReport(StatusReport.Availability.UNAVAILABLE, "Status could not be read on this device."); }
                final StatusReport report = loaded;
                final Runnable apply = new Runnable() {
                    @Override public void run() {
                        if (!started || generation != expected) return;
                        state.setText(report.label()); body.setText(fullDetails ? report.details : report.summary);
                        ui.postDelayed(refresh, 15000);
                    }
                };
                synchronized (StatusWidgetView.this) {
                    if (!started || generation != expected) return;
                    delivery = apply;
                    ui.post(apply);
                }
            }
        });
    }
    private TextView text(String value, int size, int color) {
        TextView text = new TextView(getContext());
        text.setText(value); text.setTextSize(size); text.setTextColor(color);
        text.setTextDirection(TEXT_DIRECTION_LOCALE);
        addView(text, new LayoutParams(-1, -2));
        return text;
    }
}
