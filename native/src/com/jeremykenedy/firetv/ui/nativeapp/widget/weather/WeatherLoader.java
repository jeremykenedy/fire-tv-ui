package com.jeremykenedy.firetv.ui.nativeapp.widget.weather;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class WeatherLoader {
    interface Listener { void onForecast(WeatherOptions options, WeatherForecast forecast); }
    private final Context context;
    private final Listener listener;
    private final WeatherRepository repository;
    private final WeatherOptions instanceOptions;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(1));
    private WeatherRequest request;
    private Future<?> pending;
    private boolean active;
    private int generation;
    private final Runnable refresh = new Runnable() { @Override public void run() { load(); } };
    private final Runnable timeout = new Runnable() {
        @Override public void run() {
            if (!active) return; cancel(); generation++;
            listener.onForecast(null, WeatherForecast.state(WeatherForecast.State.UNAVAILABLE,
                    "Forecast request timed out. Saved location choices have been kept.", System.currentTimeMillis()));
            main.postDelayed(refresh, 60000);
        }
    };
    WeatherLoader(Context context, Listener listener) {
        this(context, listener, new WeatherRepository(new File(context.getCacheDir(), "weather-hourly")), null);
    }
    WeatherLoader(Context context, Listener listener, WeatherRepository repository) {
        this(context, listener, repository, null);
    }
    WeatherLoader(Context context, Listener listener, WeatherRepository repository, WeatherOptions instanceOptions) {
        this.context = context.getApplicationContext(); this.listener = listener; this.repository = repository; this.instanceOptions = instanceOptions;
        worker.allowCoreThreadTimeOut(true);
    }
    void start() { if (!active) { active = true; load(); } }
    void load() {
        if (!active) return;
        main.removeCallbacks(refresh); main.removeCallbacks(timeout); cancel();
        final WeatherOptions options;
        try { options = instanceOptions == null ? WeatherPreferences.load(context) : instanceOptions; }
        catch (IllegalStateException invalid) {
            listener.onForecast(null, WeatherForecast.state(WeatherForecast.State.UNAVAILABLE, invalid.getMessage(), System.currentTimeMillis())); return;
        }
        final int current = ++generation; final WeatherRequest reading = new WeatherRequest(); request = reading;
        worker.purge();
        try {
            pending = worker.submit(new Runnable() {
                @Override public void run() {
                    try {
                        final WeatherForecast forecast = repository.read(options, System.currentTimeMillis(), reading);
                        main.post(new Runnable() { @Override public void run() {
                            if (!active || current != generation) return;
                            main.removeCallbacks(timeout); listener.onForecast(options, forecast);
                            if (options.configured) main.postDelayed(refresh, Math.max(60000, Math.min(3600000, forecast.refreshAt - System.currentTimeMillis())));
                        } });
                    } catch (CancellationException canceled) { }
                }
            });
            main.postDelayed(timeout, 20000);
        } catch (java.util.concurrent.RejectedExecutionException busy) {
            listener.onForecast(options, WeatherForecast.state(WeatherForecast.State.UNAVAILABLE,
                    "The previous forecast request is stopping. Refresh will retry shortly.", System.currentTimeMillis()));
            main.postDelayed(refresh, 60000);
        }
    }
    void close() { active = false; generation++; main.removeCallbacks(refresh); main.removeCallbacks(timeout); cancel(); worker.shutdownNow(); }
    private void cancel() { if (request != null) request.cancel(); if (pending != null) pending.cancel(true); request = null; pending = null; }
}
