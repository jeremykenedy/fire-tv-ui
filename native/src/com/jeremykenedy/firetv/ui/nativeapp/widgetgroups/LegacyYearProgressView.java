package com.jeremykenedy.firetv.ui.nativeapp.widgetgroups;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/** Year label, whole percent and dot grid for a retained legacy widget. */
public final class LegacyYearProgressView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean started;
    private final Runnable nextDay = new Runnable() { @Override public void run() { if (started) refresh(); } };
    private final BroadcastReceiver changes = new BroadcastReceiver() { @Override public void onReceive(Context context, Intent intent) { refresh(); } };
    public LegacyYearProgressView(Context context) { super(context); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        LegacyYearProgressModel value = LegacyYearProgressModel.at(LocalDate.now());
        setContentDescription("Year " + value.percent + "%");
        float width = getWidth(), height = getHeight();
        float pad = Math.max(8, width * .05f);
        int textColor = WidgetAppearance.groupColor(getContext(), true);
        paint.setColor(textColor); paint.setTextSize(Math.min(width * .11f, height * .16f));
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        canvas.drawText("Year", pad, pad + paint.getTextSize(), paint);
        String percent = value.percent + "%";
        paint.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        canvas.drawText(percent, width - pad - paint.measureText(percent), pad + paint.getTextSize(), paint);
        float left = pad + width * .025f, right = width - pad - width * .025f;
        float top = pad + paint.getTextSize() + height * .17f, bottom = height * .85f;
        float xStep = (right - left) / 10f, yStep = (bottom - top) / 4f;
        float radius = Math.max(2, Math.min(xStep, yStep) * .33f);
        for (int i = 0; i < LegacyYearProgressModel.DOTS; i++) {
            paint.setColor(i < value.filledDots ? textColor : (textColor & 0xffffff) | (Math.round((textColor >>> 24) * .2f) << 24));
            canvas.drawCircle(left + (i % 11) * xStep, top + (i / 11) * yStep, radius, paint);
        }
    }
    void start() {
        if (started) return;
        IntentFilter filter = new IntentFilter(); filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_TIME_CHANGED); filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) getContext().registerReceiver(changes, filter, Context.RECEIVER_NOT_EXPORTED);
        else getContext().registerReceiver(changes, filter);
        started = true; refresh();
    }
    void stop() {
        if (!started) return;
        started = false; handler.removeCallbacks(nextDay); getContext().unregisterReceiver(changes);
    }
    private void refresh() {
        invalidate(); handler.removeCallbacks(nextDay);
        ZonedDateTime now = ZonedDateTime.now();
        long next = now.toLocalDate().plusDays(1).atStartOfDay(now.getZone()).toInstant().toEpochMilli();
        handler.postDelayed(nextDay, Math.max(1, next - now.toInstant().toEpochMilli()));
    }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
}
