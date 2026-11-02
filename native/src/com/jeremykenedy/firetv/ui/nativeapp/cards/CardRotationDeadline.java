package com.jeremykenedy.firetv.ui.nativeapp.cards;

/** Maintains the current title's deadline across equivalent provider refreshes. */
final class CardRotationDeadline {
    private long deadline, interval;
    private String selection;

    long update(long now, boolean eligible, long nextInterval, String nextSelection) {
        if (!eligible || nextInterval <= 0 || nextSelection == null) {
            reset();
            return 0;
        }
        if (deadline == 0 || interval != nextInterval || !nextSelection.equals(selection)) {
            deadline = now + nextInterval;
            interval = nextInterval;
            selection = nextSelection;
        }
        return deadline;
    }

    void reset() { deadline = 0; interval = 0; selection = null; }
}
