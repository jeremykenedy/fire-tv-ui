package com.jeremykenedy.firetv.ui.nativeapp.layout;

import android.app.Activity;
import android.content.Context;
import android.view.View;

/** A local widget owns its view and refresh work; Home owns its stable slot. */
public interface WidgetProvider {
    enum State { READY, EMPTY, UNAVAILABLE, SETUP_REQUIRED }

    LayoutItem item();
    String title();
    default boolean defaultVisible() { return false; }
    State state(Context context);
    String message(Context context);
    int widthDp();
    int heightDp();
    View createView(Context context);
    void configure(Activity activity);

    /** Called on the UI thread when Home becomes visible. Must be idempotent and reload saved configuration. */
    void onStart(View view);

    /** Stop callbacks and cancel refresh work associated with this view. */
    void onStop(View view);
}
