package com.jeremykenedy.firetv.ui.nativeapp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import java.util.concurrent.atomic.AtomicBoolean;

/** Production startup is gated; the isolated preview has no production state to migrate. */
public final class SourceStartup {
    public interface Gate {
        boolean isReady();
        void start(Runnable complete);
        String error();
        void startProtection(Runnable complete, AtomicBoolean expired);
        String protectionError();
    }

    private SourceStartup() {}

    public static boolean ready(Context context) {
        return !(context.getApplicationContext() instanceof Gate)
                || ((Gate) context.getApplicationContext()).isReady();
    }

    public static boolean redirectIfBlocked(Activity activity) {
        if (ready(activity)) return false;
        Intent intent = new Intent().setClassName(activity, "com.jeremykenedy.firetv.ui.nativeapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
        activity.finish();
        return true;
    }

    public static void start(Context context, Runnable complete) {
        if (context.getApplicationContext() instanceof Gate)
            ((Gate) context.getApplicationContext()).start(complete);
        else complete.run();
    }

    public static void startProtection(Context context, Runnable complete) { startProtection(context, complete, null); }

    public static void startProtection(Context context, Runnable complete, AtomicBoolean expired) {
        if (context.getApplicationContext() instanceof Gate)
            ((Gate) context.getApplicationContext()).startProtection(complete, expired);
        else complete.run();
    }

    public static String protectionError(Context context) {
        return context.getApplicationContext() instanceof Gate
                ? ((Gate) context.getApplicationContext()).protectionError() : null;
    }

    public static String error(Context context) {
        return context.getApplicationContext() instanceof Gate
                ? ((Gate) context.getApplicationContext()).error() : null;
    }
}
