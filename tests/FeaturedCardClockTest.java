package com.jeremykenedy.firetv.ui;

import android.os.Handler;
import android.os.SystemClock;
import java.util.Arrays;

public final class FeaturedCardClockTest {
    private static String key(char value) {
        char[] chars = new char[64];
        Arrays.fill(chars, value);
        return new String(chars);
    }

    private static void tick(long now) {
        SystemClock.now = now;
        Runnable callback = Handler.pending;
        check(callback != null, "No callback");
        callback.run();
    }

    public static void main(String[] args) {
        android.content.Context context = new android.content.Context();
        String first = key('a'), manual = key('b'), inherited = key('d');
        final int[] changes = {0};
        Runnable changed = () -> changes[0]++;
        context.prefs.value = 8;
        FeaturedCardTiming.save(context.prefs, first, 5);
        FeaturedCardTiming.save(context.prefs, manual, 0);
        FeaturedCardClock.start(context, changed);
        check(Handler.pending == null, "Empty feeds do not run timers");

        FeaturedGroups.active.addAll(Arrays.asList(first, manual, inherited));
        FeaturedCardClock.feedsChanged();
        check(Handler.delay == 5000, "First per-feed deadline");
        tick(5000);
        check(FeaturedGroups.ticks.equals(Arrays.asList(first)), "Only five-second feed advanced");
        check(Handler.delay == 3000, "Global deadline retained");
        SystemClock.now = 6000;
        FeaturedCardClock.feedsChanged();
        check(Handler.delay == 2000, "Provider refresh does not restart countdown");
        tick(8000);
        check(FeaturedGroups.ticks.equals(Arrays.asList(first, inherited)),
                "Global feed advanced, manual feed still unchanged");
        tick(10000);
        check(FeaturedGroups.ticks.equals(Arrays.asList(first, inherited, first)),
                "Override keeps own cadence");

        FeaturedCardClock.pause();
        check(Handler.pending == null, "Menu pauses automatic changes");
        FeaturedCardClock.changed();
        check(FeaturedGroups.ticks.size() == 3, "Manual notification cannot advance another feed");
        SystemClock.now = 15000;
        FeaturedCardClock.resume();
        check(Handler.delay == 5000, "Dismiss gives fresh full interval");
        FeaturedGroups.active.remove(first);
        FeaturedCardClock.feedsChanged();
        check(Handler.delay == 8000, "Removed feed cancels its deadline");

        FeaturedCardTiming.save(context.prefs, null, 0);
        FeaturedCardClock.feedsChanged();
        check(Handler.pending == null, "All effective Manual leaves no timer");
        FeaturedCardTiming.save(context.prefs, manual, 1);
        FeaturedCardClock.feedsChanged();
        check(Handler.delay == 1000, "Individual timer works when global is Manual");
        tick(16000);
        check(FeaturedGroups.ticks.get(3).equals(manual),
                "Manual global does not suppress opted-in channel");
        FeaturedCardClock.stop();
        check(Handler.pending == null, "Leaving Main cancels callback");
        FeaturedCardClock.resume();
        FeaturedCardClock.feedsChanged();
        check(Handler.pending == null, "Off-Main refresh cannot restart callbacks");
        System.out.println("Independent feed clock, refresh and lifecycle checks passed");
    }

    private static void check(boolean condition, String why) {
        if (!condition) throw new AssertionError(why);
    }
}
