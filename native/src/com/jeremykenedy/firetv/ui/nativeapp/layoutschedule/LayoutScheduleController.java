package com.jeremykenedy.firetv.ui.nativeapp.layoutschedule;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Resolves the effective dashboard while Home is visible. It never writes layout data. */
public final class LayoutScheduleController {
    public interface Listener {
        void onDashboard(LayoutSchedule.Decision decision);
        void onDashboardUnavailable();
    }

    private final Context context;
    private final LayoutScheduleStore store;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    private boolean active;
    private int generation;
    private final Runnable update = new Runnable() {
        @Override public void run() { refresh(); }
    };
    private final BroadcastReceiver timeChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { refresh(); }
    };

    public LayoutScheduleController(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.store = new LayoutScheduleStore(this.context);
        this.listener = listener;
    }

    public void onResume() {
        if (active) return;
        active = true;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(timeChanged, filter, Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(timeChanged, filter);
        refresh();
    }

    public void onPause() {
        if (!active) return;
        active = false;
        generation++;
        handler.removeCallbacks(update);
        context.unregisterReceiver(timeChanged);
    }

    public void close() {
        onPause();
        reader.shutdownNow();
    }

    /** Call after returning from dashboard settings or another layout editor. */
    public void refresh() {
        handler.removeCallbacks(update);
        if (!active) return;
        final int request = ++generation;
        reader.execute(new Runnable() {
            @Override public void run() {
                try {
                    final LayoutSchedule.Decision decision = store.resolve(System.currentTimeMillis());
                    handler.post(new Runnable() {
                        @Override public void run() {
                            if (!active || request != generation) return;
                            listener.onDashboard(decision);
                            if (decision.nextCheckMillis != Long.MAX_VALUE) {
                                long delay = Math.max(1, decision.nextCheckMillis - System.currentTimeMillis());
                                handler.postDelayed(update, delay);
                            }
                        }
                    });
                } catch (Exception unavailable) {
                    handler.post(new Runnable() {
                        @Override public void run() {
                            if (active && request == generation) listener.onDashboardUnavailable();
                        }
                    });
                }
            }
        });
    }
}
