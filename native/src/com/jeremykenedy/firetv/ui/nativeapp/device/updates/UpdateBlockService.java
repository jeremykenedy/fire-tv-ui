package com.jeremykenedy.firetv.ui.nativeapp.device.updates;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import com.jeremykenedy.firetv.ui.nativeapp.SourceStartup;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

/** Package-scoped, discard-only legacy update protection; no packet data is retained. */
public class UpdateBlockService extends VpnService {
    public static final String MANUAL_START = "com.jeremykenedy.firetv.ui.UPDATE_PROTECTION_MANUAL";
    public static final String RETAINED_START = "com.jeremykenedy.firetv.ui.UPDATE_PROTECTION_RETAINED";
    private static final String CHANNEL = "legacy_update_blocking";
    private static final int NOTICE = 143;
    private static final UpdateBlockStatus STATUS = new UpdateBlockStatus();
    private final UpdateBlockStarts starts = new UpdateBlockStarts();
    private static volatile boolean active;
    private HandlerThread worker;
    private Handler handler;
    private SharedPreferences preferences;
    private ParcelFileDescriptor tunnel;
    private boolean selectedStore;
    private volatile boolean destroyed;
    private volatile boolean revoked;
    private boolean packageReceiverRegistered;
    private ContentObserver lockdownObserver;
    private final BroadcastReceiver packageChanges = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            Handler current = handler;
            if (current != null && !destroyed) current.post(new Runnable() {
                @Override public void run() { closeTunnel(); schedule(); }
            });
        }
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            if (UpdateBlockPolicy.UPDATES.equals(key) || UpdateBlockPolicy.APPSTORE.equals(key)) schedule();
        }
    };
    public static boolean active() { return active; }
    public static boolean foregroundDenied(Context context) {
        if (android.os.Build.VERSION.SDK_INT < 28) return false;
        android.app.AppOpsManager ops = (android.app.AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null) return true;
        try {
            int mode = ops.checkOpNoThrow("android:start_foreground", android.os.Process.myUid(), context.getPackageName());
            return mode == android.app.AppOpsManager.MODE_IGNORED || mode == android.app.AppOpsManager.MODE_ERRORED;
        } catch (IllegalArgumentException unavailable) { return false; }
        catch (SecurityException denied) { return true; }
    }
    public static String status() { return STATUS.text(); }
    public static void addStatusListener(UpdateBlockStatus.Listener listener) { STATUS.add(listener); }
    public static void removeStatusListener(UpdateBlockStatus.Listener listener) { STATUS.remove(listener); }
    public static void reportPending(String text) { STATUS.report(text); }

    @Override public void onCreate() {
        super.onCreate();
        if (!"com.jeremykenedy.firetv.ui".equals(getPackageName())) { stopSelf(); return; }
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Update protection", NotificationManager.IMPORTANCE_LOW));
        startForeground(NOTICE, notification("Checking saved update protection."));
        worker = new HandlerThread("update-protection"); worker.start();
        handler = new Handler(worker.getLooper());
    }
    private void beginStartup() {
        if (destroyed || revoked || handler == null || preferences != null || !starts.pending()) return;
        SourceStartup.start(this, new Runnable() {
            @Override public void run() {
                if (!destroyed && !revoked && handler != null && starts.pending()) handler.post(new Runnable() {
                    @Override public void run() {
                        if (destroyed || revoked || !starts.pending()) return;
                        if (SourceStartup.ready(UpdateBlockService.this)) initialize();
                        else {
                            show("Saved settings could not be prepared. Open Fire TV UI to review.");
                            starts.cancel(); stopSelf();
                        }
                    }
                });
            }
        });
    }
    private synchronized void initialize() {
        if (destroyed || revoked || !starts.pending() || preferences != null) return;
        preferences = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE);
        preferences.registerOnSharedPreferenceChangeListener(listener);
        IntentFilter packages = new IntentFilter();
        packages.addAction(Intent.ACTION_PACKAGE_ADDED); packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(Intent.ACTION_PACKAGE_CHANGED); packages.addDataScheme("package");
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(packageChanges, packages, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(packageChanges, packages);
            packageReceiverRegistered = true;
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                lockdownObserver = new ContentObserver(handler) {
                    @Override public void onChange(boolean selfChange) { checkLockdown.run(); }
                };
                try { getContentResolver().registerContentObserver(Settings.Secure.getUriFor("always_on_vpn_lockdown"), false, lockdownObserver); }
                catch (RuntimeException unavailable) { lockdownObserver = null; }
            }
            schedule();
        } catch (RuntimeException unavailable) {
            preferences.unregisterOnSharedPreferenceChangeListener(listener);
            preferences = null;
            show("Package change monitoring is unavailable. Update protection was not started.");
            starts.cancel(); stopSelf();
        }
    }
    private void schedule() {
        Handler current = handler;
        if (current != null && !destroyed) { current.removeCallbacks(reconcile); current.post(reconcile); }
    }
    private final Runnable reconcile = new Runnable() {
        @Override public void run() {
            if (destroyed || revoked || !starts.pending() || preferences == null || !SourceStartup.ready(UpdateBlockService.this)) return;
            try {
                Map<String, ?> saved = preferences.getAll();
                if (!UpdateBlockPolicy.enabled(saved)) {
                    closeTunnel(); show("Update protection is off."); stopSelf(); return;
                }
                final boolean appstore = UpdateBlockPolicy.includesAppstore(saved);
                boolean lockdown = android.os.Build.VERSION.SDK_INT >= 29 && isLockdownEnabled();
                if (!starts.manual() && !UpdateBlockStarts.automaticAllowed(android.os.Build.VERSION.SDK_INT, lockdown)) {
                    closeTunnel(); show(lockdown ? "VPN lockdown is active. Other apps may be offline. Review TV VPN settings."
                            : "Update protection needs a manual retry on this TV."); return;
                }
                if (lockdown) {
                    closeTunnel();
                    show("VPN lockdown is active. Other apps may be offline. Review TV VPN settings."); return;
                }
                if (tunnel != null && appstore == selectedStore) return;
                closeTunnel();
                final UpdateBlockPolicy.Verified packages = UpdateBlockPolicy.installed(packages(), appstore, android.os.Process.FIRST_APPLICATION_UID);
                final Builder builder = new Builder().setSession("Fire TV UI update protection")
                        .addAddress("10.217.0.1", 32).addAddress("fd88:217::1", 128)
                        .addRoute("0.0.0.0", 0).addRoute("::", 0).setBlocking(true);
                final ParcelFileDescriptor connection = UpdateBlockPolicy.build(new UpdateBlockPolicy.Builder<ParcelFileDescriptor>() {
                    @Override public void add(String name) throws Exception { builder.addAllowedApplication(name); }
                    @Override public ParcelFileDescriptor establish() {
                        UpdateBlockPolicy.Verified current = UpdateBlockPolicy.installed(packages(), appstore, android.os.Process.FIRST_APPLICATION_UID);
                        if (!packages.sameScope(current)) throw new IllegalStateException("Update packages changed during setup. Retry protection.");
                        if (android.os.Build.VERSION.SDK_INT >= 29 && isLockdownEnabled())
                            throw new IllegalStateException("VPN lockdown is active. Review TV VPN settings before retrying.");
                        return builder.establish();
                    }
                }, packages);
                synchronized (UpdateBlockService.this) {
                    boolean currentChoice;
                    try {
                        Map<String, ?> current = preferences.getAll();
                        currentChoice = UpdateBlockPolicy.enabled(current) && UpdateBlockPolicy.includesAppstore(current) == appstore;
                    } catch (RuntimeException invalid) {
                        connection.close(); throw invalid;
                    }
                    if (destroyed || revoked || !starts.pending() || !currentChoice) {
                        connection.close();
                        if (!destroyed && !revoked && starts.pending()) schedule();
                        return;
                    }
                    tunnel = connection; selectedStore = appstore; active = true;
                    show("Blocking " + packages.packages.size() + " Amazon update packages on this TV.");
                    if (android.os.Build.VERSION.SDK_INT >= 29) handler.postDelayed(checkLockdown, 5000);
                }
                Thread drain = new Thread(new Runnable() {
                    @Override public void run() {
                        try (FileInputStream input = new FileInputStream(connection.getFileDescriptor())) {
                            byte[] discarded = new byte[32768];
                            while (input.read(discarded) >= 0) { }
                        } catch (IOException closed) { }
                        Handler current = handler;
                        if (!destroyed && current != null) current.post(new Runnable() {
                            @Override public void run() {
                                synchronized (UpdateBlockService.this) {
                                    if (!destroyed && !revoked && tunnel == connection) {
                                        closeTunnel(); show("Update protection stopped. Open settings to retry.");
                                    }
                                }
                            }
                        });
                    }
                }, "update-protection-discard");
                drain.setDaemon(true); drain.start();
            } catch (Exception failure) {
                synchronized (UpdateBlockService.this) {
                    if (!destroyed && !revoked) {
                        closeTunnel();
                        show(failure.getMessage() == null ? "Update protection is unavailable." : failure.getMessage());
                    }
                }
            }
        }
    };
    private final Runnable checkLockdown = new Runnable() {
        @Override public void run() {
            synchronized (UpdateBlockService.this) {
                if (destroyed || revoked || tunnel == null) return;
                try {
                    if (isLockdownEnabled()) {
                        closeTunnel();
                        show("VPN lockdown is active. Other apps may be offline. Review TV VPN settings.");
                        return;
                    }
                } catch (RuntimeException unavailable) {
                    closeTunnel(); show("VPN status is unavailable. Review TV VPN settings."); return;
                }
                handler.removeCallbacks(this); handler.postDelayed(this, 5000);
            }
        }
    };
    private UpdateBlockPolicy.Packages packages() {
        return new UpdateBlockPolicy.Packages() {
            @Override public Integer uid(String name) {
                try {
                    ApplicationInfo info = getPackageManager().getApplicationInfo(name, 0);
                    return info == null || !info.enabled ? null : info.uid;
                } catch (PackageManager.NameNotFoundException missing) { return null; }
            }
            @Override public String[] packagesForUid(int uid) { return getPackageManager().getPackagesForUid(uid); }
        };
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (handler == null || destroyed || revoked) { stopSelf(startId); return START_NOT_STICKY; }
        String action = intent == null ? null : intent.getAction();
        boolean manual = MANUAL_START.equals(action);
        boolean retained = RETAINED_START.equals(action);
        boolean system = false;
        if (!manual && !retained && (action == null || SERVICE_INTERFACE.equals(action))
                && android.os.Build.VERSION.SDK_INT >= 29) {
            try { system = isAlwaysOn(); } catch (RuntimeException unavailable) { system = false; }
        }
        if (!manual && !retained && !system) {
            if (!starts.pending() && !active) {
                show("Saved update protection needs an explicit retry. No VPN was started.");
                stopSelf(startId);
            }
            return START_NOT_STICKY;
        }
        boolean first = starts.request(manual);
        if (preferences == null) { if (first) beginStartup(); }
        else schedule();
        return START_NOT_STICKY;
    }
    @Override public void onRevoke() {
        revoked = true;
        starts.cancel();
        closeTunnel();
        show("VPN permission was revoked. The saved update protection choice was kept; select Retry to review.");
        stopSelf();
    }
    @Override public synchronized void onDestroy() {
        destroyed = true;
        starts.cancel();
        if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(listener);
        if (packageReceiverRegistered) unregisterReceiver(packageChanges);
        if (lockdownObserver != null) getContentResolver().unregisterContentObserver(lockdownObserver);
        boolean wasActive = active;
        closeTunnel();
        if (wasActive) reportPending("Update protection stopped.");
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (worker != null) worker.quitSafely();
        super.onDestroy();
    }
    private synchronized void closeTunnel() {
        active = false;
        if (handler != null) handler.removeCallbacks(checkLockdown);
        if (tunnel != null) {
            try { tunnel.close(); } catch (IOException ignored) { }
            tunnel = null;
        }
    }
    private void show(String text) { reportPending(text); getSystemService(NotificationManager.class).notify(NOTICE, notification(text)); }
    private Notification notification(String text) {
        return new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("Fire TV UI update protection").setContentText(text).setOngoing(true).build();
    }
}
