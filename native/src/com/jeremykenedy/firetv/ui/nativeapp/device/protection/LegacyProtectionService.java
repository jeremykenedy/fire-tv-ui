package com.jeremykenedy.firetv.ui.nativeapp.device.protection;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.provider.Settings;
import com.jeremykenedy.firetv.ui.nativeapp.SourceStartup;

/** Production legacy setting guard. The VPN update blocker is not implemented here. */
public class LegacyProtectionService extends Service {
    private static final String CHANNEL = "legacy_device_protection";
    private HandlerThread worker;
    private Handler handler;
    private SharedPreferences prefs;
    private final LegacyProtectionSession session = new LegacyProtectionSession();
    private ContentObserver observer;
    private volatile boolean destroyed;
    private final SharedPreferences.OnSharedPreferenceChangeListener changes = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
            if (key != null && key.startsWith("fire_tv_ui_")) handler.post(new Runnable() {
                @Override public void run() { session.preferencesChanged(); schedule(0); }
            });
        }
    };
    private final Runnable check = new Runnable() {
        @Override public void run() {
            if (!SourceStartup.ready(LegacyProtectionService.this)) {
                status("Waiting for saved settings to finish loading.");
            } else {
                LegacyDeviceProtection.Result result;
                synchronized (LegacyDeviceProtection.LOCK) {
                    java.util.Map<String, ?> values = AndroidLegacyDeviceProtection.preferences(LegacyProtectionService.this);
                    if (!LegacyDeviceProtection.hasSavedChoices(values)) {
                        session.preferencesChanged();
                        stopSelf();
                        return;
                    }
                    result = session.check(AndroidLegacyDeviceProtection.store(LegacyProtectionService.this), values);
                }
                if (result.complete()) status("Saved settings protection is active.");
                else status("Saved settings need review: " + String.join(", ", result.unavailable));
            }
            schedule(30000);
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        if (!"com.jeremykenedy.firetv.ui".equals(getPackageName())) { stopSelf(); return; }
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Device protection", NotificationManager.IMPORTANCE_LOW));
        startForeground(42, notice("Checking saved settings."));
        worker = new HandlerThread("legacy-device-protection"); worker.start();
        handler = new Handler(worker.getLooper());
        SourceStartup.start(this, new Runnable() {
            @Override public void run() {
                if (!destroyed && handler != null) handler.post(new Runnable() {
                    @Override public void run() {
                        if (destroyed) return;
                        if (SourceStartup.ready(LegacyProtectionService.this)) initialize();
                        else status("Saved settings could not be prepared. Open Fire TV UI to review.");
                    }
                });
            }
        });
    }
    private synchronized void initialize() {
        if (destroyed || prefs != null) return;
        prefs = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(changes);
        observer = new ContentObserver(handler) {
            @Override public void onChange(boolean selfChange) { schedule(500); }
        };
        for (String key : new String[]{"screensaver_components", "screensaver_enabled", "sleep_timeout", "str.auto_wake_up_enabled"})
            getContentResolver().registerContentObserver(Settings.Secure.getUriFor(key), false, observer);
        getContentResolver().registerContentObserver(Settings.System.getUriFor("screen_off_timeout"), false, observer);
        for (String key : new String[]{"development_settings_enabled", "adb_enabled", "adb_wifi_enabled"})
            getContentResolver().registerContentObserver(Settings.Global.getUriFor(key), false, observer);
        schedule(0);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { if (prefs != null) schedule(0); return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public synchronized void onDestroy() {
        destroyed = true;
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(changes);
        if (observer != null) getContentResolver().unregisterContentObserver(observer);
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (worker != null) worker.quitSafely();
        super.onDestroy();
    }
    private void schedule(long delay) { handler.removeCallbacks(check); handler.postDelayed(check, delay); }
    private void status(String value) { getSystemService(NotificationManager.class).notify(42, notice(value)); }
    private Notification notice(String text) {
        return new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("Fire TV UI device protection").setContentText(text).setOngoing(true).build();
    }
}
