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
    private final Path snowflake = snowflakePath();
    private final Path leafVeins = leafVeinPath();
    private final android.graphics.RectF glowBounds = new android.graphics.RectF();
    private android.graphics.Bitmap seasonalGlow;
    private int seasonalGlowColor;
    private android.graphics.Bitmap snowflakeSprite;
    private int snowflakeColor;
    private final float[] x = new float[80], y = new float[80];
    private Shader base, ribbon, water, coolGlow, warmGlow;
    private final Shader[] stillBase = new Shader[4], stillLight = new Shader[4];
    private final MenuBackgroundClock clock = new MenuBackgroundClock();
    private double phase;
    private int style, speed, intensity, brightness, opacity;
    private boolean frozen;
    private String paletteId = "";
    private HolidayThemes.Theme palette;
    private Shader holidayBase, holidayGlow, holidayRibbon, holidayParticle;
    private final boolean overlay;
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
        this(context, true, false);
    }

    MenuBackgroundView(Context context, boolean mainLayer) {
        this(context, mainLayer, false);
    }

    public MenuBackgroundView(Context context, boolean mainLayer, boolean overlay) {
        super(context);
        this.mainLayer = mainLayer;
        this.overlay = overlay;
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

    public static boolean overlayEnabled(Context context) {
        return MenuBackgroundOptions.style(DeviceSettings.prefs(context)
                .getString(MenuBackgroundOptions.OVERLAY, "off")) != 0;
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
        style = MenuBackgroundOptions.style(prefs.getString(overlay
                ? MenuBackgroundOptions.OVERLAY : MenuBackgroundOptions.STYLE, "off"));
        speed = MenuBackgroundOptions.speed(prefs.getInt(overlay
                ? MenuBackgroundOptions.OVERLAY_SPEED : MenuBackgroundOptions.SPEED, 50));
        intensity = MenuBackgroundOptions.level(prefs.getInt(overlay
                ? MenuBackgroundOptions.OVERLAY_INTENSITY : MenuBackgroundOptions.INTENSITY, 50));
        brightness = overlay ? 100 : MenuBackgroundOptions.level(prefs.getInt(MenuBackgroundOptions.BRIGHTNESS, 50));
        opacity = MenuBackgroundOptions.level(prefs.getInt(MenuBackgroundOptions.OVERLAY_OPACITY, 50));
        frozen = prefs.getBoolean(overlay ? MenuBackgroundOptions.OVERLAY_FROZEN : MenuBackgroundOptions.FROZEN, false);
        phase = frozen ? 0 : clock.phase();
        String nextPalette = prefs.getString(overlay ? MenuBackgroundOptions.OVERLAY_PALETTE : MenuBackgroundOptions.PALETTE, "");
        if (!paletteId.equals(nextPalette)) {
            paletteId = nextPalette;
            palette = HolidayThemes.find(paletteId);
            updatePalette(getWidth(), getHeight());
        }
        if (style == 18) {
            int color = light(0xffd9e8ef);
            if (snowflakeSprite == null || snowflakeColor != color) {
                snowflakeColor = color;
                snowflakeSprite = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888);
                Canvas sprite = new Canvas(snowflakeSprite);
                sprite.translate(48, 48); sprite.scale(42, 42);
                Paint spritePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                spritePaint.setColor(color); spritePaint.setStyle(Paint.Style.STROKE);
                spritePaint.setStrokeCap(Paint.Cap.ROUND); spritePaint.setStrokeWidth(.065f);
                sprite.drawPath(snowflake, spritePaint);
            }
        }
        if (style == 17 || style == 23) {
            int color = accent(style == 23 ? 0xffc37443 : 0xff9bc5df);
            if (seasonalGlow == null || seasonalGlowColor != color) {
                seasonalGlowColor = color;
                seasonalGlow = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888);
                Paint spritePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
                spritePaint.setShader(new RadialGradient(32, 32, 32,
                        new int[]{color, (color & 0xffffff) | 0x44000000, color & 0xffffff},
                        new float[]{0, .3f, 1}, Shader.TileMode.CLAMP));
                new Canvas(seasonalGlow).drawCircle(32, 32, 32, spritePaint);
            }
        }
        invalidate();
    }

    private void updateRunning() {
        Choreographer.getInstance().removeFrameCallback(frame);
        boolean running = (mainLayer ? mainResumed : settingsResumed) && isAttachedToWindow()
                && getWindowVisibility() == VISIBLE && getVisibility() == VISIBLE
                && hasWindowFocus() && !frozen && MenuBackgroundOptions.animated(style);
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

    private void updatePalette(int w, int h) {
        if (palette == null || w <= 0 || h <= 0) { holidayBase = null; holidayGlow = null; return; }
        holidayBase = new LinearGradient(0, h * .1f, w, h,
                new int[]{palette.dark, palette.middle, palette.dark},
                new float[]{0, .68f, 1}, Shader.TileMode.CLAMP);
        holidayRibbon = new LinearGradient(0, 0, w, h,
                new int[]{palette.accent, palette.light, palette.accent}, null, Shader.TileMode.CLAMP);
        holidayParticle = new RadialGradient(0, 0, 1,
                new int[]{palette.light, (palette.accent & 0xffffff) | 0x55000000, palette.accent & 0xffffff},
                new float[]{0, .35f, 1}, Shader.TileMode.CLAMP);
        holidayGlow = new RadialGradient(w * .78f, h * .22f, Math.max(w, h) * .7f,
                new int[]{palette.accent, palette.accent & 0x00ffffff}, null, Shader.TileMode.CLAMP);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        updatePalette(w, h);
        base = new LinearGradient(0, 0, w, h, new int[]{0xff090e22, 0xff14213d, 0xff100d27}, null, Shader.TileMode.CLAMP);
        ribbon = new LinearGradient(0, 0, w, h, new int[]{0xff44c7d2, 0xff7b61ff, 0xffb7c8e4}, null, Shader.TileMode.CLAMP);
        water = new LinearGradient(0, 0, w, h, new int[]{0xff226c91, 0xff55b9b1, 0xff6e8ebd}, null, Shader.TileMode.CLAMP);
        coolGlow = new RadialGradient(0, 0, 1, new int[]{0xffa9c9df, 0x553c8bab, 0x002b487d},
                new float[]{0, .35f, 1}, Shader.TileMode.CLAMP);
        warmGlow = new RadialGradient(0, 0, 1, new int[]{0xffffd6a0, 0x88c37443, 0x005e3542},
                new float[]{0, .25f, 1}, Shader.TileMode.CLAMP);
        int[][] palettes = {
            {0xff0b1421, 0xff203951, 0xff101a29, 0xff7498ad},
            {0xff171719, 0xff35302e, 0xff17181c, 0xffae9382},
            {0xff0b1a18, 0xff24443e, 0xff101b20, 0xff7eac9b},
            {0xff171220, 0xff3a2b44, 0xff171a2b, 0xffa294b4}
        };
        for (int i = 0; i < palettes.length; i++) {
            int[] palette = palettes[i];
            stillBase[i] = new LinearGradient(0, h * .15f, w, h,
                    new int[]{palette[0], palette[1], palette[2]},
                    new float[]{0, .65f, 1}, Shader.TileMode.CLAMP);
            // Keep the navigation edge dark; broad, soft light falls behind the artwork.
            stillLight[i] = new RadialGradient(w * .78f, h * .28f,
                    Math.max(w, h) * .72f,
                    new int[]{palette[3], palette[3] & 0x00ffffff}, null,
                    Shader.TileMode.CLAMP);
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (style == 0) return;
        float w = getWidth(), h = getHeight();
        paint.setStyle(Paint.Style.FILL);
        alpha(255);
        if (style >= 13 && style <= 16) {
            int selected = style - 13;
            if (!overlay) {
                paint.setShader(holidayBase == null ? stillBase[selected] : holidayBase);
                canvas.drawRect(0, 0, w, h, paint);
            }
            paint.setShader(holidayGlow == null ? stillLight[selected] : holidayGlow);
            alpha(12 + intensity / 2);
            canvas.drawRect(0, 0, w, h, paint);
            if (!overlay) shade(canvas, w, h);
            return;
        }
        if (!overlay) {
            paint.setShader(holidayBase == null ? base : holidayBase);
            canvas.drawRect(0, 0, w, h, paint);
            if (holidayGlow != null) {
                paint.setShader(holidayGlow);
                alpha(28);
                canvas.drawRect(0, 0, w, h, paint);
            }
        }
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
        else if (style >= 17) drawSeasonal(canvas, w, h);
        else drawParticles(canvas, w, h);
        if (!overlay) shade(canvas, w, h);
    }

    private void shade(Canvas canvas, float w, float h) {
        paint.setShader(null);
        paint.setColor(Color.BLACK);
        alpha(130 - brightness);
        canvas.drawRect(0, 0, w, h, paint);
    }

    private void drawParticles(Canvas canvas, float w, float h) {
        int count = 16 + intensity / 2;
        paint.setStrokeWidth(Math.max(1, w / 1920));
        for (int i = 0; i < count; i++) {
            float px = (float) (((x[i] + phase * (0.06f + i % 4 * 0.01f)) % 1f) * w);
            float py = ((y[i] + (float)Math.sin(phase + i) * 0.025f + 1) % 1f) * h;
            paint.setColor(i % 3 == 0 ? accent(0xff44c7d2) : light(0xffb7c8e4));
            alpha(45 + brightness);
            canvas.drawCircle(px, py, (1 + i % 3) * w / 1280, paint);
            if (style == 3 && i >= 5) {
                float qx = (float) (((x[i - 5] + phase * (0.06f + (i - 5) % 4 * 0.01f)) % 1f) * w);
                float qy = ((y[i - 5] + (float)Math.sin(phase + i - 5) * 0.025f + 1) % 1f) * h;
                if (Math.abs(px - qx) < w * 0.2f && Math.abs(py - qy) < h * 0.25f) {
                    alpha(18 + brightness / 2);
                    canvas.drawLine(px, py, qx, qy, paint);
                }
            }
        }
    }

    private void drawRibbons(Canvas canvas, float w, float h) {
        paint.setShader(palette == null ? ribbon : holidayRibbon);
        paint.setStyle(Paint.Style.STROKE);
        for (int band = 0; band < 3; band++) {
            path.reset();
            for (int step = 0; step <= 32; step++) {
                float px = step * w / 32;
                float py = h * (0.3f + band * 0.16f) + (float)Math.sin(step / 8f + phase * 0.4f + band) * h * 0.14f;
                if (step == 0) path.moveTo(px, py); else path.lineTo(px, py);
            }
            paint.setStrokeWidth(h * (0.025f + intensity / 3000f));
            alpha(20 + brightness / 3);
            canvas.drawPath(path, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
    }

    private void drawPrism(Canvas canvas, float w, float h) {
        paint.setShader(palette == null ? ribbon : holidayRibbon);
        for (int facet = 0; facet < 5; facet++) {
            float center = w * (0.15f + facet * 0.2f) + (float)Math.sin(phase * 0.25f + facet) * w * 0.08f;
            path.reset();
            path.moveTo(center - w * 0.17f, h);
            path.lineTo(center + w * 0.08f, 0);
            path.lineTo(center + w * 0.21f, 0);
            path.lineTo(center - w * 0.02f, h);
            path.close();
            alpha(10 + brightness / 5 + intensity / 8);
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
        paint.setShader(palette == null ? ribbon : holidayRibbon);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int band = 0; band < 4; band++) {
            curve(w, h * (.23f + band * .17f), h * (.12f + intensity / 2500f), phase * .32, band);
            paint.setStrokeWidth(h * (.025f + band * .009f));
            alpha(18 + brightness / 3);
            canvas.drawPath(path, paint);
            paint.setStrokeWidth(Math.max(1, w / 1280));
            alpha(36 + brightness / 2);
            canvas.drawPath(path, paint);
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
    }

    private void glow(Canvas canvas, float px, float py, float rx, float ry, Shader shader, int alpha) {
        paint.setShader(palette == null ? shader : holidayParticle);
        alpha(alpha);
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
            paint.setColor(i % 5 == 0 ? accent(0xff83b8d4) : light(0xffd2dce7));
            alpha(Math.round((32 + brightness) * MenuBackgroundMotion.envelope(z)));
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
            paint.setColor(i % 3 == 0 ? light(0xffe6b184) : accent(0xffb87958));
            alpha(Math.round((42 + brightness) * MenuBackgroundMotion.envelope(age)));
            canvas.drawCircle(px, py, radius, paint);
        }
    }

    private void drawWaves(Canvas canvas, float w, float h) {
        paint.setShader(palette == null ? water : holidayRibbon);
        paint.setStyle(Paint.Style.STROKE);
        int count = 7 + intensity / 5;
        for (int band = 0; band < count; band++) {
            curve(w, h * (.2f + band * .6f / count), h * .13f, phase * .28 + band * .07, 0);
            paint.setStrokeWidth(w / 1920 * (band % 5 == 0 ? 2.2f : 1.1f));
            alpha(25 + brightness / 2);
            canvas.drawPath(path, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
    }

    private void drawRain(Canvas canvas, float w, float h) {
        paint.setColor(light(0xffadc2db));
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 24 + intensity / 2; i++) {
            float depth = MenuBackgroundMotion.seed(i, 12);
            float age = MenuBackgroundMotion.wrap(MenuBackgroundMotion.seed(i, 13)
                    + phase * (.12 + depth * .18));
            float px = w * MenuBackgroundMotion.wrap(MenuBackgroundMotion.seed(i, 14) - age * .055);
            float py = h * age;
            alpha(Math.round((20 + brightness * .65f) * MenuBackgroundMotion.envelope(age)));
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
            paint.setColor(accent(0xff7398bc));
            alpha(18 + brightness / 3);
            paint.setStrokeWidth(Math.max(1, w / 1600));
            canvas.drawOval(-rx, -ry, rx, ry, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(light(0xffbdd8df));
            alpha(45 + brightness);
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
    private void alpha(int value) {
        paint.setAlpha(Math.max(0, Math.min(255, overlay ? value * opacity / 100 : value)));
    }

    private int accent(int fallback) { return palette == null ? fallback : palette.accent; }
    private int light(int fallback) { return palette == null ? fallback : palette.light; }

    private static Path snowflakePath() {
        Path crystal = new Path();
        android.graphics.Matrix rotation = new android.graphics.Matrix();
        Path arm = new Path();
        arm.moveTo(0, 0); arm.lineTo(0, -.95f);
        arm.moveTo(-.22f, -.74f); arm.lineTo(0, -.52f); arm.lineTo(.22f, -.74f);
        arm.moveTo(-.13f, -.88f); arm.lineTo(0, -.75f); arm.lineTo(.13f, -.88f);
        for (int i = 0; i < 6; i++) {
            rotation.setRotate(i * 60);
            crystal.addPath(arm, rotation);
        }
        return crystal;
    }

    private static Path leafVeinPath() {
        Path veins = new Path();
        veins.moveTo(0, -1); veins.lineTo(.05f, 1.2f);
        for (int i = 0; i < 4; i++) {
            float line = -.6f + i * .35f;
            veins.moveTo(-.46f, line - .2f); veins.lineTo(0, line); veins.lineTo(.4f, line - .3f);
        }
        return veins;
    }

    private void drawSeasonal(Canvas canvas, float w, float h) {
        int count = style == 17 ? 24 + intensity : 8 + intensity / 3;
        for (int i = 0; i < count; i++) {
            float depth = MenuBackgroundMotion.seed(i, 30);
            float age = MenuBackgroundMotion.wrap(MenuBackgroundMotion.seed(i, 31)
                    + phase * (.018 + depth * .04));
            boolean rises = style == 21 || style == 23;
            float py = (rises ? 1 - age : age) * (h + h * .1f) - h * .05f;
            float px = w * MenuBackgroundMotion.seed(i, 32)
                    + (float) Math.sin(phase * (.35 + depth * .25) + i * 2.41) * w * (.008f + depth * .02f);
            float scale = h * (style == 17 ? .0015f + depth * .003f : .007f + depth * .012f);
            float fade = Math.min(1, Math.min(age, 1 - age) * 12);
            int opacity = Math.round((65 + depth * 145) * fade);
            if (style == 17 || style == 23) {
                float radius = scale * 3;
                glowBounds.set(px - radius, py - radius, px + radius, py + radius);
                paint.setShader(null);
                paint.setColor(Color.WHITE);
                alpha(opacity / 2);
                canvas.drawBitmap(seasonalGlow, null, glowBounds, paint);
                paint.setColor(light(style == 23 ? 0xfff3d1a0 : 0xffe5eef4));
                alpha(opacity);
                canvas.drawCircle(px, py, scale * (style == 23 ? .65f : .45f), paint);
                continue;
            }
            canvas.save();
            canvas.translate(px, py);
            canvas.rotate((float) (i * 137.5 + Math.sin(phase * .4 + i) * 20
                    + (style == 18 ? phase * 12 : phase * 5)));
            canvas.scale(scale, scale);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(light(0xffd9e8ef));
            alpha(opacity);
            if (style == 18) {
                glowBounds.set(-48f / 42, -48f / 42, 48f / 42, 48f / 42);
                paint.setFilterBitmap(true);
                canvas.drawBitmap(snowflakeSprite, null, glowBounds, paint);
            } else {
                float roll = .3f + .7f * Math.abs((float) Math.cos(phase * .3 + i * 1.7));
                canvas.scale(roll, 1);
                path.reset();
                if (style == 19) {
                    path.moveTo(0, -1.1f);
                    path.cubicTo(-.35f, -.7f, -.9f, -.65f, -.75f, -.12f);
                    path.cubicTo(-.85f, .55f, -.15f, .75f, .05f, 1.05f);
                    path.cubicTo(.3f, .6f, .88f, .26f, .65f, -.2f);
                    path.cubicTo(.8f, -.65f, .22f, -.82f, 0, -1.1f);
                    paint.setColor(i % 3 == 0 ? light(0xffe4be78) : accent(0xffc88858));
                } else if (style == 20 || style == 21) {
                    path.moveTo(0, -.48f);
                    path.cubicTo(-.82f, -1.35f, -1.5f, .05f, 0, 1.15f);
                    path.cubicTo(1.5f, .05f, .82f, -1.35f, 0, -.48f);
                    paint.setColor(i % 3 == 0 ? light(0xffe9b8c9) : accent(0xffbc718d));
                } else {
                    path.moveTo(0, -1);
                    path.cubicTo(-1.1f, -.5f, -.75f, .7f, 0, 1);
                    path.cubicTo(.9f, .4f, .75f, -.65f, 0, -1);
                    paint.setColor(i % 3 == 0 ? light(0xffecd4de) : accent(0xffc7a3bd));
                }
                path.close();
                alpha(opacity);
                canvas.drawPath(path, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(.04f);
                paint.setColor(light(0xfff4e2d5));
                alpha(opacity / 2);
                if (style == 19) {
                    canvas.drawPath(leafVeins, paint);
                } else canvas.drawPath(path, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            canvas.restore();
        }
    }

}
