package com.jeremykenedy.firetv.ui.nativeapp.widgetgroups;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Typeface;
import android.os.Build;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Retained grouped clock with a minute dial around its time display. */
public final class LegacyClockView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SharedPreferences preferences;
    private final Typeface clockTypeface;
    private boolean started;
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { invalidate(); }
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener changed =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override public void onSharedPreferenceChanged(SharedPreferences values, String key) {
                    if ("use_24h_clock".equals(key)) invalidate();
                }
            };
    public LegacyClockView(Context context) {
        super(context);
        preferences = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE);
        clockTypeface = getResources().getFont(com.jeremykenedy.firetv.ui.preview.R.font.big_shoulders_display_medium);
    }
    public String timeText() {
        Object saved = preferences.getAll().get("use_24h_clock");
        boolean use24 = saved instanceof Boolean ? (Boolean) saved : android.text.format.DateFormat.is24HourFormat(getContext());
        return new SimpleDateFormat(use24 ? "HH:mm" : "h:mm", Locale.getDefault()).format(new java.util.Date());
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth(), height = getHeight(), density = getResources().getDisplayMetrics().density;
        int color = WidgetAppearance.groupColor(getContext(), true);
        float inset = 8 * density, radius = 20 * density;
        float left = inset, right = width - inset, top = inset, bottom = height - inset;
        radius = Math.min(radius, Math.min(right - left, bottom - top) / 4);
        Path path = new Path();
        path.moveTo(width / 2, top); path.lineTo(right - radius, top); path.quadTo(right, top, right, top + radius);
        path.lineTo(right, bottom - radius); path.quadTo(right, bottom, right - radius, bottom);
        path.lineTo(left + radius, bottom); path.quadTo(left, bottom, left, bottom - radius);
        path.lineTo(left, top + radius); path.quadTo(left, top, left + radius, top); path.close();
        PathMeasure perimeter = new PathMeasure(path, false);
        float[] point = new float[2], tangent = new float[2];
        paint.setStrokeWidth(3 * density); paint.setStrokeCap(Paint.Cap.ROUND);
        int minute = Calendar.getInstance().get(Calendar.MINUTE);
        for (int i = 0; i < 60; i++) {
            perimeter.getPosTan(perimeter.getLength() * i / 60f, point, tangent);
            paint.setColor(i < minute ? color : (color & 0xffffff) | Math.round(((color >>> 24) & 255) * .2f) << 24);
            float length = 10 * density;
            canvas.drawLine(point[0], point[1], point[0] - tangent[1] * length, point[1] + tangent[0] * length, paint);
        }
        String time = timeText();
        paint.setColor(color); paint.setTypeface(clockTypeface);
        paint.setTextSize(Math.min(height * .355f, width * .46f));
        float available = width - 36 * density;
        if (paint.measureText(time) > available) paint.setTextSize(paint.getTextSize() * available / paint.measureText(time));
        Paint.FontMetrics metrics = paint.getFontMetrics();
        canvas.drawText(time, (width - paint.measureText(time)) / 2, (height - metrics.ascent - metrics.descent) / 2, paint);
        setContentDescription(time);
    }
    void start() {
        if (started) return;
        IntentFilter filter = new IntentFilter(); filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED); filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) getContext().registerReceiver(changes, filter, Context.RECEIVER_NOT_EXPORTED);
        else getContext().registerReceiver(changes, filter);
        preferences.registerOnSharedPreferenceChangeListener(changed);
        started = true; invalidate();
    }
    void stop() {
        if (!started) return;
        started = false; getContext().unregisterReceiver(changes);
        preferences.unregisterOnSharedPreferenceChangeListener(changed);
    }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
}
