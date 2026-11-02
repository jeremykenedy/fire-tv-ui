package com.jeremykenedy.firetv.ui.nativeapp.appearance;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

public final class FavoritesBackdrop extends Drawable {
    private final Bitmap bitmap;
    private final int color;
    private final float radius;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    public FavoritesBackdrop(Bitmap bitmap, int color, float radius) {
        this.bitmap = bitmap; this.color = color; this.radius = radius;
    }
    @Override public void draw(Canvas canvas) {
        RectF bounds = new RectF(getBounds());
        if (bitmap != null) {
            BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix matrix = new Matrix(); matrix.setScale(bounds.width() / bitmap.getWidth(), bounds.height() / bitmap.getHeight());
            matrix.postTranslate(bounds.left, bounds.top); shader.setLocalMatrix(matrix);
            paint.setShader(shader); paint.setAlpha(255);
            canvas.drawRoundRect(bounds, radius, radius, paint);
        }
        paint.setShader(null); paint.setColor(color);
        canvas.drawRoundRect(bounds, radius, radius, paint);
    }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
