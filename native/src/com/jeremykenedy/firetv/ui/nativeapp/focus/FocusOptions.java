package com.jeremykenedy.firetv.ui.nativeapp.focus;

/** User-selected visual feedback; keyboard focus never waits for an animation. */
public final class FocusOptions {
    public static final int FAST_MILLIS = 100;
    public static final int MAX_GROWTH = 10;
    public final int durationMillis;
    public final int growthPercent;
    public final boolean borderEnabled;
    public final int borderRgb;
    public final int borderOpacity;

    public FocusOptions(int durationMillis, int growthPercent, boolean borderEnabled,
            int borderRgb, int borderOpacity) {
        if (durationMillis < 0 || durationMillis > 500 || growthPercent < 0
                || growthPercent > MAX_GROWTH || borderRgb < 0 || borderRgb > 0xffffff
                || borderOpacity < 0 || borderOpacity > 100)
            throw new IllegalArgumentException("Choose a supported focus setting.");
        this.durationMillis = durationMillis;
        this.growthPercent = growthPercent;
        this.borderEnabled = borderEnabled;
        this.borderRgb = borderRgb;
        this.borderOpacity = borderOpacity;
    }

    public static FocusOptions defaults() {
        return new FocusOptions(FAST_MILLIS, 6, true, 0xffffff, 100);
    }

    public int borderArgb() {
        return (Math.round(borderOpacity * 255f / 100f) << 24) | borderRgb;
    }

    public float scale(float progress) {
        return 1f + growthPercent * Math.max(0f, Math.min(1f, progress)) / 100f;
    }

    public static int growthInset(int dimension) {
        return (int) Math.ceil(Math.max(0, dimension) * MAX_GROWTH / (200f + 2f * MAX_GROWTH));
    }
}
