package com.jeremykenedy.firetv.ui;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

/** Reopens only the exact Equipment Control error after remote recognition returns. */
final class EquipmentDialogRecovery {
    private final AccessibilityService service;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long receiverRetryAt;
    private final Runnable pending = new Runnable() {
        @Override
        public void run() {
            recover();
        }
    };

    EquipmentDialogRecovery(AccessibilityService service) {
        this.service = service;
    }

    void check() {
        handler.removeCallbacks(pending);
        handler.postDelayed(pending, 150);
    }

    void stop() {
        handler.removeCallbacksAndMessages(null);
    }

    private void recover() {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) {
            return;
        }
        try {
            String pkg = String.valueOf(root.getPackageName());
            if ("com.amazon.tv.devicecontrolsettings".equals(pkg)) {
                recoverReceiver(root);
            }
        } finally {
            root.recycle();
        }
    }

    private void recoverReceiver(AccessibilityNodeInfo root) {
        String prefix = "com.amazon.tv.devicecontrolsettings:id/";
        if (!hasText(root, prefix + "txt_dialog_title", "Equipment Control is not available")
                || !hasText(root, prefix + "txt_dialog_message",
                        "Your current Fire TV remote does not support Equipment Control.")) {
            return;
        }
        if (Settings.Global.getInt(service.getContentResolver(), "show_dc_settings", 0) != 1) {
            handler.postDelayed(pending, 1000);
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now < receiverRetryAt || !click(root, prefix + "btn_dialog_ok", "OK")) {
            return;
        }
        receiverRetryAt = now + 30000;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent("com.amazon.tv.oobe.settings.ACTION.DEVICE_CONTROL",
                        Uri.parse("devicecontrolsettings://"));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (intent.resolveActivity(service.getPackageManager()) != null) {
                    try {
                        service.startActivity(intent);
                        Log.i("FireTvDialogRecovery", "Reopened Equipment Control after remote recognition recovered.");
                    } catch (RuntimeException unavailable) {
                        Log.w("FireTvDialogRecovery", "Equipment Control is no longer available.");
                    }
                }
            }
        }, 350);
    }

    private static boolean hasText(AccessibilityNodeInfo root, String id, String text) {
        boolean found = false;
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByViewId(id)) {
            found |= text.contentEquals(node.getText() == null ? "" : node.getText());
            node.recycle();
        }
        return found;
    }

    private static boolean click(AccessibilityNodeInfo root, String id, String text) {
        boolean clicked = false;
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByViewId(id)) {
            if (!clicked && text.contentEquals(node.getText() == null ? "" : node.getText())
                    && node.isEnabled()) {
                clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            node.recycle();
        }
        return clicked;
    }
}
