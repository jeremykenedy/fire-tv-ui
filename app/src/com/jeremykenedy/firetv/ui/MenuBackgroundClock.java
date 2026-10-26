package com.jeremykenedy.firetv.ui;

/** Pauses scene time without catching up when a menu becomes visible again. */
final class MenuBackgroundClock {
    private boolean running, sampled;
    private long previous;
    private double phase;

    void resumed(boolean value) {
        if (running != value) sampled = false;
        running = value;
    }

    boolean frame(long nanos, int speed) {
        if (!running) return false;
        if (!sampled) {
            previous = nanos;
            sampled = true;
        } else if (nanos > previous) {
            phase += Math.min(100000000L, nanos - previous) * speed / 500000000000.0;
            previous = nanos;
        }
        return true;
    }

    double phase() {
        return phase;
    }
}
