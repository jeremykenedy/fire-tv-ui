package com.jeremykenedy.firetv.ui.nativeapp.appearance;

public final class AppearanceClockTest {
    public static void main(String[] args) {
        AppearanceClock clock = new AppearanceClock();
        clock.frame(1000, 50);
        clock.frame(1100, 50);
        check(clock.phaseSeconds(), .125);
        clock.frame(1200, 100);
        check(clock.phaseSeconds(), .35);
        clock.pause();
        clock.frame(10000, 0);
        check(clock.phaseSeconds(), .35);
        clock.frame(11000, 0);
        check(clock.phaseSeconds(), .375);
        if (clock.elapsedMillis() != 300) throw new AssertionError("Elapsed time changed while paused");
        System.out.println("Appearance phase remains continuous across speed and pause changes.");
    }

    private static void check(double actual, double expected) {
        if (Math.abs(actual - expected) > .000001) throw new AssertionError(actual + " != " + expected);
    }
}
