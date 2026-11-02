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
    public final boolean fillEnabled;
    public final int fillRgb;
    public final int fillOpacity;

    public FocusOptions(int durationMillis, int growthPercent, boolean borderEnabled,
            int borderRgb, int borderOpacity) {
        this(durationMillis, growthPercent, borderEnabled, borderRgb, borderOpacity,
                true, 0x3c3251, 100);
    }

    public FocusOptions(int durationMillis, int growthPercent, boolean borderEnabled,
            int borderRgb, int borderOpacity, boolean fillEnabled, int fillRgb, int fillOpacity) {
        if (durationMillis < 0 || durationMillis > 500 || growthPercent < 0
                || growthPercent > MAX_GROWTH || borderRgb < 0 || borderRgb > 0xffffff
                || borderOpacity < 0 || borderOpacity > 100
                || fillRgb < 0 || fillRgb > 0xffffff || fillOpacity < 0 || fillOpacity > 100)
            throw new IllegalArgumentException("Choose a supported focus setting.");
        this.durationMillis = durationMillis;
        this.growthPercent = growthPercent;
        this.borderEnabled = borderEnabled;
        this.borderRgb = borderRgb;
        this.borderOpacity = borderOpacity;
        this.fillEnabled = fillEnabled;
        this.fillRgb = fillRgb;
        this.fillOpacity = fillOpacity;
    }

    public static FocusOptions defaults() {
        return new FocusOptions(FAST_MILLIS, 6, true, 0xffffff, 100);
    }

    public int borderArgb() {
        return (Math.round(borderOpacity * 255f / 100f) << 24) | borderRgb;
    }

    public int surfaceRgb(float progress) {
        float amount = fillEnabled ? fillOpacity / 100f * Math.max(0f, Math.min(1f, progress)) : 0f;
        int color = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int base = (0x251f39 >> shift) & 255;
            int target = (fillRgb >> shift) & 255;
            color |= Math.round(base + (target - base) * amount) << shift;
        }
        return 0xff000000 | color;
    }

    public float scale(float progress) {
        return 1f + growthPercent * Math.max(0f, Math.min(1f, progress)) / 100f;
    }

    public static int growthInset(int dimension) {
        return (int) Math.ceil(Math.max(0, dimension) * MAX_GROWTH / (200f + 2f * MAX_GROWTH));
    }
}
