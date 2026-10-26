package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/** Runs only while Main is resumed; menus and item screens keep their selected item. */
public final class FeaturedCardClock {
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static Context context;
    private static Runnable changed;
    private static boolean paused;
    private static final Runnable tick = new Runnable() {
        @Override public void run() {
            if (context == null || paused) return;
            FeaturedGroups.advance();
            changed();
            schedule();
        }
    };

    private FeaturedCardClock() {}

    public static void start(Context owner, Runnable notify) {
        stop();
        context = owner.getApplicationContext();
        changed = notify;
        paused = false;
        changed();
        schedule();
    }

    public static void stop() {
        handler.removeCallbacks(tick);
        context = null;
        changed = null;
        paused = false;
    }

    static void pause() {
        paused = true;
        handler.removeCallbacks(tick);
    }

    static void resume() {
        paused = false;
        schedule();
    }

    static void changed() {
        if (changed != null) changed.run();
    }

    private static void schedule() {
        handler.removeCallbacks(tick);
        if (context == null || paused) return;
        long delay = FeaturedCardTiming.delay(DeviceSettings.prefs(context));
        if (delay != Long.MAX_VALUE) handler.postDelayed(tick, delay);
    }
}
