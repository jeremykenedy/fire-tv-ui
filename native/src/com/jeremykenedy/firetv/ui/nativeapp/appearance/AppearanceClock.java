package com.jeremykenedy.firetv.ui.nativeapp.appearance;

final class AppearanceClock {
    private long previous, elapsed;
    private double phase;
    private boolean started;

    void frame(long now, int speed) {
        if (started) {
            long delta = Math.max(0, Math.min(100, now - previous));
            elapsed += delta;
            phase += delta / 1000.0 * (0.25 + speed / 50.0);
        }
        previous = now;
        started = true;
    }

    void pause() { started = false; }
    long elapsedMillis() { return elapsed; }
    double phaseSeconds() { return phase; }
}
