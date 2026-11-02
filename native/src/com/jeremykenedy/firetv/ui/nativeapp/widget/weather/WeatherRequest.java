package com.jeremykenedy.firetv.ui.nativeapp.widget.weather;

import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class WeatherRequest {
    private static final ThreadPoolExecutor CLOSER = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(4), new ThreadFactory() {
        @Override public Thread newThread(Runnable action) { Thread thread = new Thread(action, "weather-cancel"); thread.setDaemon(true); return thread; }
    }, new ThreadPoolExecutor.DiscardPolicy());
    static { CLOSER.allowCoreThreadTimeOut(true); }
    private final AtomicBoolean canceled = new AtomicBoolean();
    private final AtomicReference<HttpURLConnection> connection = new AtomicReference<>();
    private final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
    void check() { if (canceled.get() || Thread.currentThread().isInterrupted()) throw new CancellationException(); }
    int timeoutMillis() throws SocketTimeoutException {
        check(); long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (remaining <= 0) throw new SocketTimeoutException("Weather request deadline exceeded.");
        return (int) Math.max(1, Math.min(5000, remaining));
    }
    void attach(HttpURLConnection value) { connection.set(value); if (canceled.get()) close(value); }
    void detach(HttpURLConnection value) { connection.compareAndSet(value, null); }
    void cancel() {
        canceled.set(true);
        HttpURLConnection value = connection.getAndSet(null);
        if (value != null) close(value);
    }
    private void close(final HttpURLConnection value) { CLOSER.execute(new Runnable() { @Override public void run() { value.disconnect(); } }); }
}
