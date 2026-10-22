package com.jeremykenedy.firetv.ui;

/** One explicit native-home launch, without changing the user's Home setting. */
final class FactoryHomeBypass {
    private long armedAt = -1;
    private long firstHomeAt = -1;

    synchronized void arm(long now) {
        armedAt = now;
        firstHomeAt = -1;
    }

    synchronized void clear() {
        armedAt = -1;
        firstHomeAt = -1;
    }

    synchronized boolean skip(boolean home, boolean leavingHome, long now) {
        if (armedAt < 0 || now < armedAt) return false;
        if (now - armedAt > 10000) {
            clear();
            return false;
        }
        if (!home) {
            if (firstHomeAt >= 0 && leavingHome) clear();
            return false;
        }
        if (firstHomeAt < 0) firstHomeAt = now;
        if (now - firstHomeAt <= 1000) return true;
        clear();
        return false;
    }
}
