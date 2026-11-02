package com.jeremykenedy.firetv.ui.nativeapp.widget.radar;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Native bitmap/view proof using an unmodified, explicitly captured NWS image. */
public final class RadarInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        final Bundle result = new Bundle();
        try {
            final byte[] bytes;
            try (InputStream input = getContext().getAssets().open("radar-fixtures/nws-krtx.gif"); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) out.write(buffer, 0, count);
                bytes = out.toByteArray();
            }
            final Throwable[] error = new Throwable[1];
            runOnMainSync(new Runnable() { @Override public void run() {
                try {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    check(bitmap != null && bitmap.getWidth() == 600 && bitmap.getHeight() == 550, "Original complete NWS GIF decodes at source dimensions");
                    long now = System.currentTimeMillis();
                    RadarSource source = RadarSource.find("station:KRTX");
                    RadarCache.Entry image = new RadarCache.Entry(source, bytes, now-20*60000, now-20*60000, "");
                    RadarSnapshot snapshot = RadarSnapshot.show(source, image, now, true);
                    RadarView view = new RadarView(getTargetContext(), false);
                    view.render(snapshot, bitmap, null);
                    float density = getTargetContext().getResources().getDisplayMetrics().density;
                    int width = Math.round(520*density), height = Math.round(420*density);
                    view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                    view.layout(0, 0, width, height);
                    ImageView picture = (ImageView) view.findViewWithTag("radar:image");
                    check(picture.getScaleType() == ImageView.ScaleType.FIT_CENTER, "Source logo, warning legend, product scale and printed timestamps cannot be cropped");
                    check(((BitmapDrawable)picture.getDrawable()).getBitmap() == bitmap, "Original complete decoded product is used");
                    check(picture.getHeight() > 0, "Radar area remains visible alongside attribution");
                    TextView status = (TextView) view.findViewWithTag("radar:status");
                    check(status.getText().toString().contains("Stale"), "Captured image has an explicit stale state");
                    TextView attribution = (TextView)view.findViewWithTag("radar:source");
                    check(attribution.getBottom() <= height && attribution.getText().toString().contains("Downloaded:")
                            && attribution.getText().toString().contains("File modified:")
                            && attribution.getText().toString().contains("Not an alert service"), "Separate retrieval/file time and source limits remain visible");
                    Bitmap rendered = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    view.draw(new Canvas(rendered));
                    File folder = new File(getTargetContext().getFilesDir(), "radar-proof"); folder.mkdirs();
                    File screenshot = new File(folder, "captured-stale-radar.png");
                    try (FileOutputStream output = new FileOutputStream(screenshot)) { rendered.compress(Bitmap.CompressFormat.PNG, 100, output); }
                    rendered.recycle(); view.stop();
                    result.putString("image", screenshot.toString());
                } catch (Throwable failure) { error[0] = failure; }
            } });
            if (error[0] != null) throw new AssertionError(error[0]);
            result.putString("result", "PASS: native original GIF decode, uncropped fit, visible stale state, separate metadata and bounded widget render");
        } catch (Throwable failure) { result.putString("failure", failure.toString()); }
        finish(result.containsKey("failure") ? 1 : -1, result);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
