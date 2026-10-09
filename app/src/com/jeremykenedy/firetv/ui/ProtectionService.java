package com.jeremykenedy.firetv.ui;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.VpnService;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import java.io.FileInputStream;
import java.io.IOException;

/** Local settings guard and a discard-only VPN restricted to Amazon updaters. */
public final class ProtectionService extends VpnService {
    public static final String BLOCK_UPDATES = "fire_tv_ui_block_updates";
    public static final String BLOCK_APPSTORE = "fire_tv_ui_block_appstore";
    private static final String CHANNEL = "device_protection";
    private static final String[] UPDATERS = {
        "com.amazon.device.software.ota", "com.amazon.device.software.ota.override",
        "com.amazon.tv.forcedotaupdater", "com.amazon.tv.forcedotaupdater.v2"
    };
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ParcelFileDescriptor tunnel;
    private boolean blockedAppstore;
    private static volatile boolean active;
    private static volatile String failure;
    private static volatile int blockedPackages;

    private final Runnable guard = new Runnable() {
        @Override
        public void run() {
            try {
                DeviceSettings.repair(ProtectionService.this);
            } catch (RuntimeException failed) {
                failure = failed.getMessage();
            }
            handler.removeCallbacks(this);
            handler.postDelayed(this, 30000);
        }
    };
    private final ContentObserver changes = new ContentObserver(handler) {
        @Override
        public void onChange(boolean selfChange) {
            handler.removeCallbacks(guard);
            handler.postDelayed(guard, 500);
        }
    };

    public static void start(Context context) {
        context.startForegroundService(new Intent(context, ProtectionService.class));
    }

    public static boolean updatesBlocked() {
        return active;
    }

    public static String status() {
        return active ? "Blocking " + blockedPackages + " Amazon update packages on this TV."
                : failure == null ? "Amazon update traffic is allowed." : failure;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager notices = getSystemService(NotificationManager.class);
        notices.createNotificationChannel(new NotificationChannel(CHANNEL,
                "Device protection", NotificationManager.IMPORTANCE_LOW));
        Intent controls = new Intent(this, SystemControlsActivity.class);
        PendingIntent intent = PendingIntent.getActivity(this, 0, controls,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("Fire TV UI device protection")
                .setContentText("Local settings protection. No analytics or activity reporting.")
                .setContentIntent(intent).setOngoing(true).build();
        startForeground(42, notification);
        String[] secure = {DeviceSettings.COMPONENTS, DeviceSettings.ENABLED, DeviceSettings.SLEEP, DeviceSettings.AUTO_WAKE,
            "enabled_accessibility_services", "accessibility_enabled"};
        for (String key : secure) {
            getContentResolver().registerContentObserver(Settings.Secure.getUriFor(key), false, changes);
        }
        getContentResolver().registerContentObserver(Settings.System.getUriFor(DeviceSettings.IDLE), false, changes);
        for (String key : new String[] {Settings.Global.ADB_ENABLED, DeviceSettings.WIFI_ADB}) {
            getContentResolver().registerContentObserver(Settings.Global.getUriFor(key), false, changes);
        }
        handler.post(guard);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean wanted = DeviceSettings.prefs(this).getBoolean(BLOCK_UPDATES, false);
        boolean appstore = DeviceSettings.prefs(this).getBoolean(BLOCK_APPSTORE, false);
        if (!wanted) {
            closeTunnel();
            failure = null;
        } else if (tunnel == null || blockedAppstore != appstore) {
            closeTunnel();
            establishBlocker(appstore);
        }
        handler.removeCallbacks(guard);
        handler.post(guard);
        if (!wanted && !DeviceSettings.canWrite(this)) {
            stopForeground(true);
            stopSelf();
        }
        return START_STICKY;
    }

    private void establishBlocker(boolean appstore) {
        if (VpnService.prepare(this) != null) {
            failure = "Allow the TV's VPN permission to block Amazon update traffic.";
            return;
        }
        try {
            Builder builder = new Builder().setSession("Fire TV UI update protection")
                    .addAddress("10.217.0.1", 32).addAddress("fd88:217::1", 128)
                    .addRoute("0.0.0.0", 0).addRoute("::", 0).setBlocking(true);
            int count = 0;
            for (String pkg : UPDATERS) {
                count += allowInstalled(builder, pkg);
            }
            if (appstore) {
                count += allowInstalled(builder, "com.amazon.venezia");
            }
            if (count == 0) {
                throw new IllegalStateException("No Amazon update packages are installed on this device.");
            }
            // An empty allowed-app list would route every app, so it must
            // never reach establish(). Nothing is forwarded from this tunnel.
            final ParcelFileDescriptor connection = builder.establish();
            if (connection == null) {
                throw new IllegalStateException("The TV did not start update protection.");
            }
            tunnel = connection;
            blockedPackages = count;
            blockedAppstore = appstore;
            active = true;
            failure = null;
            Thread discard = new Thread(new Runnable() {
                @Override
                public void run() {
                    try (FileInputStream input = new FileInputStream(connection.getFileDescriptor())) {
                        byte[] packet = new byte[32768];
                        while (input.read(packet) >= 0) {
                            // Discard updater traffic locally without recording it.
                        }
                    } catch (IOException closed) {
                        // Closing the local tunnel ends the blocking reader.
                    }
                }
            }, "Update protection");
            discard.setDaemon(true);
            discard.start();
        } catch (Exception failed) {
            closeTunnel();
            failure = failed.getMessage();
        }
    }

    private int allowInstalled(Builder builder, String pkg) {
        try {
            android.content.pm.ApplicationInfo app = getPackageManager().getApplicationInfo(pkg, 0);
            if (app.uid < android.os.Process.FIRST_APPLICATION_UID) {
                throw new IllegalStateException("An Amazon updater shares a core system UID. "
                        + "This TV needs another update-blocking method.");
            }
            builder.addAllowedApplication(pkg);
            return 1;
        } catch (android.content.pm.PackageManager.NameNotFoundException absent) {
            return 0;
        }
    }

    private void closeTunnel() {
        active = false;
        blockedPackages = 0;
        if (tunnel != null) {
            try {
                tunnel.close();
            } catch (IOException ignored) {
                // The system may have closed the descriptor already.
            }
            tunnel = null;
        }
    }

    @Override
    public void onRevoke() {
        DeviceSettings.prefs(this).edit().putBoolean(BLOCK_UPDATES, false).commit();
        closeTunnel();
        failure = "Update protection was stopped by the TV or another VPN.";
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        closeTunnel();
        handler.removeCallbacksAndMessages(null);
        getContentResolver().unregisterContentObserver(changes);
        super.onDestroy();
    }
}
