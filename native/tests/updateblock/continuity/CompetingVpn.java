package com.jeremykenedy.firetv.updatecontinuityproof;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

/** No INTERNET permission or forwarded traffic; owns only its own inert UID. */
public final class CompetingVpn extends VpnService {
    private ParcelFileDescriptor tunnel;
    @Override public void onCreate() {
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("fixture-vpn", "Fixture VPN", NotificationManager.IMPORTANCE_LOW));
        startForeground(801, new Notification.Builder(this, "fixture-vpn").setSmallIcon(android.R.drawable.ic_lock_lock).setContentTitle("Disposable competing VPN").build());
        try {
            tunnel = new Builder().setSession("Disposable competing VPN").addAddress("10.218.0.1", 32)
                    .addRoute("0.0.0.0", 0).addAllowedApplication(getPackageName()).establish();
            if (tunnel == null) stopSelf();
        } catch (Exception failure) { stopSelf(); }
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) { return START_NOT_STICKY; }
    @Override public void onRevoke() { stopSelf(); }
    @Override public void onDestroy() {
        try { if (tunnel != null) tunnel.close(); } catch (java.io.IOException ignored) { }
        super.onDestroy();
    }
}
