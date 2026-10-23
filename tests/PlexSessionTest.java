package com.jeremykenedy.firetv.ui;

public final class PlexSessionTest {
    public static void main(String[] args) throws Exception {
        long previous = PlexSession.begin();
        long disconnect = PlexSession.begin();
        final int[] saved = {0};
        if (PlexSession.commit(previous, () -> saved[0]++))
            throw new AssertionError("A late pairing request must not reconnect after disconnect.");
        if (saved[0] != 0 || PlexSession.current(previous))
            throw new AssertionError("An obsolete request must stay cancelled.");
        if (!PlexSession.commit(disconnect, () -> saved[0]++))
            throw new AssertionError("The current request must be committed.");
        if (saved[0] != 1)
            throw new AssertionError("The accepted connection change must run exactly once.");
        long firstServer = PlexSession.begin();
        long secondServer = PlexSession.begin();
        if (PlexSession.commit(
                firstServer,
                () -> {
                    throw new AssertionError("Old server result committed.");
                })) throw new AssertionError();
        if (!PlexSession.current(secondServer))
            throw new AssertionError("The newer server choice must remain current.");
    }
}
