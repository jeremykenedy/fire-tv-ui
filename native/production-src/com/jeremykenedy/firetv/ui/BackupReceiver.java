package com.jeremykenedy.firetv.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.jeremykenedy.firetv.ui.nativeapp.device.HomeCommandHandler;

/** Explicit ordered CLI entry, protected by android.permission.DUMP in the production manifest. */
public final class BackupReceiver extends BroadcastReceiver {
    @Override public void onReceive(final Context context, Intent intent) {
        if (!isOrderedBroadcast() || intent == null || intent.getComponent() == null
                || !"com.jeremykenedy.firetv.ui".equals(context.getPackageName())
                || !context.getPackageName().equals(intent.getComponent().getPackageName())
                || !BackupReceiver.class.getName().equals(intent.getComponent().getClassName())) {
            setResultCode(1); setResultData("java.lang.SecurityException: Unsupported Home command delivery.");
            return;
        }
        final String operation = intent.getStringExtra("operation");
        final PendingResult result = goAsync();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    result.setResultData(HomeCommandHandler.execute(context.getApplicationContext(), operation));
                    result.setResultCode(0);
                } catch (RuntimeException failed) {
                    result.setResultCode(1);
                    result.setResultData(failed.getClass().getName() + ": " + failed.getMessage());
                } finally {
                    result.finish();
                }
            }
        }, "source-home-command").start();
    }
}
