package com.jeremykenedy.firetv.ui.nativeapp.widget.photos;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.CancellationSignal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class PhotoDecoder {
    public static final int MAX_BYTES = 16 * 1024 * 1024, MAX_EDGE = 1024;
    private PhotoDecoder() {}
    public static Bitmap read(Context context, Uri uri, CancellationSignal signal) throws IOException {
        if (!"content".equals(uri.getScheme()) || uri.getAuthority() == null || uri.getAuthority().isEmpty()) throw new IOException("Choose a document image.");
        try (AssetFileDescriptor descriptor = context.getContentResolver().openAssetFileDescriptor(uri, "r", signal)) {
            if (descriptor == null || descriptor.getLength() > MAX_BYTES) throw new IOException("Choose a readable photo smaller than 16 MB.");
            final InputStream stream = descriptor.createInputStream();
            if (signal != null) signal.setOnCancelListener(new CancellationSignal.OnCancelListener() {
                @Override public void onCancel() { try { stream.close(); } catch (IOException ignored) {} }
            });
            try (InputStream input = stream) { return decode(input, signal); }
            finally { if (signal != null) signal.setOnCancelListener(null); }
        }
    }
    static Bitmap decode(InputStream input, CancellationSignal signal) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
        while ((count = input.read(buffer)) != -1) {
            cancelled(signal);
            if (bytes.size() + count > MAX_BYTES) throw new IOException("Choose a photo smaller than 16 MB.");
            bytes.write(buffer, 0, count);
        }
        cancelled(signal); byte[] data = bytes.toByteArray();
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (options.outWidth < 1 || options.outHeight < 1 || (long) options.outWidth * options.outHeight > 40000000)
            throw new IOException("Choose a readable photo no larger than 40 megapixels.");
        options.inSampleSize = 1;
        while ((Math.max(options.outWidth, options.outHeight) + options.inSampleSize - 1) / options.inSampleSize > MAX_EDGE) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false; options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap image = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (image == null) throw new IOException("This photo could not be read.");
        try {
            cancelled(signal);
            int orientation = ExifInterface.ORIENTATION_NORMAL;
            try { orientation = new ExifInterface(new ByteArrayInputStream(data)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL); }
            catch (IOException unsupportedMetadata) { /* Images without EXIF retain their decoded orientation. */ }
            Matrix transform = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: transform.setScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_180: transform.setRotate(180); break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL: transform.setScale(1, -1); break;
                case ExifInterface.ORIENTATION_TRANSPOSE: transform.setRotate(90); transform.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_90: transform.setRotate(90); break;
                case ExifInterface.ORIENTATION_TRANSVERSE: transform.setRotate(270); transform.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_270: transform.setRotate(270); break;
                default: break;
            }
            if (!transform.isIdentity()) {
                Bitmap rotated = Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), transform, true);
                if (rotated != image) { image.recycle(); image = rotated; }
            }
            cancelled(signal); return image;
        } catch (RuntimeException | IOException failure) { image.recycle(); throw failure; }
    }
    private static void cancelled(CancellationSignal signal) throws IOException {
        if (Thread.currentThread().isInterrupted() || signal != null && signal.isCanceled()) throw new IOException("Photo loading cancelled.");
    }
}
