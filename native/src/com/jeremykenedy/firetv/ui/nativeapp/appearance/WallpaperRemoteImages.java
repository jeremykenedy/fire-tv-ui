package com.jeremykenedy.firetv.ui.nativeapp.appearance;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HttpsURLConnection;

/** Downloads a validated image without changing the selected wallpaper. */
public final class WallpaperRemoteImages {
    private static final int MAX_BYTES = 12 * 1024 * 1024;
    private static final int MAX_DIMENSION = 16384;
    private static final int DEADLINE_MS = 20000;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor WORK = new ThreadPoolExecutor(2, 2, 30,
            TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(2));
    private static final ThreadPoolExecutor CLEANUP = new ThreadPoolExecutor(1, 1, 30,
            TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(8));
    private static final ScheduledThreadPoolExecutor DEADLINES = new ScheduledThreadPoolExecutor(1);
    static { WORK.allowCoreThreadTimeOut(true); CLEANUP.allowCoreThreadTimeOut(true); DEADLINES.setRemoveOnCancelPolicy(true); }

    public interface Callback {
        void success(Uri image);
        void failure(String message);
    }

    public static final class Request {
        private final long deadline = SystemClock.elapsedRealtime() + DEADLINE_MS;
        private Callback callback;
        private Future<?> future;
        private ScheduledFuture<?> timeout;
        private HttpsURLConnection connection;
        private File file;
        private boolean cancelled, finished, complete;

        private Request(Callback callback) { this.callback = callback; }

        public void cancel() {
            synchronized (this) {
                if (complete) return;
                cancelled = true;
                callback = null;
            }
            stop();
        }

        private void stop() {
            final HttpsURLConnection active;
            final File pending;
            synchronized (this) {
                if (future != null) future.cancel(true);
                if (timeout != null) timeout.cancel(false);
                active = connection;
                connection = null;
                pending = file;
                file = null;
            }
            WORK.purge();
            if (pending != null) pending.delete();
            try {
                CLEANUP.execute(new Runnable() {
                    @Override public void run() {
                        if (active != null) active.disconnect();
                    }
                });
            } catch (RejectedExecutionException ignored) {
                // Socket timeouts and the worker's finally block still bound cleanup.
            }
        }

        private synchronized boolean expired() {
            return cancelled || finished || Thread.currentThread().isInterrupted()
                    || SystemClock.elapsedRealtime() >= deadline;
        }

        private synchronized boolean track(HttpsURLConnection value) {
            if (expired()) return false;
            connection = value;
            return true;
        }

        private synchronized boolean track(File value) {
            if (expired()) return false;
            file = value;
            return true;
        }

        private boolean deliver(final Uri image, final String message) {
            synchronized (this) {
                if (cancelled || finished) return false;
                finished = true;
                if (timeout != null) timeout.cancel(false);
            }
            MAIN.post(new Runnable() {
                @Override public void run() {
                    Callback target;
                    synchronized (Request.this) {
                        if (cancelled || complete) return;
                        complete = true;
                        target = callback;
                        callback = null;
                        if (timeout != null) timeout.cancel(false);
                        if (image != null) file = null;
                    }
                    if (target != null) {
                        if (image != null) target.success(image);
                        else target.failure(message);
                    }
                }
            });
            return true;
        }
    }

    private WallpaperRemoteImages() {}

    public static Request download(Context context, final String value, Callback callback) {
        final Request request = new Request(callback);
        final Context application = context.getApplicationContext();
        request.timeout = DEADLINES.schedule(new Runnable() {
            @Override public void run() {
                if (request.deliver(null, "The image download timed out.")) request.stop();
            }
        }, DEADLINE_MS, TimeUnit.MILLISECONDS);
        try {
            request.future = WORK.submit(new Runnable() {
                @Override public void run() {
                    File output = null;
                    boolean success = false;
                    try {
                        byte[] bytes = read(value, request);
                        validate(bytes, request);
                        if (request.expired()) return;
                        File directory = new File(application.getFilesDir(), "wallpapers");
                        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException();
                        output = File.createTempFile("wallpaper-", ".img", directory);
                        if (!request.track(output)) return;
                        try (FileOutputStream stream = new FileOutputStream(output)) {
                            stream.write(bytes);
                            stream.getFD().sync();
                        }
                        if (request.expired()) return;
                        success = request.deliver(Uri.fromFile(output), null);
                    } catch (Exception | OutOfMemoryError failure) {
                        if (!request.expired()) request.deliver(null, "The image could not be downloaded or is invalid.");
                    } finally {
                        if (!success && output != null) output.delete();
                    }
                }
            });
        } catch (RejectedExecutionException busy) {
            request.deliver(null, "Another image download is in progress. Try again.");
        }
        return request;
    }

    private static byte[] read(String value, Request request) throws Exception {
        URL url = checkedUrl(value);
        for (int redirects = 0; redirects <= 3; redirects++) {
            if (request.expired()) throw new IOException();
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            if (!request.track(connection)) { connection.disconnect(); throw new IOException(); }
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(5000); connection.setReadTimeout(5000);
                int code = connection.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || redirects == 3) throw new IOException();
                    url = checkedUrl(new URL(url, location).toExternalForm());
                    continue;
                }
                if (code != 200 || connection.getContentLengthLong() > MAX_BYTES) throw new IOException();
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                try (InputStream input = connection.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (request.expired() || count > MAX_BYTES - output.size()) throw new IOException();
                        output.write(buffer, 0, count);
                    }
                }
                return output.toByteArray();
            } finally { connection.disconnect(); }
        }
        throw new IOException();
    }

    private static URL checkedUrl(String value) throws Exception {
        URI uri = new URI(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null) throw new IOException();
        return uri.toURL();
    }

    private static void validate(byte[] bytes, Request request) throws IOException {
        if (bytes.length == 0 || request.expired()) throw new IOException();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth < 1 || options.outHeight < 1
                || options.outWidth > MAX_DIMENSION || options.outHeight > MAX_DIMENSION) throw new IOException();
        options.inSampleSize = 1;
        while (options.outWidth / options.inSampleSize > 1920
                || options.outHeight / options.inSampleSize > 1080) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (bitmap == null) throw new IOException();
        bitmap.recycle();
        if (request.expired()) throw new IOException();
    }
}
