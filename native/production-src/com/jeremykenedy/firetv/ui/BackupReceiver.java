package com.jeremykenedy.firetv.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.jeremykenedy.firetv.ui.nativeapp.SourceStartup;
import com.jeremykenedy.firetv.ui.nativeapp.device.HomeCommandHandler;
import com.jeremykenedy.firetv.ui.nativeapp.device.commands.SettingCommandHandler;

/** Explicit ordered CLI entry, protected by android.permission.DUMP in the production manifest. */
public final class BackupReceiver extends BroadcastReceiver {
    @Override public void onReceive(final Context context, Intent intent) {
        if (!isOrderedBroadcast() || intent == null || intent.getComponent() == null
                || !"com.jeremykenedy.firetv.ui".equals(context.getPackageName())
                || !context.getPackageName().equals(intent.getComponent().getPackageName())
                || !BackupReceiver.class.getName().equals(intent.getComponent().getClassName())) {
            setResultCode(1); setResultData("java.lang.SecurityException: Unsupported command delivery.");
            return;
        }
        if (!SourceStartup.ready(context)) {
            SourceStartup.start(context, new Runnable() { @Override public void run() {} });
            setResultCode(1); setResultData("Launcher settings are being prepared. Retry the command.");
            return;
        }
        final Intent request = new Intent(intent);
        final String operation = request.getStringExtra("operation");
        final PendingResult result = goAsync();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final String reply;
                    if ("setting".equals(operation)) {
                        reply = SettingCommandHandler.execute(context.getApplicationContext(), request);
                    } else if (operation != null && operation.startsWith("home-")) {
                        reply = HomeCommandHandler.execute(context.getApplicationContext(), operation);
                    } else {
                        throw new UnsupportedOperationException("This operation is not supported by the source command bridge.");
                    }
                    result.setResultData(reply);
                    result.setResultCode(0);
                } catch (RuntimeException failed) {
                    result.setResultCode(1);
                    result.setResultData(failed.getClass().getName() + ": " + failed.getMessage());
                } finally {
                    result.finish();
                }
            }
        }, "source-device-command").start();
    }
}
