package com.jeremykenedy.firetv.ui;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.atomic.AtomicBoolean;
import com.jeremykenedy.firetv.ui.nativeapp.SourceStartup;
import com.jeremykenedy.firetv.ui.nativeapp.device.HomeCommandHandler;
import com.jeremykenedy.firetv.ui.nativeapp.device.HomeSetupPolicy;

/** Honors an existing boot opt-in without selecting Home or enabling system services. */
public final class DeviceBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(final Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        final PendingResult pending = goAsync();
        final Handler handler = new Handler(Looper.getMainLooper());
        final AtomicBoolean finished = new AtomicBoolean();
        final Runnable finish = new Runnable() {
            @Override public void run() { handler.removeCallbacks(this); if (finished.compareAndSet(false, true)) pending.finish(); }
        };
        handler.postDelayed(finish, 8000);
        try {
            SourceStartup.start(context, new Runnable() {
                @Override public void run() {
                    if (finished.get()) return;
                    try {
                        if (SourceStartup.ready(context)) {
                            launchIfEnabled(context);
                            SourceStartup.startProtection(context, finish, finished);
                        } else finish.run();
                    } catch (RuntimeException unavailable) { finish.run(); }
                }
            });
        } catch (RuntimeException unavailable) {
            finish.run();
        }
    }

    private void launchIfEnabled(Context context) {
        if (!HomeCommandHandler.recover(context)
                || !HomeSetupPolicy.launchOnBoot(context.getPackageName(),
                    context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).getAll())) return;
        Intent home = new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(context.getPackageName(),
                "com.jeremykenedy.firetv.ui.nativeapp.MainActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            context.startActivity(home);
        } catch (RuntimeException unavailable) {
            // The platform can restrict background starts; keep the chosen Home and services intact.
        }
    }
}
