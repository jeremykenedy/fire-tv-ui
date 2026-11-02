package com.jeremykenedy.firetv.ui.nativeapp.appgrid;

/** Geometry for the compact Home dock, measured against the original TV presentation. */
public final class MiniDockLayout {
    private MiniDockLayout() {}

    public static int width(int availablePixels, float density) {
        if (availablePixels < 1 || density <= 0) throw new IllegalArgumentException("Invalid display size.");
        return Math.max(1, Math.min(availablePixels, Math.round(416 * density)));
    }

    public static int cell(float density) { return Math.max(1, Math.round(68 * density)); }
    public static int artwork(float density) { return Math.max(1, Math.round(56 * density)); }
    public static int height(float density) { return Math.max(1, Math.round(80 * density)); }
}
