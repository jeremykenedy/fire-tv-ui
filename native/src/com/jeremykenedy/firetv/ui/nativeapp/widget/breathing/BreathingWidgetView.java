package com.jeremykenedy.firetv.ui.nativeapp.widget.breathing;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import java.util.Locale;

public final class BreathingWidgetView extends View implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private BreathingSession session;
    private boolean started;
    private final Runnable frame = new Runnable() {
        @Override public void run() {
            if (!started) return;
            long now = SystemClock.elapsedRealtime();
            if (session.running && session.elapsedAt(now) == session.duration * 1000L) {
                session = session.pause(now);
                BreathingPreferences.save(getContext(), session);
            }
            invalidate();
            handler.postDelayed(this, session.running && !session.reducedMotion ? 33 : 1000);
        }
    };

    public BreathingWidgetView(Context context) {
        super(context);
        setTag("breathing:exercise");
        session = BreathingPreferences.read(context);
        paint.setTextAlign(Paint.Align.CENTER);
    }

    public void start() {
        session = BreathingPreferences.read(getContext());
        if (started) { invalidate(); return; }
        started = true;
        BreathingPreferences.preferences(getContext()).registerOnSharedPreferenceChangeListener(this);
        frame.run();
    }

    public void stop() {
        if (!started) return;
        started = false;
        handler.removeCallbacks(frame);
        BreathingPreferences.preferences(getContext()).unregisterOnSharedPreferenceChangeListener(this);
        try {
            session = BreathingPreferences.read(getContext()).pause(SystemClock.elapsedRealtime());
            BreathingPreferences.save(getContext(), session);
        } catch (IllegalArgumentException ignored) {}
        invalidate();
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        try { session = BreathingPreferences.read(getContext()); invalidate(); }
        catch (IllegalArgumentException error) { stop(); }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float x = getWidth() / 2f;
        float scale = Math.min(density, Math.min(getWidth() / 360f, getHeight() / 280f));
        float top = Math.max(0, (getHeight() - 280 * scale) / 2f);
        long now = SystemClock.elapsedRealtime();
        BreathingSession.Phase phase = session.phase(now);
        float radius = (session.reducedMotion ? 44 : 28 + 24 * phase.expansion) * scale;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * scale);
        paint.setColor(0xff597d89);
        canvas.drawCircle(x, top + 85 * scale, 58 * scale, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xff69b5ba);
        canvas.drawCircle(x, top + 85 * scale, radius, paint);
        boolean complete = session.elapsedAt(now) == session.duration * 1000L;
        String title = complete ? "Complete" : session.running ? phase.name : session.elapsed == 0 ? "Breathing break" : "Paused";
        line(canvas, title, x, top + 176 * scale, 25 * scale, 0xffffffff);
        long remaining = session.duration * 1000L - session.elapsedAt(now);
        long seconds = (remaining + 999) / 1000;
        String time = String.format(Locale.getDefault(), "%d:%02d remaining", seconds / 60, seconds % 60);
        String detail = session.running ? ((phase.remaining + 999) / 1000) + " sec · " + time : time;
        line(canvas, detail, x, top + 207 * scale, 18 * scale, 0xffd6e7e9);
        line(canvas, "Select for actions · Silent", x, top + 239 * scale, 16 * scale, 0xffbed4da);
        String description = "Breathing break: " + title + ", " + detail;
        if (!description.contentEquals(getContentDescription() == null ? "" : getContentDescription())) setContentDescription(description);
    }

    private void line(Canvas canvas, String text, float x, float y, float size, int color) {
        paint.setColor(color); paint.setTextSize(size); canvas.drawText(text, x, y, paint);
    }

    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
}
