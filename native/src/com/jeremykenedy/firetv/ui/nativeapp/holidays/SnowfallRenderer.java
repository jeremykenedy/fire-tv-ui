package com.jeremykenedy.firetv.ui.nativeapp.holidays;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

final class SnowfallRenderer {
    private final SceneSnowfall snow;
    private final boolean windLeft;
    private final Paint flake = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint softFlake = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path silhouette = new Path();

    SnowfallRenderer(long seed, boolean windLeft) {
        snow = new SceneSnowfall(seed);
        this.windLeft = windLeft;
        softFlake.setShader(new RadialGradient(0, 0, 1, new int[]{0xdfffffff, 0x70e9f5ff, Color.TRANSPARENT},
                new float[]{0, .4f, 1}, Shader.TileMode.CLAMP));
        flake.setColor(0xffedf6ff);
        flake.setStrokeCap(Paint.Cap.ROUND);
        for (int arm = 0; arm < 6; arm++) {
            double angle = arm * Math.PI / 3;
            float x = (float) Math.cos(angle), y = (float) Math.sin(angle);
            silhouette.moveTo(0, 0); silhouette.lineTo(x, y);
            float branchX = x * .6f, branchY = y * .6f;
            silhouette.moveTo(branchX, branchY);
            silhouette.lineTo(branchX - y * .23f + x * .2f, branchY + x * .23f + y * .2f);
            silhouette.moveTo(branchX, branchY);
            silhouette.lineTo(branchX + y * .23f + x * .2f, branchY - x * .23f + y * .2f);
        }
    }

    void reset() { snow.reset(); }
    void advance(long deltaMillis, int speed) { snow.advance(deltaMillis, speed); }

    void draw(Canvas canvas, int viewportWidth, int viewportHeight) {
        float pixelScale = Math.max(.5f, viewportHeight / 1080f);
        for (int index = 0; index < SceneSnowfall.COUNT; index++) {
            float horizontal = windLeft ? 1 - snow.x(index) : snow.x(index);
            float x = horizontal * (viewportWidth + 24) - 12;
            float y = snow.y(index) * (viewportHeight + 24) - 12;
            float radius = snow.radius(index) * pixelScale;
            int depth = snow.depth(index);
            if (depth == 0) {
                flake.setStyle(Paint.Style.FILL); flake.setAlpha(110);
                canvas.drawCircle(x, y, radius, flake);
            } else {
                int checkpoint = canvas.save();
                canvas.translate(x, y);
                canvas.rotate(windLeft ? -snow.angle(index) : snow.angle(index));
                canvas.scale(radius, radius);
                if (depth == 1) {
                    flake.setStyle(Paint.Style.STROKE); flake.setStrokeWidth(.3f); flake.setAlpha(190);
                    canvas.drawPath(silhouette, flake);
                } else {
                    canvas.drawCircle(0, 0, 1, softFlake);
                }
                canvas.restoreToCount(checkpoint);
            }
        }
    }
}
