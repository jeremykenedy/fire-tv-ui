package com.jeremykenedy.firetv.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class DeviceBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            return;
        }
        if (DeviceSettings.canWrite(context)) {
            try {
                ProtectionService.start(context);
                DeviceSettings.repair(context);
                if (HomeControl.wanted(context)) {
                    if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
                        HomeControl.rebind(context);
                    } else {
                        HomeControl.setEnabled(context, true, true);
                    }
                }
            } catch (RuntimeException ignored) {
                // The controls page reports permission and read-back state.
            }
        }
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && HomeControl.wanted(context)) {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                context.startActivity(launch);
            }
        }
    }
}
