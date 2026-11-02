package com.jeremykenedy.firetv.ui.nativeapp.widget.radar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class RadarLoader {
    interface Listener { void loaded(RadarSnapshot snapshot, Bitmap bitmap, String error, boolean retainImage); }
    private static final ThreadPoolExecutor WORK = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(2), new ThreadPoolExecutor.AbortPolicy());
    static { WORK.allowCoreThreadTimeOut(true); }
    private static RadarRepository shared;
    private final RadarRepository repository;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final RadarSource source;
    private Listener listener;
    private RadarRequest request;
    private Future<?> pending;
    private boolean active;
    private int generation;
    private RadarSnapshot lastSnapshot;
    private final Runnable refresh = new Runnable() { @Override public void run() { load(); } };
    private final Runnable timeout = new Runnable() { @Override public void run() {
        if (!active) return;
        cancel(); generation++;
        RadarSnapshot fallback = lastSnapshot == null ? null : RadarSnapshot.show(source, lastSnapshot.image, System.currentTimeMillis(), true);
        if (listener != null) listener.loaded(fallback, null,
                fallback != null && fallback.image != null ? "Radar refresh timed out. Stale saved image." : "Radar request timed out. The selected area is kept.",
                fallback != null && fallback.image != null);
        main.postDelayed(refresh, RadarCache.RETRY_MILLIS);
    } };

    RadarLoader(Context context, RadarSource source, Listener listener) {
        this.source = source; this.listener = listener;
        synchronized (RadarLoader.class) {
            if (shared == null) shared = new RadarRepository(new File(context.getCacheDir(), "nws-standard-radar"));
            repository = shared;
        }
    }
    void start() { if (!active) { active = true; load(); } }
    private void load() {
        if (!active) return;
        main.removeCallbacks(refresh); main.removeCallbacks(timeout); cancel();
        final int current = ++generation;
        final RadarRequest reading = new RadarRequest(); request = reading;
        WORK.purge();
        try {
            pending = WORK.submit(new Runnable() { @Override public void run() {
                Bitmap bitmap = null;
                RadarSnapshot snapshot = null;
                String error = null;
                boolean retained = false;
                try {
                    final RadarSnapshot saved = repository.saved(source, System.currentTimeMillis());
                    Bitmap previous = null;
                    if(saved.image != null) {
                        try { previous = decode(saved.image, reading); }
                        catch(CancellationException cancelled) { throw cancelled; }
                        catch(RuntimeException | OutOfMemoryError invalid) { repository.invalidate(source); }
                        if(previous == null) repository.invalidate(source);
                    }
                    final Bitmap preview = previous;
                    if (preview != null) main.post(new Runnable() { @Override public void run() {
                        if (!active || current != generation || listener == null) { preview.recycle(); return; }
                        lastSnapshot = saved;
                        listener.loaded(saved, preview, null, false);
                    } });
                    snapshot = repository.read(source, System.currentTimeMillis(), reading);
                    retained = preview != null && snapshot.image != null && snapshot.image.bytes == saved.image.bytes;
                    if (snapshot.image != null && !retained) {
                        bitmap = decode(snapshot.image, reading);
                        if (bitmap == null) {
                            repository.invalidate(source);
                            error = "Radar image could not be decoded. The selected area is kept.";
                        }
                    }
                    reading.check();
                } catch (CancellationException cancelled) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                } catch (RuntimeException | OutOfMemoryError unavailable) {
                    repository.invalidate(source);
                    if (bitmap != null) bitmap.recycle();
                    bitmap = null; error = "Radar image unavailable. The selected area is kept.";
                }
                final Bitmap result = bitmap;
                final RadarSnapshot observed = snapshot;
                final String message = error;
                final boolean retainImage = retained;
                main.post(new Runnable() { @Override public void run() {
                    if (!active || current != generation || listener == null) { if (result != null) result.recycle(); return; }
                    main.removeCallbacks(timeout);
                    lastSnapshot = observed;
                    listener.loaded(observed, result, message, retainImage);
                    long next = observed == null ? RadarCache.RETRY_MILLIS : observed.refreshAt - System.currentTimeMillis();
                    main.postDelayed(refresh, Math.max(RadarCache.RETRY_MILLIS, Math.min(3600000, next)));
                } });
            } });
            main.postDelayed(timeout, 20000);
        } catch (java.util.concurrent.RejectedExecutionException busy) {
            if (listener != null) listener.loaded(null, null, "Radar is waiting for an earlier request to stop.", false);
            main.postDelayed(refresh, RadarCache.RETRY_MILLIS);
        }
    }
    private static Bitmap decode(RadarCache.Entry entry, RadarRequest request) {
        byte[] bytes = entry.bytes;
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true; options.inScaled = false;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0 || options.outWidth > 1200 || options.outHeight > 1200
                || (long) options.outWidth * options.outHeight > 720000) throw new IllegalArgumentException("Unsupported radar dimensions");
        request.check(); options.inJustDecodeBounds = false; options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    void close() {
        active = false; generation++; listener = null;
        main.removeCallbacks(refresh); main.removeCallbacks(timeout); cancel(); WORK.purge();
    }
    private void cancel() {
        if (request != null) request.cancel();
        if (pending != null) pending.cancel(true);
        request = null; pending = null;
    }
}
