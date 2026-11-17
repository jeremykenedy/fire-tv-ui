package com.jeremykenedy.firetv.ui.nativeapp.device.updates;

/** Coordinates starts that can arrive while saved settings are still loading. */
final class UpdateBlockStarts {
    private boolean requested;
    private boolean manual;
    private boolean closed;

    synchronized boolean request(boolean isManual) {
        if (closed) return false;
        manual |= isManual;
        if (requested) return false;
        requested = true;
        return true;
    }

    synchronized boolean pending() { return requested && !closed; }
    synchronized boolean manual() { return manual && !closed; }
    synchronized void cancel() { closed = true; requested = false; }

    static boolean automaticAllowed(int api, boolean lockdown) {
        return api >= 27 && !lockdown;
    }
}
