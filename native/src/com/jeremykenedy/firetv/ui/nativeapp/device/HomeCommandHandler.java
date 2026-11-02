package com.jeremykenedy.firetv.ui.nativeapp.device;

import android.content.Context;

/** Production compatibility API. It reads OS routing and never writes secure settings. */
public final class HomeCommandHandler {
    private HomeCommandHandler() {}
    public static boolean recover(Context context) {
        if (!HomeRoutingPolicy.PRODUCTION_PACKAGE.equals(context.getPackageName())) return false;
        try {
            HomeCommandPolicy.recover(new HomeCommandStore(context));
            return true;
        } catch (RuntimeException failed) {
            return false;
        }
    }

    public static String execute(Context context, String operation) {
        if (!HomeRoutingPolicy.PRODUCTION_PACKAGE.equals(context.getPackageName())) {
            throw new SecurityException("Home commands require the production application identity.");
        }
        return HomeCommandPolicy.execute(new HomeCommandStore(context), operation);
    }
}
