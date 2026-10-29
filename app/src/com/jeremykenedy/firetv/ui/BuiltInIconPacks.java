package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import java.util.LinkedHashMap;

/** Optional tile treatments rendered from each installed application's own artwork. */
public final class BuiltInIconPacks {
    public static final String CLEAN_LINE = "builtin:clean_line";
    public static final String MUTED_SOLID = "builtin:muted_solid";

    private BuiltInIconPacks() {}

    public static boolean supports(String pack) {
        return CLEAN_LINE.equals(pack) || MUTED_SOLID.equals(pack);
    }

    public static String caption(String pack) {
        String label = CLEAN_LINE.equals(pack) ? "Clean line"
                : MUTED_SOLID.equals(pack) ? "Muted solid" : pack;
        return "(" + label + ")";
    }

    public static LinkedHashMap<String, String> mappings(Context context, String pack) {
        if (!supports(pack)) return null;
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (ApplicationInfo app : context.getPackageManager().getInstalledApplications(0)) {
            // The native pack consumer groups component mappings by package.
            result.put(app.packageName + "/", app.packageName);
        }
        return result;
    }

    public static Bitmap render(Context context, String pack, String packageName,
            int width, int height) {
        if (!supports(pack) || width < 1 || height < 1 || width > 2048 || height > 2048) {
            return null;
        }
        try {
            Drawable artwork = context.getPackageManager().getApplicationIcon(packageName);
            return render(artwork, pack, width, height);
        } catch (PackageManager.NameNotFoundException | IllegalArgumentException unavailable) {
            return null;
        }
    }

    static Bitmap render(Drawable artwork, String pack, int width, int height) {
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float inset = Math.min(width, height) * 0.07f;
        RectF plate = new RectF(inset, inset, width - inset, height - inset);
        float radius = Math.min(width, height) * 0.12f;
        boolean muted = MUTED_SOLID.equals(pack);
        paint.setColor(muted ? 0xff29364b : 0xffa9bacb);
        if (!muted) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2f, height * 0.012f));
        }
        canvas.drawRoundRect(plate, radius, radius, paint);

        int originalWidth = Math.max(1, artwork.getIntrinsicWidth());
        int originalHeight = Math.max(1, artwork.getIntrinsicHeight());
        float scale = Math.min(width * 0.74f / originalWidth, height * 0.68f / originalHeight);
        int iconWidth = Math.max(1, Math.round(originalWidth * scale));
        int iconHeight = Math.max(1, Math.round(originalHeight * scale));
        Bitmap icon = Bitmap.createBitmap(iconWidth, iconHeight, Bitmap.Config.ARGB_8888);
        Rect bounds = new Rect(artwork.getBounds());
        try {
            artwork.setBounds(0, 0, iconWidth, iconHeight);
            artwork.draw(new Canvas(icon));
        } finally {
            artwork.setBounds(bounds);
        }
        paint.reset();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        if (muted) {
            ColorMatrix colors = new ColorMatrix();
            colors.setSaturation(0.45f);
            paint.setColorFilter(new ColorMatrixColorFilter(colors));
        }
        canvas.drawBitmap(icon, (width - iconWidth) / 2f, (height - iconHeight) / 2f, paint);
        icon.recycle();
        return result;
    }
}
