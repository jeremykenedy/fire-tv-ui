package com.jeremykenedy.firetv.ui.nativeapp.holidays;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class HolidaySceneLoader {
    private static final int MAX_WIDTH = 1920, MAX_HEIGHT = 1080;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor WORK = new ThreadPoolExecutor(1, 1, 30,
            TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(4));
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(16 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getAllocationByteCount(); }
    };

    public interface Callback { void onLoaded(Bitmap bitmap); }

    public static final class Request {
        private Callback callback;
        private Future<?> future;
        private volatile boolean cancelled;

        private Request(Callback callback) { this.callback = callback; }

        public synchronized void cancel() {
            cancelled = true;
            callback = null;
            if (future != null) future.cancel(true);
            WORK.purge();
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
    }

    private HolidaySceneLoader() {}

    public static Request load(Context context, final HolidayScenes.Scene scene,
            int width, int height, Callback callback) {
        return loadResource(context, scene, scene == null ? "" : scene.resourceName, width, height, callback);
    }

    public static Request loadAnimationBackground(Context context, HolidayScenes.Scene scene,
            int width, int height, Callback callback) {
        return loadResource(context, scene, scene == null ? "" : scene.animatedBackgroundResourceName,
                width, height, callback);
    }

    public static Request loadCharacters(Context context, HolidayScenes.Scene scene, Callback callback) {
        return loadResource(context, scene, scene == null ? "" : scene.characterResourceName,
                MAX_WIDTH, MAX_HEIGHT, callback);
    }

    private static Request loadResource(Context context, HolidayScenes.Scene scene, final String resourceName,
            int width, int height, Callback callback) {
        final Request request = new Request(callback);
        if (scene == null || HolidayScenes.find(scene.id) != scene || resourceName.isEmpty()) {
            request.deliver(null);
            return request;
        }
        final int targetWidth = Math.max(1, Math.min(MAX_WIDTH, width));
        final int targetHeight = Math.max(1, Math.min(MAX_HEIGHT, height));
        final String key = resourceName + ":" + targetWidth + "x" + targetHeight;
        Bitmap cached = CACHE.get(key);
        if (cached != null) { request.deliver(cached); return request; }
        final Context application = context.getApplicationContext();
        try {
            request.future = WORK.submit(new Runnable() {
                @Override public void run() {
                    Bitmap bitmap = null;
                    try {
                        if (request.cancelled) return;
                        Resources resources = application.getResources();
                        int resource = resources.getIdentifier(resourceName, "drawable", application.getPackageName());
                        if (resource != 0) bitmap = decode(resources, resource, targetWidth, targetHeight);
                        if (bitmap != null && !request.cancelled) CACHE.put(key, bitmap);
                    } catch (RuntimeException | OutOfMemoryError unavailable) {
                        // The existing background remains usable when scene artwork cannot load.
                    } finally { request.deliver(bitmap); }
                }
            });
        } catch (RejectedExecutionException busy) { request.deliver(null); }
        return request;
    }

    private static Bitmap decode(Resources resources, int resource, int width, int height) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        bounds.inScaled = false;
        BitmapFactory.decodeResource(resources, resource, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0
                || bounds.outWidth > 8192 || bounds.outHeight > 8192) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = 1;
        while ((bounds.outWidth + options.inSampleSize - 1) / options.inSampleSize > width
                || (bounds.outHeight + options.inSampleSize - 1) / options.inSampleSize > height)
            options.inSampleSize *= 2;
        return BitmapFactory.decodeResource(resources, resource, options);
    }
}
