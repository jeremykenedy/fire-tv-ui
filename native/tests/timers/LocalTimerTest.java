package com.jeremykenedy.firetv.ui.nativeapp.widget.timers;

import java.time.Instant;

public final class LocalTimerTest {
    public static void main(String[] args) {
        CountdownEntry entry = CountdownEntry.parse("Launch", "2026-11-01", "01:30", "America/Los_Angeles");
        check(entry.targetMillis == Instant.parse("2026-11-01T08:30:00Z").toEpochMilli(), "Repeated time uses first occurrence.");
        check(entry.localDateTime().equals("2026-11-01 01:30"), "Target renders in its explicit zone.");
        reject(() -> CountdownEntry.parse("Skipped", "2026-03-08", "02:30", "America/Los_Angeles"));
        reject(() -> CountdownEntry.parse("Bad date", "2026-02-30", "12:00", "UTC"));
        reject(() -> CountdownEntry.parse("Bad zone", "2026-11-01", "12:00", "Mars/Unknown"));
        CountdownEntry india = CountdownEntry.parse("Call", "2026-11-01", "14:15", "Asia/Kolkata");
        check(india.targetMillis == Instant.parse("2026-11-01T08:45:00Z").toEpochMilli(), "Fractional offset zone.");
        check(india.remaining(india.targetMillis + 1) == 0, "Past countdown remains reached.");
        check(india.remaining(india.targetMillis - 1000) == 1000, "Countdown follows actual wall date.");

        TimerState timer = TimerState.create("Tea", 60, false);
        check(!timer.sound && !timer.running, "New timers are quiet and ready.");
        timer = timer.resume(1000, 1000000, 3);
        check(timer.remaining(11000, 9000000, 3) == 50000, "Wall clock jumps cannot alter a same-boot running timer.");
        TimerState restarted = new TimerState(timer.label, timer.duration, timer.pausedRemaining, timer.running,
                timer.sound, timer.elapsedDeadline, timer.wallDeadline, timer.boot);
        check(restarted.remaining(21000, 1, 3) == 40000, "Process restart preserves the monotonic deadline.");
        TimerState paused = restarted.pause(21000, 1, 3);
        check(paused.remaining(999999, 99999999, 3) == 40000, "Pause freezes remaining duration.");
        TimerState resumed = paused.resume(1000000, 2000000, 3);
        check(resumed.remaining(1010000, 10, 3) == 30000, "Resume uses remaining duration.");
        TimerState rebooted = restarted.recover(500, 1020000, 4);
        check(rebooted.remaining(1500, 999999999, 4) == 39000, "Reboot recovery reanchors once to the new monotonic clock.");
        check(restarted.recover(500, 2000000, 4).pausedRemaining == 0, "Expired reboot recovery finishes without scheduling an alarm.");
        TimerState restricted = restarted.recover(500, 1020000, -1234);
        check(restricted.remaining(1500, 999999999, -1234) == 39000, "Active restricted-device timer uses elapsed time.");
        check(restricted.recover(3000, 1030000, -4567).remaining(4000, 1, -4567) == 29000,
                "Restricted-device process restart reanchors from the saved wall deadline.");
        check(resumed.pause(9999999, 10, 3).pausedRemaining == 0, "Elapsed timer finishes at zero.");
        check(resumed.reset().pausedRemaining == 60000 && !resumed.reset().running, "Reset returns to the saved duration.");
        check(TimerState.format(999).equals("00:00:01"), "Display rounds remaining fractions up.");
        reject(() -> TimerState.create("Tea", 0, false));
        reject(() -> TimerState.create("Tea", 604801, false));
        reject(() -> TimerState.create("\n", 60, false));
        System.out.println("LocalTimerTest: behavioral checks passed");
    }

    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
    private static void reject(Runnable action) {
        try { action.run(); throw new AssertionError("Invalid value accepted"); }
        catch (IllegalArgumentException expected) {}
    }
}
