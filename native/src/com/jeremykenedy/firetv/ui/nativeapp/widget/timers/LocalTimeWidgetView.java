package com.jeremykenedy.firetv.ui.nativeapp.widget.timers;

import android.content.Context;
import android.media.ToneGenerator;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class LocalTimeWidgetView extends LinearLayout {
    private final boolean timer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TextView label;
    private final TextView value;
    private final TextView detail;
    private boolean started;
    private ToneGenerator tone;
    private final Runnable releaseTone = new Runnable() {
        @Override public void run() { if (tone != null) { tone.release(); tone = null; } }
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!started) return;
            render();
            handler.postDelayed(this, 1000);
        }
    };

    public LocalTimeWidgetView(Context context, boolean timer) {
        super(context);
        this.timer = timer;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        setPadding(padding, padding, padding, padding);
        label = text(22, 0xffffffff, 2);
        value = text(32, 0xffffffff, 1);
        detail = text(16, 0xffc8bfdc, 3);
        setTag(timer ? "local:timer" : "local:countdown");
        render();
    }

    private TextView text(int size, int color, int lines) {
        TextView text = new TextView(getContext());
        text.setTextSize(size);
        text.setTextColor(color);
        text.setMaxLines(lines);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        addView(text, new LayoutParams(-1, -2));
        return text;
    }

    public void start() {
        if (started) { render(); return; }
        started = true;
        tick.run();
    }

    public void stop() {
        started = false;
        handler.removeCallbacks(tick);
        handler.removeCallbacks(releaseTone);
        releaseTone.run();
    }

    public boolean isRefreshing() { return started; }

    private void render() {
        if (!timer) {
            CountdownEntry entry = TimerPreferences.countdown(getContext());
            label.setText(entry == null ? "Countdown" : entry.label);
            value.setText(entry == null ? "Set a date" : entry.remaining(System.currentTimeMillis()) == 0
                    ? "Target reached" : TimerState.format(entry.remaining(System.currentTimeMillis())));
            detail.setText(entry == null ? "Select to configure" : entry.localDateTime() + " " + entry.zone);
            return;
        }
        TimerState state = TimerPreferences.timer(getContext());
        label.setText(state == null ? "Timer" : state.label);
        if (state == null) { value.setText("Set a timer"); detail.setText("Select to configure"); return; }
        long remaining = state.remaining(SystemClock.elapsedRealtime(), System.currentTimeMillis(), TimerPreferences.boot(getContext()));
        if (started && state.running && remaining == 0) {
            boolean saved = TimerPreferences.saveTimer(getContext(), state.pause(SystemClock.elapsedRealtime(),
                    System.currentTimeMillis(), TimerPreferences.boot(getContext())));
            if (saved && state.sound) sound();
        }
        value.setText(remaining == 0 ? "Timer finished" : TimerState.format(remaining));
        detail.setText((remaining == 0 ? "Finished" : state.running ? "Running" : state.pausedRemaining == state.duration ? "Ready" : "Paused")
                + " · Sound " + (state.sound ? "on" : "off") + "\nSelect for timer actions");
    }

    private void sound() {
        try {
            releaseTone.run();
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 60);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 300);
            handler.postDelayed(releaseTone, 400);
        } catch (RuntimeException ignored) { releaseTone.run(); }
    }

    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
}
