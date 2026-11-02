package com.jeremykenedy.firetv.ui.nativeapp.holidays;

/** Low river mist stays within its authored landscape bands without a visible loop reset. */
public final class WinterMist {
    private float phase;

    public void reset() { phase = 0; }

    public void advance(long deltaMillis, int speed) {
        float seconds = Math.max(0, Math.min(100, deltaMillis)) / 1000f;
        float rate = .45f + Math.max(0, Math.min(100, speed)) * .018f;
        phase = (phase + seconds * rate * .13f) % ((float) Math.PI * 2);
    }

    public float offsetX(int band) { return .014f * (float) Math.sin(phase + band * 1.3f); }
    public float offsetY(int band) { return .003f * (float) Math.sin(phase * 2 + band * 1.3f); }
    public int opacity(int band) { return Math.round(21 + 7 * (float) Math.sin(phase + band * 1.3f)); }
}
