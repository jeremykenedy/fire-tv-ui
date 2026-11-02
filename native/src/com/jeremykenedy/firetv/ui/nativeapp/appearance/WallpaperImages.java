package com.jeremykenedy.firetv.ui.nativeapp.appearance;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(2), new ThreadPoolExecutor.AbortPolicy());
    static { WORKER.allowCoreThreadTimeOut(true); }
    static Future<?> load(Context context, String value, Callback callback) {
        final Context app=context.getApplicationContext(); final Uri uri=Uri.parse(value);
        if (!WallpaperAccess.supported(context,uri)) { callback.loaded(null); return null; }
        WORKER.purge();
        try {
            return WORKER.submit(new Runnable() {
                @Override public void run() {
                    Bitmap image=null;
                    try {
                        BitmapFactory.Options options=new BitmapFactory.Options(); options.inJustDecodeBounds=true;
                        try(InputStream input=app.getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(input,null,options); }
                        if(options.outWidth>0 && options.outHeight>0 && options.outWidth<=65535 && options.outHeight<=65535) {
                            options.inSampleSize=1;
                            while(options.outWidth/options.inSampleSize>1920 || options.outHeight/options.inSampleSize>1080) options.inSampleSize*=2;
                            options.inJustDecodeBounds=false;
                            if(!Thread.currentThread().isInterrupted()) try(InputStream input=app.getContentResolver().openInputStream(uri)) { image=BitmapFactory.decodeStream(input,null,options); }
                        }
                    } catch(Exception | OutOfMemoryError failure) { image=null; }
                    final Bitmap result=image;
                    new Handler(Looper.getMainLooper()).post(new Runnable() { @Override public void run() { callback.loaded(result); } });
                }
            });
        } catch(java.util.concurrent.RejectedExecutionException busy) { callback.loaded(null); return null; }
    }
}
