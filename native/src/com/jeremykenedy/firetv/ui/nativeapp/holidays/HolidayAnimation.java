package com.jeremykenedy.firetv.ui.nativeapp.holidays;

/** A bounded scene timeline, advanced only while its native view is active. */
public final class HolidayAnimation {
    private long elapsedMillis;
    private int speed = 50;

    public void setSpeed(int value) {
        int selected = Math.max(0, Math.min(100, value));
        if (speed == selected) return;
        float progress = progress();
        speed = selected;
        elapsedMillis = Math.round(progress * duration());
    }

    public void reset() { elapsedMillis = 0; }

    public void advance(long deltaMillis) {
        elapsedMillis = (elapsedMillis + Math.max(0, Math.min(100, deltaMillis))) % duration();
    }

    public int frame() { return (int) ((elapsedMillis / (220 - speed * 140 / 100)) % 6); }

    public float progress() { return (elapsedMillis % duration()) / (float) duration(); }

    public int opacity() {
        float progress = progress();
        return Math.round(255 * Math.min(1, Math.min(progress / .04f, (1 - progress) / .08f)));
    }

    public float flicker() {
        return .72f + .18f * (float) Math.sin(elapsedMillis * .006)
                + .1f * (float) Math.sin(elapsedMillis * .017);
    }

    private int duration() { return 45000 - speed * 30000 / 100; }
}
