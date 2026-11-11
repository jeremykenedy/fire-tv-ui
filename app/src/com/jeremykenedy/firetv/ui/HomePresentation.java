package com.jeremykenedy.firetv.ui;

import android.app.Activity;
import java.lang.ref.WeakReference;

/** Keeps ordinary Home delivery from rebuilding an already visible launcher. */
public final class HomePresentation {
    private static WeakReference<Activity> resumed = new WeakReference<>(null);
    private static WeakReference<Activity> pausedPopup = new WeakReference<>(null);
    private static WeakReference<Activity> cardOwner = new WeakReference<>(null);
    private static String sourceChoices;
    private static java.util.Set<String> hiddenCards;

    private HomePresentation() {}

    public static void resumed(Activity activity) {
        android.content.SharedPreferences prefs = DeviceSettings.prefs(activity);
        String sources = prefs.getString("app_visibility", "{}");
        java.util.Set<String> hidden = new java.util.HashSet<>(prefs.getStringSet(
                "hidden_featured_cards", java.util.Collections.<String>emptySet()));
        boolean changed = cardOwner.get() == activity
                && (!sources.equals(sourceChoices) || !hidden.equals(hiddenCards));
        cardOwner = new WeakReference<>(activity);
        sourceChoices = sources;
        hiddenCards = hidden;
        resumed = new WeakReference<>(activity);
        pausedPopup.clear();
        if (changed) activity.recreate();
    }

    public static void paused(Activity activity) {
        if (resumed.get() == activity) {
            // Android pauses the current activity before delivering a Home intent.
            pausedPopup = new WeakReference<>(activity.hasWindowFocus() ? null : activity);
            resumed.clear();
        }
    }

    public static void closeTransientWindow(Activity activity) {
        // The pinned launcher has composition-local popup state. Retain its reset
        // only for a popup owning focus; normal Home and app returns keep the scene.
        if (pausedPopup.get() == activity
                || (resumed.get() == activity && !activity.hasWindowFocus())) {
            pausedPopup.clear();
            activity.recreate();
        }
    }
}
