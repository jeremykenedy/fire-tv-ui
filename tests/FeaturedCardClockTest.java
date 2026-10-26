package com.jeremykenedy.firetv.ui;

public final class FeaturedCardClockTest {
    public static void main(String[] args) {
        android.content.Context context = new android.content.Context();
        final int[] changes = {0};
        Runnable changed = new Runnable() { public void run() { changes[0]++; } };
        context.prefs.value = 0;
        FeaturedCardClock.start(context, changed);
        check(android.os.Handler.pending == null, "Manual schedules no automatic rotation");
        context.prefs.value = 5;
        FeaturedCardClock.start(context, changed);
        check(android.os.Handler.delay == 5000, "Saved five-second interval reaches the actual clock");
        android.os.Handler.pending.run();
        check(FeaturedGroups.ticks == 1, "One deadline advances titles once");
        FeaturedCardClock.pause();
        check(android.os.Handler.pending == null, "Card menu pauses title changes");
        FeaturedCardClock.changed();
        check(FeaturedGroups.ticks == 1, "Manual title notification does not advance other feeds");
        FeaturedCardClock.resume();
        check(android.os.Handler.pending != null, "Dismiss resumes a fresh full interval");
        FeaturedCardClock.stop();
        check(android.os.Handler.pending == null, "Leaving Main cancels its clock");
        FeaturedCardClock.resume();
        check(android.os.Handler.pending == null, "Menu dismissal off Main cannot restart animation");
        System.out.println("Main-only card clock and menu snapshot checks passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
