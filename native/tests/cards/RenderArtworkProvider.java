package com.jeremykenedy.firetv.ui.nativeapp.providers;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Test APK only: bounded image pipes for delayed success and timeout checks. */
public final class RenderArtworkProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "image/png"; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only.");
        try {
            final ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            final boolean image = "slow-image".equals(uri.getLastPathSegment());
            Thread writer = new Thread(new Runnable() {
                @Override public void run() {
                    SystemClock.sleep(image ? 800 : 10000);
                    if (image) {
                        Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(Color.rgb(80, 100, 160));
                        try (ParcelFileDescriptor.AutoCloseOutputStream output =
                                     new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
                        } catch (IOException ignored) {
                        } finally {
                            bitmap.recycle();
                        }
                    } else try { pipe[1].close(); } catch (IOException ignored) {}
                }
            }, "render-artwork-fixture");
            writer.setDaemon(true);
            writer.start();
            return pipe[0];
        } catch (IOException unavailable) { throw new FileNotFoundException("Pipe unavailable."); }
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
