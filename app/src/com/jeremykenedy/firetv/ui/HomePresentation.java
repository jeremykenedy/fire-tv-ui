package com.jeremykenedy.firetv.ui;

import android.app.Activity;
import java.lang.ref.WeakReference;

/** Keeps ordinary Home delivery from rebuilding an already visible launcher. */
public final class HomePresentation {
    private static WeakReference<Activity> resumed = new WeakReference<>(null);

    private HomePresentation() {}

    public static void resumed(Activity activity) {
        resumed = new WeakReference<>(activity);
    }

    public static void paused(Activity activity) {
        if (resumed.get() == activity) resumed.clear();
    }

    public static void closeTransientWindow(Activity activity) {
        // The pinned launcher has composition-local popup state. Retain its reset
        // only for a popup owning focus; normal Home and app returns keep the scene.
        if (resumed.get() == activity && !activity.hasWindowFocus()) activity.recreate();
    }
}
