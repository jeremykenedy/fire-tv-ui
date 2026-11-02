package com.jeremykenedy.firetv.ui.nativeapp.providers;

import android.content.Context;
import android.database.ContentObserver;
import android.media.tv.TvContract;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.OperationCanceledException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Main-thread lifecycle owner for bounded, cancellable TV provider snapshots. */
public final class RecommendationLoader implements AutoCloseable {
    public interface Listener { void onRecommendations(TvRecommendations.Result result); }
    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private CancellationSignal cancellation;
    private Future<?> pending;
    private boolean active;
    private boolean observing;
    private boolean closed;
    private int generation;
    private final Runnable reload = new Runnable() {
        @Override public void run() { refresh(); }
    };
    private final Runnable notificationChanged = new Runnable() {
        @Override public void run() {
            main.post(new Runnable() {
                @Override public void run() {
                    main.removeCallbacks(reload);
                    if (active) main.postDelayed(reload, 200);
                }
            });
        }
    };
    private final ContentObserver observer = new ContentObserver(main) {
        @Override public void onChange(boolean selfChange) {
            main.removeCallbacks(reload);
            if (active) main.postDelayed(reload, 200);
        }
    };

    public RecommendationLoader(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void start() {
        requireMain();
        if (closed) throw new IllegalStateException("Recommendation loader is closed.");
        if (active) return;
        active = true;
        NotificationRecommendations.addListener(notificationChanged);
        try {
            context.getContentResolver().registerContentObserver(TvContract.Channels.CONTENT_URI, true, observer);
            context.getContentResolver().registerContentObserver(TvContract.PreviewPrograms.CONTENT_URI, true, observer);
            context.getContentResolver().registerContentObserver(TvContract.WatchNextPrograms.CONTENT_URI, true, observer);
            observing = true;
        } catch (RuntimeException unavailable) {
            context.getContentResolver().unregisterContentObserver(observer);
        }
        refresh();
    }

    public void refresh() {
        requireMain();
        if (!active || closed) return;
        main.removeCallbacks(reload);
        cancel();
        final int request = ++generation;
        final CancellationSignal signal = new CancellationSignal();
        cancellation = signal;
        pending = worker.submit(new Runnable() {
            @Override public void run() {
                try {
                    final TvRecommendations.Result snapshot = RecommendationSources.read(context, signal);
                    main.post(new Runnable() {
                        @Override public void run() {
                            if (active && !closed && request == generation && !signal.isCanceled())
                                listener.onRecommendations(snapshot);
                        }
                    });
                } catch (OperationCanceledException canceled) { }
            }
        });
    }

    public void stop() {
        requireMain();
        active = false;
        NotificationRecommendations.removeListener(notificationChanged);
        generation++;
        main.removeCallbacks(reload);
        cancel();
        if (observing) context.getContentResolver().unregisterContentObserver(observer);
        observing = false;
    }

    @Override public void close() {
        requireMain();
        stop();
        closed = true;
        worker.shutdownNow();
    }

    private void cancel() {
        if (cancellation != null) cancellation.cancel();
        if (pending != null) pending.cancel(true);
        cancellation = null;
        pending = null;
    }
    private void requireMain() {
        if (Looper.myLooper() != main.getLooper()) throw new IllegalStateException("Use the main thread for recommendation lifecycle.");
    }
}
