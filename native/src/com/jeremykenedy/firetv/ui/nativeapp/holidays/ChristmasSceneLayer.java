package com.jeremykenedy.firetv.ui.nativeapp.holidays;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/** Snow depth and light positions composed specifically for the original Christmas village. */
final class ChristmasSceneLayer {
    private static final float[] LIGHTS = {
        .927f, .078f, .920f, .159f, .948f, .190f, .902f, .256f,
        .958f, .285f, .898f, .336f, .933f, .381f, .976f, .417f,
        .868f, .450f, .930f, .490f, .978f, .533f, .853f, .551f,
        .907f, .592f, .959f, .631f, .848f, .656f, .916f, .704f,
        .980f, .728f, .758f, .539f, .733f, .631f, .477f, .640f
    };
    private final SnowfallRenderer snow = new SnowfallRenderer(0x4348524953544d41L, false);
    private final Paint light = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float phase;

    ChristmasSceneLayer() {
        light.setShader(new RadialGradient(0, 0, 1, new int[]{0xc0ffeab1, 0x50ffc56f, Color.TRANSPARENT},
                new float[]{0, .3f, 1}, Shader.TileMode.CLAMP));

    }

    void reset() { phase = 0; snow.reset(); }

    void advance(long deltaMillis, int speed) {
        long bounded = Math.max(0, Math.min(100, deltaMillis));
        snow.advance(bounded, speed);
        phase = (phase + bounded * .0006f) % ((float) Math.PI * 2);
    }

    void draw(Canvas canvas, RectF scene, int viewportWidth, int viewportHeight) {
        for (int index = 0; index < LIGHTS.length / 2; index++) {
            float brightness = .65f + .15f * (float) Math.sin(phase + index * 1.9f);
            int checkpoint = canvas.save();
            canvas.translate(scene.left + scene.width() * LIGHTS[index * 2],
                    scene.top + scene.height() * LIGHTS[index * 2 + 1]);
            float size = scene.width() * (index < 17 ? .009f : .006f);
            canvas.scale(size, size);
            light.setAlpha(Math.round(95 * brightness));
            canvas.drawCircle(0, 0, 1, light);
            canvas.restoreToCount(checkpoint);
        }
        snow.draw(canvas, viewportWidth, viewportHeight);
    }
}
