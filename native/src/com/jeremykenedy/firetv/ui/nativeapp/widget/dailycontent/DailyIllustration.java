package com.jeremykenedy.firetv.ui.nativeapp.widget.dailycontent;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Original vector artwork, without downloaded assets or image decoding. */
final class DailyIllustration extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private DailyContent content;
    DailyIllustration(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    void setContent(DailyContent content) { this.content = content; invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (content == null) return;
        canvas.save(); canvas.scale(getWidth() / 640f, getHeight() / 240f);
        canvas.drawColor(0xff122c3b);
        if (content.kind == DailyContent.Kind.COMIC) comic(canvas);
        else art(canvas);
        canvas.restore();
    }
    private void art(Canvas c) {
        int[] colors = {0xfff4bd76, 0xff65c5bc, 0xffd98298, 0xff92a9e8};
        int accent = colors[DailyContent.index(content.date, colors.length, 0)];
        switch (content.edition) {
            case 0:
                for (int i = 0; i < 8; i++) { paint.setColor(i % 2 == 0 ? accent : 0xff246176); c.drawCircle(i * 100, 230 - i * 17, 120, paint); }
                break;
            case 1:
                paint.setColor(accent);
                for (int y = 0; y < 5; y++) for (int x = 0; x < 13; x++) if ((x + y) % 3 != 0) c.drawRect(x * 50 + 12, y * 48 + 12, x * 50 + 28, y * 48 + 29, paint);
                break;
            case 2:
                for (int i = 0; i < 5; i++) { paint.setColor(colors[i % 4]); Path p = new Path(); p.moveTo(i * 150 - 100, 240); p.lineTo(i * 150 + 80, 25 + i * 19); p.lineTo(i * 150 + 250, 240); p.close(); c.drawPath(p, paint); }
                break;
            case 3:
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(5);
                for (int i = 0; i < 6; i++) { paint.setColor(colors[i % 4]); c.drawCircle(220, 120, 25 + i * 36, paint); }
                paint.setStyle(Paint.Style.FILL); paint.setColor(accent); c.drawCircle(450, 90, 24, paint);
                break;
            case 4:
                for (int x = 0; x < 8; x++) for (int y = 0; y < 3; y++) { paint.setColor(colors[(x + y) % 4]); c.drawOval(x * 80 + 15, y * 80 + 10, x * 80 + 65, y * 80 + 60, paint); paint.setColor(0xff122c3b); c.drawCircle(x * 80 + 40, y * 80 + 35, 12, paint); }
                break;
            case 5:
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(9);
                for (int i = 0; i < 10; i++) { paint.setColor(colors[i % 4]); Path p = new Path(); p.moveTo(-20, i * 30 - 20); p.cubicTo(180, i * 30 + 90, 450, i * 30 - 90, 660, i * 30 + 20); c.drawPath(p, paint); }
                paint.setStyle(Paint.Style.FILL); break;
            default:
                for (int i = 0; i < 10; i++) { paint.setColor(colors[i % 4]); c.drawRect(i * 64 + 6, 220 - i * 18, i * 64 + 58, 240, paint); }
        }
    }
    private void comic(Canvas c) {
        for (int panel = 0; panel < 2; panel++) {
            c.save(); c.translate(panel * 320, 0);
            paint.setColor(0xffc8e5da); c.drawRect(5, 5, 315, 235, paint);
            paint.setColor(0xff53766e); c.drawRect(5, 210, 315, 235, paint);
            paint.setColor(0xff8a949d); c.drawOval(35, 150, 145, 214, paint);
            paint.setColor(0xff213b48); c.drawCircle(76, 174, 4, paint); c.drawCircle(108, 174, 4, paint);
            paint.setStrokeWidth(8); paint.setColor(0xff2a8257); c.drawLine(235, 208, 235, 110, paint);
            c.drawOval(190, 115, 235, 148, paint); c.drawOval(235, 90, 280, 125, paint);
            paint.setColor(0xff213b48); c.drawCircle(225, 143, 3, paint); c.drawCircle(242, 143, 3, paint);
            paint.setTextSize(20); c.drawText(panel == 0 ? "1. Pebble" : "2. Sprout", 22, 37, paint);
            c.restore();
        }
    }
}
