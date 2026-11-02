package com.jeremykenedy.firetv.ui.nativeapp.widget.uv;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.CancellationException;

public final class UvView extends LinearLayout {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final UvRepository repository = new UvRepository();
    private final boolean full;
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(2));
    static { WORKER.allowCoreThreadTimeOut(true); }
    private Future<?> task;
    private UvRequest request;
    private Runnable delivery;
    private volatile int generation;
    private volatile boolean started;
    private final Runnable refresh = new Runnable() { @Override public void run() { load(); } };

    public UvView(Context context) { this(context, false); }
    UvView(Context context, boolean full) {
        super(context);
        this.full = full;
        setOrientation(VERTICAL); setLayoutDirection(LAYOUT_DIRECTION_LOCALE);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        setPadding(padding, padding, padding, padding);
        text("UV Index forecast", 24, Color.WHITE);
    }
    public void start() {
        if (started) return;
        stop();
        started = true;
        load();
    }
    public synchronized void stop() {
        started = false; generation++;
        ui.removeCallbacks(refresh);
        if (delivery != null) ui.removeCallbacks(delivery);
        if (request != null) request.cancel();
        if (task != null) { task.cancel(true); WORKER.remove((Runnable) task); }
        delivery = null; request = null; task = null;
    }
    public boolean isRefreshing() { return started; }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
    private void load() {
        if (!started) return;
        final String zip;
        try { zip = UvPreferences.read(getContext()); }
        catch (IllegalArgumentException invalid) { message("Review the ZIP code in Widgets settings."); return; }
        if (zip == null) { message("Choose a ZIP code in Widgets settings."); return; }
        final int expected = generation;
        final UvRequest fetch = new UvRequest();
        request = fetch;
        message("Loading EPA forecast for " + zip + "...");
        try { task = WORKER.submit(new Runnable() {
            @Override public void run() {
                final UvRepository.Snapshot result;
                try { result = repository.load(zip, fetch, System.currentTimeMillis()); }
                catch (CancellationException canceled) { return; }
                final Runnable apply = new Runnable() {
                    @Override public void run() {
                        if (!started || generation != expected) return;
                        render(result.at(System.currentTimeMillis()), zip);
                        ui.postDelayed(refresh, UvRepository.REFRESH);
                    }
                };
                synchronized (UvView.this) {
                    if (!started || generation != expected) return;
                    delivery = apply; ui.post(apply);
                }
            }
        }); } catch (RejectedExecutionException pending) {
            message("A previous UV request is finishing. Retrying shortly.");
            ui.postDelayed(refresh, 30000);
        }
    }
    private void message(String message) {
        removeAllViews(); text("UV Index forecast", 24, Color.WHITE); text(message, 18, 0xffc8bfdc);
    }
    private void render(UvRepository.Snapshot result, String zip) {
        removeAllViews(); text("UV Index forecast", 24, Color.WHITE);
        text("EPA  ·  ZIP " + zip + "  ·  Local times", 16, 0xffc8bfdc);
        if (result.forecast == null || result.forecast.hours.isEmpty()) { text(result.message, 18, Color.WHITE); return; }
        if (result.state == UvRepository.State.STALE) text(result.message, 17, 0xffffca75);
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        DateTimeFormatter format = DateTimeFormatter.ofPattern(DateFormat.is24HourFormat(getContext())
                ? "MMM d, uuuu HH:mm" : "MMM d, uuuu h:mm a", locale);
        int limit = full ? UvForecast.MAX_ROWS : 6;
        for (int i = 0; i < result.forecast.hours.size() && i < limit; i++) {
            UvForecast.Hour hour = result.forecast.hours.get(i);
            text(hour.localTime.format(format) + "   UV " + UvForecast.value(hour.index, locale), 18, Color.WHITE);
        }
        if (!full && result.forecast.hours.size() > limit) text("More hours in widget settings", 15, 0xffc8bfdc);
    }
    private void text(String value, int size, int color) {
        TextView text = new TextView(getContext());
        text.setText(value); text.setTextSize(size); text.setTextColor(color); text.setTextDirection(TEXT_DIRECTION_LOCALE);
        addView(text, new LayoutParams(-1, -2));
    }
}
