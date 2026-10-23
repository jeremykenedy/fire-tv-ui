package com.jeremykenedy.firetv.ui;

/** Serialize connection changes against older pairing and refresh requests. */
final class PlexSession {
    private static long generation;

    private PlexSession() {}

    static synchronized long begin() {
        return ++generation;
    }

    static synchronized boolean current(long request) {
        return generation == request;
    }

    static synchronized void cancel(long request) {
        if (request != 0 && generation == request) generation++;
    }

    static synchronized boolean commit(long request, Change change) throws Exception {
        if (generation != request) return false;
        change.apply();
        return true;
    }

    interface Change {
        void apply() throws Exception;
    }
}
