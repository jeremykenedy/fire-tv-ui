package com.jeremykenedy.firetv.ui.nativeapp.cards;

public final class CardRotationDeadlineTest {
    public static void main(String[] args) {
        CardRotationDeadline clock = new CardRotationDeadline();
        check(clock.update(1000, true, 8000, "one") == 9000);
        for (int time = 2000; time <= 8000; time += 1000)
            check(clock.update(time, true, 8000, "one") == 9000);
        check(clock.update(8100, true, 5000, "one") == 13100);
        check(clock.update(8200, true, 5000, "two") == 13200);
        check(clock.update(8300, true, 0, "two") == 0);
        check(clock.update(8400, true, 5000, "two") == 13400);
        check(clock.update(8500, false, 5000, "two") == 0);
        check(clock.update(8600, true, 5000, "two") == 13600);
        clock.reset();
        check(clock.update(13600, true, 5000, "one") == 18600);
        System.out.println("Card rotation deadlines survive refresh and reset on selection, interval and lifecycle changes.");
    }
    private static void check(boolean condition) { if (!condition) throw new AssertionError("Unexpected rotation deadline."); }
}
