package com.jeremykenedy.firetv.ui.nativeapp.appearance;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.io.InputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class WallpaperImages {
    interface Callback { void loaded(Bitmap image); }
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 30,
            TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(2), new ThreadPoolExecutor.AbortPolicy());
    static { WORKER.allowCoreThreadTimeOut(true); }

    static final class Request {
        private Callback callback;
        private Future<?> future;
        private boolean cancelled;

        Request(Callback callback) { this.callback = callback; }

        synchronized void cancel() {
            cancelled = true;
            callback = null;
            if (future != null) future.cancel(true);
            WORKER.purge();
        }

        private synchronized boolean cancelled() { return cancelled; }

        private void deliver(final Bitmap bitmap) {
            MAIN.post(new Runnable() {
                @Override public void run() {
                    Callback target;
                    synchronized (Request.this) {
                        target = cancelled ? null : callback;
                        callback = null;
                    }
                    if (target != null) target.loaded(bitmap);
                    else if (bitmap != null) bitmap.recycle();
                }
            });
        }
    }

    static Request load(Context context, String value, Callback callback) {
        final Request request = new Request(callback);
        final Context application = context.getApplicationContext();
        final Uri uri = Uri.parse(value);
        if (!WallpaperAccess.supported(context, uri)) {
            request.deliver(null);
            return request;
        }
        WORKER.purge();
        try {
            request.future = WORKER.submit(new Runnable() {
                @Override public void run() {
                    Bitmap image = null;
                    try {
                        if (request.cancelled()) return;
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        try (InputStream input = application.getContentResolver().openInputStream(uri)) {
                            BitmapFactory.decodeStream(input, null, options);
                        }
                        if (options.outWidth > 0 && options.outHeight > 0
                                && options.outWidth <= 65535 && options.outHeight <= 65535) {
                            options.inSampleSize = 1;
                            while (options.outWidth / options.inSampleSize > 1920
                                    || options.outHeight / options.inSampleSize > 1080) options.inSampleSize *= 2;
                            options.inJustDecodeBounds = false;
                            if (!request.cancelled()) {
                                try (InputStream input = application.getContentResolver().openInputStream(uri)) {
                                    image = BitmapFactory.decodeStream(input, null, options);
                                }
                                if (image != null && !request.cancelled()) {
                                    int orientation = ExifInterface.ORIENTATION_NORMAL;
                                    try (InputStream input = application.getContentResolver().openInputStream(uri)) {
                                        orientation = new ExifInterface(input).getAttributeInt(
                                                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                                    } catch (Exception unavailable) {
                                        // Formats without EXIF retain their decoded orientation.
                                    }
                                    image = orient(image, orientation);
                                }
                            }
                        }
                    } catch (Exception | OutOfMemoryError failure) {
                        image = null;
                    } finally {
                        request.deliver(image);
                    }
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException busy) {
            request.deliver(null);
        }
        return request;
    }

    static Bitmap orient(Bitmap image, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.setScale(-1,1); break;
            case ExifInterface.ORIENTATION_ROTATE_180: matrix.setRotate(180); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.setScale(1,-1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE: matrix.setRotate(90); matrix.postScale(-1,1); break;
            case ExifInterface.ORIENTATION_ROTATE_90: matrix.setRotate(90); break;
            case ExifInterface.ORIENTATION_TRANSVERSE: matrix.setRotate(-90); matrix.postScale(-1,1); break;
            case ExifInterface.ORIENTATION_ROTATE_270: matrix.setRotate(-90); break;
            default: return image;
        }
        Bitmap oriented = Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),matrix,true);
        if (oriented != image) image.recycle();
        return oriented;
    }
}
