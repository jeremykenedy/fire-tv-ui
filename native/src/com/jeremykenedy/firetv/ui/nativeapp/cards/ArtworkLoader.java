package com.jeremykenedy.firetv.ui.nativeapp.cards;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.content.res.AssetFileDescriptor;
import android.os.CancellationSignal;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/** Shared bounded artwork work; requests never retain an Activity context. */
public final class ArtworkLoader {
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final int MAX_EDGE = 768;
    private static final int DEADLINE_MILLIS = 6000;
    private static final ScheduledThreadPoolExecutor DEADLINES = new ScheduledThreadPoolExecutor(1);
    private static final ThreadPoolExecutor CLEANUP = new ThreadPoolExecutor(2, 2, 30,
            TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(128));
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor WORK = new ThreadPoolExecutor(2, 2, 30,
            TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(64));
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getAllocationByteCount(); }
    };

    static { DEADLINES.setRemoveOnCancelPolicy(true); }

    public interface Callback { void onLoaded(Bitmap bitmap); }

    public static final class Request {
        private volatile boolean cancelled;
        private volatile boolean stopped;
        private final long deadline = SystemClock.elapsedRealtime() + DEADLINE_MILLIS;
        private final CancellationSignal signal = new CancellationSignal();
        private Callback callback;
        private Future<?> future;
        private ScheduledFuture<?> timeout;
        private Closeable stream;
        private HttpURLConnection connection;

        private Request(Callback callback) { this.callback = callback; }

        public void cancel() {
            synchronized (this) { cancelled = true; callback = null; }
            stop();
        }

        private void stop() {
            final Closeable closing;
            final HttpURLConnection disconnecting;
            synchronized (this) {
                stopped = true;
                if (future != null) future.cancel(true);
                if (timeout != null) timeout.cancel(false);
                closing = stream;
                stream = null;
                disconnecting = connection;
                connection = null;
            }
            // Provider cancellation and stream close may use Binder or network IO.
            // Never run them while the main thread is updating focus or lifecycle.
            try {
                CLEANUP.execute(new Runnable() {
                    @Override public void run() {
                        signal.cancel();
                        close(closing);
                        if (disconnecting != null) disconnecting.disconnect();
                    }
                });
            } catch (RejectedExecutionException busy) {
                // Work remains bounded; the IO loop also observes its deadline.
            }
            WORK.purge();
        }

        private boolean expired() {
            return stopped || Thread.currentThread().isInterrupted()
                    || SystemClock.elapsedRealtime() >= deadline;
        }

        private boolean track(Closeable value) {
            synchronized (this) {
                if (!expired()) { stream = value; return true; }
            }
            close(value);
            return false;
        }

        private boolean track(HttpURLConnection value) {
            synchronized (this) {
                if (!expired()) { connection = value; return true; }
            }
            value.disconnect();
            return false;
        }

        private void deliver(final Bitmap bitmap) {
            MAIN.post(new Runnable() {
                @Override public void run() {
                    Callback target;
                    synchronized (Request.this) {
                        target = cancelled ? null : callback;
                        callback = null;
                    }
                    if (target != null) target.onLoaded(bitmap);
                }
            });
        }

        private void release() {
            Closeable closing;
            HttpURLConnection disconnecting;
            synchronized (this) {
                if (timeout != null) timeout.cancel(false);
                closing = stream;
                stream = null;
                disconnecting = connection;
                connection = null;
            }
            close(closing);
            if (disconnecting != null) disconnecting.disconnect();
        }

    }

    private ArtworkLoader() {}

    public static Request load(Context context, final Uri uri, Callback callback) {
        return load(context, uri, MAX_EDGE, callback);
    }

    public static Request load(Context context, final Uri uri, int requestedEdge, Callback callback) {
        final int edge = Math.max(32, Math.min(MAX_EDGE, requestedEdge));
        final Request request = new Request(callback);
        if (uri == null || !allowed(uri)) {
            request.deliver(null);
            return request;
        }
        final String location = uri.toString();
        final String key = location + "\n" + edge;
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            request.deliver(cached);
            return request;
        }
        final Context application = context.getApplicationContext();
        request.timeout = DEADLINES.schedule(new Runnable() {
            @Override public void run() {
                request.deliver(null);
                request.stop();
            }
        }, DEADLINE_MILLIS, TimeUnit.MILLISECONDS);
        try {
            request.future = WORK.submit(new Runnable() {
                @Override public void run() {
                    Bitmap bitmap = null;
                    try {
                        if (request.expired()) return;
                        byte[] bytes;
                        if ("https".equals(uri.getScheme())) {
                            HttpURLConnection connection = (HttpURLConnection) new URL(location).openConnection();
                            if (!request.track(connection)) return;
                            connection.setInstanceFollowRedirects(false);
                            connection.setConnectTimeout(4000);
                            connection.setReadTimeout(4000);
                            if (connection.getResponseCode() != 200 || connection.getContentLengthLong() > MAX_BYTES)
                                return;
                            InputStream input = connection.getInputStream();
                            if (input == null || !request.track(input)) return;
                            bytes = read(input, request);
                        } else {
                            AssetFileDescriptor asset = application.getContentResolver()
                                    .openAssetFileDescriptor(uri, "r", request.signal);
                            if (asset == null || !request.track(asset)) return;
                            bytes = read(asset, request);
                        }
                        if (bytes != null && !request.expired()) bitmap = decode(bytes, edge);
                        if (bitmap != null && !request.expired()) CACHE.put(key, bitmap);
                    } catch (IOException | RuntimeException unavailable) {
                        // Missing grants, broken providers and network errors leave the title usable.
                    } catch (OutOfMemoryError unavailable) {
                        // Providers can supply images whose decoder needs more memory than their dimensions imply.
                    } finally {
                        request.release();
                        if (!request.cancelled) request.deliver(bitmap);
                    }
                }
            });
        } catch (RejectedExecutionException busy) { request.stop(); request.deliver(null); }
        return request;
    }

    private static boolean allowed(Uri uri) {
        String scheme = uri.getScheme();
        return "https".equals(scheme) || "content".equals(scheme) || "android.resource".equals(scheme);
    }

    private static byte[] read(InputStream input, Request request) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while (!request.expired() && (count = input.read(buffer)) != -1) {
            if (output.size() + count > MAX_BYTES) return null;
            output.write(buffer, 0, count);
        }
        return request.expired() ? null : output.toByteArray();
    }

    private static byte[] read(AssetFileDescriptor asset, Request request) throws IOException {
        FileDescriptor descriptor = asset.getFileDescriptor();
        try {
            long length = asset.getDeclaredLength();
            if (length > MAX_BYTES) return null;
            if (asset.getStartOffset() > 0) Os.lseek(descriptor, asset.getStartOffset(), OsConstants.SEEK_SET);
            int flags = Os.fcntlInt(descriptor, OsConstants.F_GETFL, 0);
            Os.fcntlInt(descriptor, OsConstants.F_SETFL, flags | OsConstants.O_NONBLOCK);
            StructPollfd poll = new StructPollfd();
            poll.fd = descriptor;
            poll.events = (short) OsConstants.POLLIN;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            while (!request.expired()) {
                if (length >= 0 && output.size() >= length) break;
                int remaining = (int) Math.min(200, request.deadline - SystemClock.elapsedRealtime());
                if (remaining <= 0) return null;
                if (Os.poll(new StructPollfd[]{poll}, remaining) == 0) continue;
                int count;
                try {
                    int wanted = length < 0 ? buffer.length : (int) Math.min(buffer.length, length - output.size());
                    count = Os.read(descriptor, buffer, 0, wanted);
                } catch (ErrnoException retry) {
                    if (retry.errno == OsConstants.EAGAIN || retry.errno == OsConstants.EINTR) continue;
                    throw retry;
                }
                if (count == 0) break;
                if (output.size() + count > MAX_BYTES) return null;
                output.write(buffer, 0, count);
            }
            return request.expired() ? null : output.toByteArray();
        } catch (ErrnoException unavailable) { throw new IOException(unavailable); }
    }

    private static Bitmap decode(byte[] bytes, int edge) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 32768 || bounds.outHeight > 32768)
            return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while ((bounds.outWidth + options.inSampleSize - 1) / options.inSampleSize > edge
                || (bounds.outHeight + options.inSampleSize - 1) / options.inSampleSize > edge)
            options.inSampleSize *= 2;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private static void close(Closeable stream) {
        if (stream != null) try { stream.close(); } catch (IOException ignored) {}
    }
}
