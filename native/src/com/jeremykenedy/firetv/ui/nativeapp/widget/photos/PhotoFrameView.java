package com.jeremykenedy.firetv.ui.nativeapp.widget.photos;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PhotoFrameView extends FrameLayout {
    private final ImageView image;
    private final TextView status;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService worker;
    private CancellationSignal cancellation;
    private Bitmap displayed;
    private PhotoOptions options;
    private boolean active;
    private int generation, index;
    private final Runnable advance = new Runnable() { @Override public void run() { showNext(); } };
    public PhotoFrameView(Context context) {
        super(context); setBackgroundColor(0xff111722);
        image = new ImageView(context); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(image, new LayoutParams(-1, -1));
        status = new TextView(context); status.setTextColor(Color.WHITE); status.setTextSize(18);
        status.setGravity(Gravity.CENTER); int padding = Math.round(14 * getResources().getDisplayMetrics().density);
        status.setPadding(padding, padding, padding, padding); addView(status, new LayoutParams(-1, -1));
        status.setText("Choose photos to set up Photo frame.");
    }
    public void start() {
        final PhotoOptions next;
        try { next = PhotoPreferences.load(getContext()); }
        catch (IllegalStateException error) { stop(); status.setText(error.getMessage()); return; }
        if (active && options != null && PhotoPreferences.encode(next).equals(PhotoPreferences.encode(options))) return;
        stop(); active = true; index = 0; options = next;
        image.setScaleType(options.fit == PhotoOptions.Fit.CONTAIN ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
        if (options.photos.isEmpty()) { status.setText(options.configured ? "No selected photos. Open Photo frame to add images." : "Choose photos to set up Photo frame."); return; }
        worker = Executors.newSingleThreadExecutor(); showNext();
    }
    private void showNext() {
        if (!active || worker == null || options.photos.isEmpty()) return;
        final PhotoOptions.Photo photo = options.photos.get(index++ % options.photos.size());
        if (index >= options.photos.size()) index = 0;
        final int request = generation;
        cancellation = new CancellationSignal(); final CancellationSignal signal = cancellation;
        worker.execute(new Runnable() {
            @Override public void run() {
                Bitmap loaded = null;
                try { loaded = PhotoDecoder.read(getContext().getApplicationContext(), Uri.parse(photo.uri), signal); }
                catch (Exception unavailable) { /* Retain the selected URI for reconnection. */ }
                final Bitmap result = loaded;
                handler.post(new Runnable() {
                    @Override public void run() {
                        if (!active || request != generation) { if (result != null) result.recycle(); return; }
                        clearImage(); displayed = result; image.setImageBitmap(result);
                        status.setText(result == null ? "Photo unavailable. Open Photo frame to reconnect or remove it. Your selection has been kept." : "");
                        image.setContentDescription(result == null ? "Photo unavailable" : "Selected personal photo");
                        if (options.photos.size() > 1) handler.postDelayed(advance, options.intervalSeconds * 1000L);
                    }
                });
            }
        });
    }
    public void stop() {
        active = false; generation++; handler.removeCallbacks(advance);
        if (cancellation != null) cancellation.cancel(); cancellation = null;
        if (worker != null) worker.shutdownNow(); worker = null;
        clearImage();
    }
    private void clearImage() { image.setImageDrawable(null); if (displayed != null) displayed.recycle(); displayed = null; }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
}
