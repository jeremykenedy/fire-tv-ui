package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Native decorative layer behind Main. It never takes input or runs off Main. */
public final class MenuBackgroundView extends View {
    private static final List<WeakReference<MenuBackgroundView>> views = new ArrayList<>();
    private static boolean mainResumed;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float[] x = new float[80], y = new float[80];
    private Shader base, ribbon;
    private long lastFrame;
    private float phase;
    private int style, speed, intensity, brightness;
    private boolean running;
    private final Runnable frame = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long now = SystemClock.uptimeMillis();
            phase += Math.min(100, now - lastFrame) * speed / 500000f;
            lastFrame = now;
            invalidate();
            postDelayed(this, 33);
        }
    };

    public MenuBackgroundView(Context context) {
        super(context);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        for (int i = 0; i < x.length; i++) {
            x[i] = (i * 0.61803399f) % 1f;
            y[i] = (i * 0.41421356f + 0.13f) % 1f;
        }
        views.add(new WeakReference<>(this));
        reload();
    }

    public static boolean enabled(Context context) {
        return MenuBackgroundOptions.style(DeviceSettings.prefs(context)
                .getString(MenuBackgroundOptions.STYLE, "off")) != 0;
    }

    public static void resumed(boolean resumed) {
        mainResumed = resumed;
        Iterator<WeakReference<MenuBackgroundView>> iterator = views.iterator();
        while (iterator.hasNext()) {
            MenuBackgroundView view = iterator.next().get();
            if (view == null) iterator.remove();
            else { view.reload(); view.updateRunning(); }
        }
    }

    private void reload() {
        android.content.SharedPreferences prefs = DeviceSettings.prefs(getContext());
        style = MenuBackgroundOptions.style(prefs.getString(MenuBackgroundOptions.STYLE, "off"));
        speed = MenuBackgroundOptions.speed(prefs.getInt(MenuBackgroundOptions.SPEED, 50));
        intensity = MenuBackgroundOptions.level(prefs.getInt(MenuBackgroundOptions.INTENSITY, 50));
        brightness = MenuBackgroundOptions.level(prefs.getInt(MenuBackgroundOptions.BRIGHTNESS, 50));
        invalidate();
    }

    private void updateRunning() {
        removeCallbacks(frame);
        running = mainResumed && isAttachedToWindow() && getWindowVisibility() == VISIBLE && style != 0;
        if (running) { lastFrame = SystemClock.uptimeMillis(); post(frame); }
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); updateRunning(); }
    @Override protected void onDetachedFromWindow() { removeCallbacks(frame); running = false; super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); updateRunning(); }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        base = new LinearGradient(0, 0, w, h, new int[]{0xff090e22, 0xff14213d, 0xff100d27}, null, Shader.TileMode.CLAMP);
        ribbon = new LinearGradient(0, 0, w, h, new int[]{0xff44c7d2, 0xff7b61ff, 0xffb7c8e4}, null, Shader.TileMode.CLAMP);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (style == 0) return;
        float w = getWidth(), h = getHeight();
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        paint.setShader(base);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
        if (style == 2) drawRibbons(canvas, w, h);
        else if (style == 4) drawPrism(canvas, w, h);
        else drawParticles(canvas, w, h);
        paint.setShader(null);
        paint.setColor(Color.BLACK);
        paint.setAlpha(130 - brightness);
        canvas.drawRect(0, 0, w, h, paint);
    }

    private void drawParticles(Canvas canvas, float w, float h) {
        int count = 16 + intensity / 2;
        paint.setStrokeWidth(Math.max(1, w / 1920));
        for (int i = 0; i < count; i++) {
            float px = ((x[i] + phase * (0.06f + i % 4 * 0.01f)) % 1f) * w;
            float py = ((y[i] + (float)Math.sin(phase + i) * 0.025f + 1) % 1f) * h;
            paint.setColor(i % 3 == 0 ? 0xff44c7d2 : 0xffb7c8e4);
            paint.setAlpha(45 + brightness);
            canvas.drawCircle(px, py, (1 + i % 3) * w / 1280, paint);
            if (style == 3 && i > 0) {
                float qx = ((x[i - 1] + phase * (0.06f + (i - 1) % 4 * 0.01f)) % 1f) * w;
                float qy = ((y[i - 1] + (float)Math.sin(phase + i - 1) * 0.025f + 1) % 1f) * h;
                if (Math.abs(px - qx) < w * 0.2f && Math.abs(py - qy) < h * 0.25f) {
                    paint.setAlpha(18 + brightness / 2);
                    canvas.drawLine(px, py, qx, qy, paint);
                }
            }
        }
    }

    private void drawRibbons(Canvas canvas, float w, float h) {
        paint.setShader(ribbon);
        paint.setStyle(Paint.Style.STROKE);
        for (int band = 0; band < 3; band++) {
            path.reset();
            for (int step = 0; step <= 32; step++) {
                float px = step * w / 32;
                float py = h * (0.3f + band * 0.16f) + (float)Math.sin(step / 8f + phase * 0.4f + band) * h * 0.14f;
                if (step == 0) path.moveTo(px, py); else path.lineTo(px, py);
            }
            paint.setStrokeWidth(h * (0.025f + intensity / 3000f));
            paint.setAlpha(20 + brightness / 3);
            canvas.drawPath(path, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
    }

    private void drawPrism(Canvas canvas, float w, float h) {
        paint.setShader(ribbon);
        for (int facet = 0; facet < 5; facet++) {
            float center = w * (0.15f + facet * 0.2f) + (float)Math.sin(phase * 0.25f + facet) * w * 0.08f;
            path.reset();
            path.moveTo(center - w * 0.17f, h);
            path.lineTo(center + w * 0.08f, 0);
            path.lineTo(center + w * 0.21f, 0);
            path.lineTo(center - w * 0.02f, h);
            path.close();
            paint.setAlpha(10 + brightness / 5 + intensity / 8);
            canvas.drawPath(path, paint);
        }
        paint.setShader(null);
    }
}
