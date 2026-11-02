package com.jeremykenedy.firetv.ui.nativeapp.widget.breathing;

public final class BreathingSessionTest {
    public static void main(String[] args) {
        BreathingSession session = BreathingSession.defaults();
        check(!session.running && session.elapsed == 0, "Exercise is opt-in and initially stopped.");
        session = session.start(1000);
        check(session.phase(1000).name.equals("Inhale"), "Starts on inhale.");
        check(session.phase(5000).name.equals("Exhale"), "Zero hold skips directly to exhale.");
        check(session.phase(11000).name.equals("Inhale"), "Zero rest skips directly to next cycle.");
        check(session.phase(3000).expansion > 0.49f && session.phase(3000).expansion < 0.51f, "Inhale expands with calm interpolation.");
        check(session.phase(8000).expansion > 0.49f && session.phase(8000).expansion < 0.51f, "Exhale contracts.");
        BreathingSession paused = session.pause(7000);
        check(paused.elapsedAt(99000) == 6000 && !paused.running, "Pause preserves exact elapsed progress.");
        BreathingSession resumed = paused.start(100000);
        check(resumed.elapsedAt(102000) == 8000, "Resume excludes paused time.");
        check(resumed.reset().elapsed == 0 && !resumed.reset().running, "Reset leaves session paused at zero.");
        check(session.phase(500000).name.equals("Complete") && session.elapsedAt(500000) == 120000, "Duration clamps to session completion.");
        check(session.pause(500000).start(600000).elapsed == 0, "Starting a completed exercise begins anew.");
        BreathingSession holds = new BreathingSession(4, 2, 6, 3, 60, true, 0, 0, true);
        check(holds.phase(4000).name.equals("Hold") && holds.phase(4000).expansion == 1, "Inhale hold stays expanded.");
        check(holds.phase(12000).name.equals("Rest") && holds.phase(12000).expansion == 0, "Exhale rest stays contracted.");
        check(holds.phase(15000).name.equals("Inhale"), "Held cycle repeats at exact boundary.");
        reject(new Runnable() { @Override public void run() { new BreathingSession(1, 0, 6, 0, 60, false, 0, 0, false); } });
        reject(new Runnable() { @Override public void run() { new BreathingSession(4, 9, 6, 0, 60, false, 0, 0, false); } });
        reject(new Runnable() { @Override public void run() { new BreathingSession(4, 0, 6, 0, 1801, false, 0, 0, false); } });
        System.out.println("BreathingSessionTest: behavioral checks passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void reject(Runnable action) {
        try { action.run(); throw new AssertionError("Invalid cadence accepted"); } catch (IllegalArgumentException expected) {}
    }
}
