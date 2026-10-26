package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.Choreographer;
import android.view.View;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Decorative layer that runs only while its own menu is visible and resumed. */
public final class MenuBackgroundView extends View {
    private static final List<WeakReference<MenuBackgroundView>> views = new ArrayList<>();
    private static boolean mainResumed;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Path path = new Path();
    private final float[] x = new float[80], y = new float[80];
    private Shader base, ribbon, water, coolGlow, warmGlow;
    private final MenuBackgroundClock clock = new MenuBackgroundClock();
    private double phase;
    private int style, speed, intensity, brightness;
    private final boolean mainLayer;
    private boolean settingsResumed;
    private final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
        @Override public void doFrame(long nanos) {
            if (!clock.frame(nanos, speed)) return;
            phase = clock.phase();
            invalidate();
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    public MenuBackgroundView(Context context) {
        this(context, true);
    }

    MenuBackgroundView(Context context, boolean mainLayer) {
        super(context);
        this.mainLayer = mainLayer;
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
            else if (view.mainLayer) { view.reload(); view.updateRunning(); }
        }
    }

    void menuResumed(boolean resumed) {
        settingsResumed = resumed;
        reload();
        updateRunning();
    }

    void refresh() {
        reload();
        updateRunning();
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
        Choreographer.getInstance().removeFrameCallback(frame);
        boolean running = (mainLayer ? mainResumed : settingsResumed) && isAttachedToWindow()
                && getWindowVisibility() == VISIBLE && getVisibility() == VISIBLE
                && hasWindowFocus() && style != 0;
        clock.resumed(running);
        if (running) Choreographer.getInstance().postFrameCallback(frame);
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); updateRunning(); }
    @Override protected void onDetachedFromWindow() { Choreographer.getInstance().removeFrameCallback(frame); clock.resumed(false); super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); updateRunning(); }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); updateRunning(); }
    @Override protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        if (clock != null) updateRunning();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        base = new LinearGradient(0, 0, w, h, new int[]{0xff090e22, 0xff14213d, 0xff100d27}, null, Shader.TileMode.CLAMP);
        ribbon = new LinearGradient(0, 0, w, h, new int[]{0xff44c7d2, 0xff7b61ff, 0xffb7c8e4}, null, Shader.TileMode.CLAMP);
        water = new LinearGradient(0, 0, w, h, new int[]{0xff226c91, 0xff55b9b1, 0xff6e8ebd}, null, Shader.TileMode.CLAMP);
        coolGlow = new RadialGradient(0, 0, 1, new int[]{0xffa9c9df, 0x553c8bab, 0x002b487d},
                new float[]{0, .35f, 1}, Shader.TileMode.CLAMP);
        warmGlow = new RadialGradient(0, 0, 1, new int[]{0xffffd6a0, 0x88c37443, 0x005e3542},
                new float[]{0, .25f, 1}, Shader.TileMode.CLAMP);
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
        else if (style == 5) drawSilk(canvas, w, h);
        else if (style == 6) drawBokeh(canvas, w, h);
        else if (style == 7) drawStars(canvas, w, h);
        else if (style == 8) drawEmbers(canvas, w, h);
        else if (style == 9) drawWaves(canvas, w, h);
        else if (style == 10) drawRain(canvas, w, h);
        else if (style == 11) drawOrbits(canvas, w, h);
        else if (style == 12) drawRays(canvas, w, h);
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
            float px = (float) (((x[i] + phase * (0.06f + i % 4 * 0.01f)) % 1f) * w);
            float py = ((y[i] + (float)Math.sin(phase + i) * 0.025f + 1) % 1f) * h;
            paint.setColor(i % 3 == 0 ? 0xff44c7d2 : 0xffb7c8e4);
            paint.setAlpha(45 + brightness);
            canvas.drawCircle(px, py, (1 + i % 3) * w / 1280, paint);
            if (style == 3 && i >= 5) {
                float qx = (float) (((x[i - 5] + phase * (0.06f + (i - 5) % 4 * 0.01f)) % 1f) * w);
                float qy = ((y[i - 5] + (float)Math.sin(phase + i - 5) * 0.025f + 1) % 1f) * h;
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

    private void curve(float w, float center, float amplitude, double time, int band) {
        path.reset();
        path.moveTo(0, center + MenuBackgroundMotion.wave(0, time, band) * amplitude);
        for (int step = 0; step < 16; step++) {
            float start = step / 16f, end = (step + 1) / 16f, third = 1 / 48f;
            float a = center + MenuBackgroundMotion.wave(start, time, band) * amplitude;
            float b = center + MenuBackgroundMotion.wave(end, time, band) * amplitude;
            path.cubicTo((start + third) * w,
                    a + MenuBackgroundMotion.waveSlope(start, time, band) * amplitude * third,
                    (end - third) * w,
                    b - MenuBackgroundMotion.waveSlope(end, time, band) * amplitude * third,
                    end * w, b);
        }
    }

    private void drawSilk(Canvas canvas, float w, float h) {
        paint.setShader(ribbon);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int band = 0; band < 4; band++) {
            curve(w, h * (.23f + band * .17f), h * (.12f + intensity / 2500f), phase * .32, band);
            paint.setStrokeWidth(h * (.025f + band * .009f));
            paint.setAlpha(18 + brightness / 3);
            canvas.drawPath(path, paint);
            paint.setStrokeWidth(Math.max(1, w / 1280));
            paint.setAlpha(36 + brightness / 2);
            canvas.drawPath(path, paint);
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
    }

    private void glow(Canvas canvas, float px, float py, float rx, float ry, Shader shader, int alpha) {
        paint.setShader(shader);
        paint.setAlpha(alpha);
        canvas.save();
        canvas.translate(px, py);
        canvas.scale(rx, ry);
        canvas.drawCircle(0, 0, 1, paint);
        canvas.restore();
        paint.setShader(null);
    }

    private void drawBokeh(Canvas canvas, float w, float h) {
        for (int i = 0; i < 5 + intensity / 12; i++) {
            float depth = MenuBackgroundMotion.seed(i, 1);
            float px = w * (.12f + MenuBackgroundMotion.seed(i, 2) * .76f
                    + (float) Math.sin(phase * (.12 + depth * .18) + i * 2.7) * .12f);
            float py = h * (.1f + MenuBackgroundMotion.seed(i, 3) * .8f
                    + (float) Math.cos(phase * (.17 + depth * .11) + i * 1.9) * .1f);
            float radius = h * (.065f + depth * .12f);
            glow(canvas, px, py, radius, radius, i % 4 == 0 ? warmGlow : coolGlow,
                    24 + brightness / 2);
        }
    }

    private void drawStars(Canvas canvas, float w, float h) {
        for (int i = 0; i < 32 + intensity; i++) {
            float z = MenuBackgroundMotion.wrap(MenuBackgroundMotion.seed(i, 4)
                    - phase * (.025 + MenuBackgroundMotion.seed(i, 5) * .035));
            float px = w * .5f + (MenuBackgroundMotion.seed(i, 6) - .5f) * w / (z + .35f);
            float py = h * .5f + (MenuBackgroundMotion.seed(i, 7) - .5f) * h / (z + .35f);
            paint.setColor(i % 5 == 0 ? 0xff83b8d4 : 0xffd2dce7);
            paint.setAlpha(Math.round((32 + brightness) * MenuBackgroundMotion.envelope(z)));
            canvas.drawCircle(px, py, w / 1920 * (.7f + (1 - z) * 1.8f), paint);
        }
    }

    private void drawEmbers(Canvas canvas, float w, float h) {
        glow(canvas, w * .5f, h * 1.12f, w * .7f, h * .55f, warmGlow, 28 + brightness / 2);
        for (int i = 0; i < 16 + intensity / 2; i++) {
            float age = MenuBackgroundMotion.wrap(MenuBackgroundMotion.seed(i, 8)
                    + phase * (.04 + MenuBackgroundMotion.seed(i, 9) * .07));
            float px = w * (MenuBackgroundMotion.seed(i, 10)
                    + (float) Math.sin(phase * .7 + i * 1.9) * .025f * age);
            float py = h * (1 - age);
            float radius = w / 1920 * (1.5f + MenuBackgroundMotion.seed(i, 11) * 3);
            paint.setColor(i % 3 == 0 ? 0xffe6b184 : 0xffb87958);
            paint.setAlpha(Math.round((42 + brightness) * MenuBackgroundMotion.envelope(age)));
            canvas.drawCircle(px, py, radius, paint);
        }
    }

    private void drawWaves(Canvas canvas, float w, float h) {
        paint.setShader(water);
        paint.setStyle(Paint.Style.STROKE);
        int count = 7 + intensity / 5;
        for (int band = 0; band < count; band++) {
            curve(w, h * (.2f + band * .6f / count), h * .13f, phase * .28 + band * .07, 0);
            paint.setStrokeWidth(w / 1920 * (band % 5 == 0 ? 2.2f : 1.1f));
            paint.setAlpha(25 + brightness / 2);
            canvas.drawPath(path, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
    }

    private void drawRain(Canvas canvas, float w, float h) {
        paint.setColor(0xffadc2db);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 24 + intensity / 2; i++) {
            float depth = MenuBackgroundMotion.seed(i, 12);
            float age = MenuBackgroundMotion.wrap(MenuBackgroundMotion.seed(i, 13)
                    + phase * (.12 + depth * .18));
            float px = w * MenuBackgroundMotion.wrap(MenuBackgroundMotion.seed(i, 14) - age * .055);
            float py = h * age;
            paint.setAlpha(Math.round((20 + brightness * .65f) * MenuBackgroundMotion.envelope(age)));
            paint.setStrokeWidth(w / 1920 * (.65f + depth));
            canvas.drawLine(px, py, px - w * .0015f, py + h * (.006f + depth * .012f), paint);
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawOrbits(Canvas canvas, float w, float h) {
        paint.setStyle(Paint.Style.STROKE);
        for (int ring = 0; ring < 3 + intensity / 25; ring++) {
            float rx = w * (.12f + ring * .042f), ry = h * (.08f + ring * .025f);
            float angle = (float) (phase * (.08 + ring * .012) + ring * .81);
            canvas.save();
            canvas.translate(w * .55f, h * .53f);
            canvas.rotate(-24 + ring * 9);
            paint.setColor(0xff7398bc);
            paint.setAlpha(18 + brightness / 3);
            paint.setStrokeWidth(Math.max(1, w / 1600));
            canvas.drawOval(-rx, -ry, rx, ry, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xffbdd8df);
            paint.setAlpha(45 + brightness);
            canvas.drawCircle((float) Math.cos(angle) * rx, (float) Math.sin(angle) * ry,
                    w / 1920 * (1.8f + ring * .2f), paint);
            paint.setStyle(Paint.Style.STROKE);
            canvas.restore();
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawRays(Canvas canvas, float w, float h) {
        for (int i = 0; i < 2 + intensity / 34; i++) {
            canvas.save();
            canvas.translate(w * (.12f + i * .24f)
                    + (float) Math.sin(phase * .12 + i * 1.3) * w * .08f, h * .35f);
            canvas.rotate(24 + (float) Math.sin(phase * .08 + i) * 9);
            glow(canvas, 0, 0, w * .085f, h * 1.15f, coolGlow, 24 + brightness / 2);
            canvas.restore();
        }
    }
}
