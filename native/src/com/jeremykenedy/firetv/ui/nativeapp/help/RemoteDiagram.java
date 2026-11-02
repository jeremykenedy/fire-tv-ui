package com.jeremykenedy.firetv.ui.nativeapp.help;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** A schematic of navigation controls, not a particular remote model. */
public final class RemoteDiagram extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public RemoteDiagram(Context context) {
        super(context);
        setContentDescription("Direction ring moves focus. Center Select opens an item; hold it for app actions. Back returns.");
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = Math.min(getWidth() / 520f, getHeight() / 150f);
        canvas.save();
        canvas.scale(scale, scale);
        paint.setColor(0xff94baff);
        canvas.drawCircle(76, 72, 58, paint);
        paint.setColor(0xff202c44);
        canvas.drawCircle(76, 72, 33, paint);
        paint.setColor(0xffffffff);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(17);
        canvas.drawText("Select", 76, 78, paint);
        canvas.drawText("↑", 76, 28, paint);
        canvas.drawText("↓", 76, 123, paint);
        canvas.drawText("‹", 29, 78, paint);
        canvas.drawText("›", 123, 78, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(20);
        canvas.drawText("Directions: move focus", 166, 40, paint);
        canvas.drawText("Select: open / hold: app actions", 166, 77, paint);
        canvas.drawText("Back: return to previous screen", 166, 114, paint);
        canvas.restore();
    }
}
