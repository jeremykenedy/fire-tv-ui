package com.jeremykenedy.firetv.ui.nativeapp.widget.calendar;

import android.content.Context;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.OperationCanceledException;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class CalendarLoader {
    interface Listener { void onCalendar(CalendarOptions options, CalendarData.Result result); }
    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ThreadPoolExecutor worker;
    private CancellationSignal signal;
    private Future<?> pending;
    private boolean active;
    private int generation;
    private final Runnable refresh = new Runnable() { @Override public void run() { load(); } };
    private final Runnable timeout = new Runnable() {
        @Override public void run() {
            if (!active) return;
            cancel(); generation++;
            try { deliver(CalendarPreferences.load(context), CalendarSource.result(CalendarData.State.UNAVAILABLE,
                    Collections.<CalendarData.Calendar>emptyList(), Collections.<CalendarData.Event>emptyList(), System.currentTimeMillis())); }
            catch (IllegalStateException ignored) { }
            main.postDelayed(refresh, 60000);
        }
    };
    CalendarLoader(Context context, Listener listener) { this.context = context.getApplicationContext(); this.listener = listener; }
    void start() {
        if (active) return; active = true;
        if (worker == null) worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(1));
        load();
    }
    void load() {
        if (!active) return;
        main.removeCallbacks(refresh); main.removeCallbacks(timeout); cancel();
        final CalendarOptions options;
        try { options = CalendarPreferences.load(context); }
        catch (IllegalStateException error) {
            listener.onCalendar(null, CalendarSource.result(CalendarData.State.UNAVAILABLE, Collections.<CalendarData.Calendar>emptyList(), Collections.<CalendarData.Event>emptyList(), System.currentTimeMillis())); return;
        }
        final int request = ++generation; final CancellationSignal requestSignal = new CancellationSignal(); signal = requestSignal;
        worker.purge();
        try {
            pending = worker.submit(new Runnable() {
                @Override public void run() {
                    try {
                        final CalendarData.Result result = CalendarSource.read(context, options, System.currentTimeMillis(), requestSignal);
                        main.post(new Runnable() {
                            @Override public void run() {
                                if (!active || request != generation || requestSignal.isCanceled()) return;
                                main.removeCallbacks(timeout); deliver(options, result); main.postDelayed(refresh, 60000);
                            }
                        });
                    } catch (OperationCanceledException canceled) { }
                }
            });
            main.postDelayed(timeout, 5000);
        } catch (java.util.concurrent.RejectedExecutionException unavailable) {
            deliver(options, CalendarSource.result(CalendarData.State.UNAVAILABLE, Collections.<CalendarData.Calendar>emptyList(), Collections.<CalendarData.Event>emptyList(), System.currentTimeMillis()));
            main.postDelayed(refresh, 60000);
        }
    }
    private void deliver(CalendarOptions options, CalendarData.Result result) { listener.onCalendar(options, result); }
    void stop() {
        active = false; generation++; main.removeCallbacks(refresh); main.removeCallbacks(timeout); cancel();
    }
    void close() { stop(); if (worker != null) { worker.shutdownNow(); worker = null; } }
    private void cancel() { if (signal != null) signal.cancel(); if (pending != null) pending.cancel(true); signal = null; pending = null; }
}
