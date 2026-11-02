package com.jeremykenedy.firetv.ui.homepermissionfixture;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/** Companion app deliberately requests no permissions and shares no production UID. */
public final class UnprivilegedSender extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent trigger) {
        final PendingResult pending = goAsync();
        Intent command = new Intent().setComponent(new ComponentName("com.jeremykenedy.firetv.ui",
                "com.jeremykenedy.firetv.ui.BackupReceiver"))
                .putExtra("operation", "home-preference-on");
        try {
            android.content.pm.ActivityInfo target = context.getPackageManager().getReceiverInfo(command.getComponent(), 0);
            if (!target.exported || !"android.permission.DUMP".equals(target.permission)) {
                pending.setResultCode(1); pending.setResultData("target-boundary-unavailable"); pending.finish(); return;
            }
        } catch (android.content.pm.PackageManager.NameNotFoundException absent) {
            pending.setResultCode(1); pending.setResultData("target-not-visible"); pending.finish(); return;
        }
        try {
            context.sendOrderedBroadcast(command, null, new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) {
                    boolean denied = getResultCode() == 73 && "not-delivered".equals(getResultData());
                    pending.setResultCode(denied ? 0 : 1);
                    pending.setResultData(denied ? "permission-boundary-preserved" : "unexpected-receiver-response");
                    pending.finish();
                }
            }, null, 73, "not-delivered", null);
        } catch (SecurityException denied) {
            pending.setResultCode(0); pending.setResultData("permission-boundary-preserved"); pending.finish();
        }
    }
}
