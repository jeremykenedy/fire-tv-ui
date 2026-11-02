package com.jeremykenedy.firetv.ui.nativeapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** Tracks dialogs owned by the current Home activity. */
public final class HomeDialogBuilder extends AlertDialog.Builder {
    private final Context context;
    private static WeakReference<Activity> home = new WeakReference<>(null);
    private static boolean active;
    private static final List<WeakReference<AlertDialog>> dialogs = new ArrayList<>();

    public HomeDialogBuilder(Context context) { super(context); this.context = context; }

    public static void activate(Activity activity) {
        Activity previous = home.get();
        if (previous != null && previous != activity) release(previous);
        else if (previous == null) dialogs.clear();
        home = new WeakReference<>(activity);
        active = true;
    }

    public static void deactivate(Activity activity) {
        if (home.get() == activity) active = false;
    }

    public static void release(Activity activity) {
        if (home.get() != activity) return;
        List<AlertDialog> showing = takeShowingDialogs();
        active = false;
        home.clear();
        dismiss(showing);
    }

    @Override public AlertDialog create() {
        AlertDialog dialog = super.create();
        Activity activity = home.get();
        if (activity != null && !activity.isDestroyed() && context == activity) {
            for (int i = dialogs.size() - 1; i >= 0; i--) {
                AlertDialog existing = dialogs.get(i).get();
                if (existing == null || !existing.isShowing()) dialogs.remove(i);
            }
            dialogs.add(new WeakReference<>(dialog));
        }
        return dialog;
    }

    public static boolean dismissForHome() {
        return active && dismissForHome(home.get());
    }

    public static boolean dismissForHome(Activity activity) {
        if (activity == null || home.get() != activity || activity.isFinishing() || activity.isDestroyed()) return false;
        List<AlertDialog> showing = takeShowingDialogs();
        dismiss(showing);
        return !showing.isEmpty();
    }

    private static List<AlertDialog> takeShowingDialogs() {
        List<AlertDialog> showing = new ArrayList<>();
        for (int i = dialogs.size() - 1; i >= 0; i--) {
            AlertDialog dialog = dialogs.get(i).get();
            if (dialog != null && dialog.isShowing()) showing.add(dialog);
        }
        dialogs.clear();
        return showing;
    }

    private static void dismiss(List<AlertDialog> showing) {
        for (AlertDialog dialog : showing) dialog.dismiss();
    }
}
