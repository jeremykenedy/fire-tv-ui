package com.jeremykenedy.firetv.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The manifest restricts this CLI entry point to callers with DUMP permission. */
public final class BackupReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String operation = intent.getStringExtra("operation");
            if ("save".equals(operation)) {
                BackupStore.save(context);
            } else if ("restore".equals(operation)) {
                BackupStore.restore(context, "import".equals(intent.getStringExtra("source")));
            } else if ("simple".equals(operation)) {
                BackupStore.simpleSetup(context);
            } else if ("home-on".equals(operation) || "home-off".equals(operation)) {
                HomeControl.setEnabled(context, "home-on".equals(operation), true);
            } else if ("home-preference-on".equals(operation) || "home-preference-off".equals(operation)) {
                HomeControl.remember(context, "home-preference-on".equals(operation));
            } else if ("dream-unlock".equals(operation)) {
                DeviceSettings.prefs(context).edit().putBoolean(DeviceSettings.DREAM_UNLOCKED, true).commit();
            } else if ("adb-protection-on".equals(operation) || "adb-protection-off".equals(operation)) {
                DeviceSettings.keepAdb(context, "adb-protection-on".equals(operation));
                ProtectionService.start(context);
            } else if ("protection-on".equals(operation) || "protection-off".equals(operation)) {
                DeviceSettings.capture(context);
                DeviceSettings.prefs(context).edit().putBoolean(DeviceSettings.PROTECT,
                        "protection-on".equals(operation)).commit();
                ProtectionService.start(context);
            } else if ("setting".equals(operation)) {
                String namespace = intent.getStringExtra("namespace");
                String key = intent.getStringExtra("key");
                String value = intent.getStringExtra("value");
                if ("secure".equals(namespace) && DeviceSettings.SLEEP.equals(key)) {
                    DeviceSettings.sleep(context, Integer.parseInt(value));
                } else if ("system".equals(namespace) && DeviceSettings.IDLE.equals(key)) {
                    DeviceSettings.idle(context, Integer.parseInt(value));
                } else if ("secure".equals(namespace) && DeviceSettings.AUTO_WAKE.equals(key)
                        && ("0".equals(value) || "1".equals(value))) {
                    DeviceSettings.autoWake(context, "1".equals(value));
                } else if ("secure".equals(namespace) && DeviceSettings.COMPONENTS.equals(key)) {
                    DeviceSettings.component(context, "null".equals(value) ? null : value);
                } else if ("secure".equals(namespace) && DeviceSettings.ENABLED.equals(key)
                        && ("0".equals(value) || "1".equals(value))) {
                    DeviceSettings.enableDream(context, "1".equals(value));
                } else {
                    throw new IllegalArgumentException("This setting cannot be changed through Fire TV UI.");
                }
            } else {
                throw new IllegalArgumentException("Unknown backup operation.");
            }
            setResultCode(0);
            setResultData("ok");
        } catch (Exception error) {
            setResultCode(1);
            setResultData(error.getClass().getName() + ": "
                    + (error.getMessage() == null ? "The operation failed." : error.getMessage()));
        }
    }
}
