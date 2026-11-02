package com.jeremykenedy.firetv.ui.nativeapp.widget.feeds;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.DialogInterface;
import android.os.Bundle;

/** The widget's Select action opens focusable native controls without changing Home's slot focus model. */
public final class FeedPanel {
    private FeedPanel() {}
    public static void show(final Activity activity) {
        try {
            if (new FeedStore(activity).read() == null) { FeedSettings.show(activity); return; }
        } catch (Exception unavailable) { FeedSettings.show(activity); return; }
        final FeedView view = new FeedView(activity);
        final AlertDialog dialog = new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle("Custom feed").setView(view)
                .setNegativeButton("Done", null).setPositiveButton("Source settings", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface ignored, int which) { FeedSettings.show(activity); }
                }).create();
        final Application.ActivityLifecycleCallbacks lifecycle = new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(Activity current) { if (current == activity && dialog.isShowing()) view.start(); }
            @Override public void onActivityStopped(Activity current) { if (current == activity) view.stop(); }
            @Override public void onActivityDestroyed(Activity current) { if (current == activity) dialog.dismiss(); }
            @Override public void onActivityCreated(Activity current, Bundle state) {}
            @Override public void onActivityResumed(Activity current) {}
            @Override public void onActivityPaused(Activity current) {}
            @Override public void onActivitySaveInstanceState(Activity current, Bundle state) {}
        };
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) { activity.getApplication().registerActivityLifecycleCallbacks(lifecycle); view.start(); }
        });
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface ignored) { view.stop(); activity.getApplication().unregisterActivityLifecycleCallbacks(lifecycle); }
        });
        dialog.show();
    }
}
