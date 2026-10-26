package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;

/** Keeps the system Home fallback available when our launcher cannot open. */
public final class HomeKeyDispatch {
    private static final String TAG = "FireTvHomeKey";

    private HomeKeyDispatch() {
    }

    public static boolean dispatch(Context context, KeyEvent event, boolean enabled, Intent launch) {
        if (!enabled || event == null || event.getKeyCode() != KeyEvent.KEYCODE_HOME) {
            return false;
        }
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return true;
        }
        Log.i(TAG, "Home up dispatch");
        if (launch == null) {
            Log.w(TAG, "Home up unavailable");
            return false;
        }
        try {
            context.startActivity(launch);
            Log.i(TAG, "Home up launched");
            return true;
        } catch (Exception failed) {
            Log.w(TAG, "Home up launch failed");
            return false;
        }
    }
}
