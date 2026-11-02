package com.jeremykenedy.firetv.ui.nativeapp.holidays;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

/** Cold river mist follows the original secular winter landscape, without holiday decorations. */
final class WinterSceneLayer {
    private final SnowfallRenderer snow = new SnowfallRenderer(0x57494e544552L, true);
    private final WinterMist mist = new WinterMist();
    private final Paint haze = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path[] bands = {new Path(), new Path(), new Path()};

    WinterSceneLayer() {
        haze.setShader(new LinearGradient(0, .58f, 0, .91f,
                new int[]{Color.TRANSPARENT, 0xffe1eeff, 0x80d9e8fa, Color.TRANSPARENT},
                new float[]{0, .25f, .65f, 1}, Shader.TileMode.CLAMP));
        bands[0].moveTo(.40f, .81f);
        bands[0].cubicTo(.57f, .73f, .76f, .82f, 1.04f, .73f);
        bands[0].cubicTo(.88f, .82f, .65f, .81f, .44f, .87f);
        bands[0].close();
        bands[1].moveTo(.50f, .71f);
        bands[1].cubicTo(.62f, .64f, .80f, .72f, .97f, .66f);
        bands[1].cubicTo(.83f, .75f, .63f, .69f, .53f, .76f);
        bands[1].close();
        bands[2].moveTo(.57f, .63f);
        bands[2].cubicTo(.67f, .59f, .77f, .64f, .88f, .60f);
        bands[2].cubicTo(.79f, .67f, .69f, .63f, .59f, .66f);
        bands[2].close();
    }

    void reset() { mist.reset(); snow.reset(); }

    void advance(long deltaMillis, int speed) {
        mist.advance(deltaMillis, speed); snow.advance(deltaMillis, speed);
    }

    void draw(Canvas canvas, RectF scene, int viewportWidth, int viewportHeight) {
        for (int band = 0; band < bands.length; band++) {
            int checkpoint = canvas.save();
            canvas.translate(scene.left + scene.width() * mist.offsetX(band),
                    scene.top + scene.height() * mist.offsetY(band));
            canvas.scale(scene.width(), scene.height());
            haze.setAlpha(mist.opacity(band));
            canvas.drawPath(bands[band], haze);
            canvas.restoreToCount(checkpoint);
        }
        snow.draw(canvas, viewportWidth, viewportHeight);
    }
}
