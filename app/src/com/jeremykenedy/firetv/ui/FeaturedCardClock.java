package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.HashMap;
import java.util.Map;

/** Runs only while Main is resumed; menus and item screens keep their selected item. */
public final class FeaturedCardClock {
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final FeaturedCardSchedule deadlines = new FeaturedCardSchedule();
    private static Context context;
    private static Runnable changed;
    private static boolean paused;
    private static final Runnable refresh = new Runnable() {
        @Override public void run() { schedule(); }
    };
    private static final Runnable tick = new Runnable() {
        @Override public void run() {
            if (context == null || paused) return;
            long now = SystemClock.uptimeMillis();
            sync(now);
            if (FeaturedGroups.advance(deadlines.due(now))) changed();
            schedule();
        }
    };

    private FeaturedCardClock() {}

    public static void start(Context owner, Runnable notify) {
        stop();
        context = owner.getApplicationContext();
        changed = notify;
        changed();
        schedule();
    }

    public static void stop() {
        handler.removeCallbacks(tick);
        handler.removeCallbacks(refresh);
        deadlines.clear();
        context = null;
        changed = null;
        paused = false;
    }

    static void pause() {
        paused = true;
        handler.removeCallbacks(tick);
        deadlines.clear();
    }

    static void resume() {
        paused = false;
        schedule();
    }

    static void changed() {
        if (changed != null) changed.run();
    }

    static void feedsChanged() {
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }

    private static void sync(long now) {
        android.content.SharedPreferences prefs = DeviceSettings.prefs(context);
        Map<String, Integer> custom = FeaturedCardTiming.overrides(prefs);
        int global = FeaturedCardTiming.seconds(prefs.getInt(FeaturedCardTiming.SETTING, 8));
        Map<String, Integer> active = new HashMap<>();
        for (String feed : FeaturedGroups.rotatingFeeds()) {
            Integer value = custom.get(feed);
            active.put(feed, value == null ? global : value);
        }
        deadlines.sync(active, now);
    }

    private static void schedule() {
        handler.removeCallbacks(tick);
        if (context == null || paused) return;
        long now = SystemClock.uptimeMillis();
        sync(now);
        long delay = deadlines.delay(now);
        if (delay != Long.MAX_VALUE) handler.postDelayed(tick, delay);
    }
}
