package com.jeremykenedy.firetv.ui.nativeapp;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PreviewApplication extends Application implements SourceStartup.Gate {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Runnable> callbacks = new ArrayList<>();
    private volatile boolean ready;
    private volatile String failure;
    private boolean running;
    @Override
    public boolean isReady() {
        synchronized (com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState.class) {
            return ready && !HolidayPresetTransaction.hasRecovery(this);
        }
    }
    @Override
    public String error() {
        return HolidayPresetTransaction.hasRecovery(this)
            ? "An interrupted holiday change needs to be restored. Select Retry."
            : failure;
    }
    @Override
    public String protectionError() {
        return null;
    }
    @Override
    public void startProtection(Runnable done, AtomicBoolean expired) {
        main.post(done);
    }
    @Override
    public synchronized void start(Runnable done) {
        if (isReady()) {
            main.post(done);
            return;
        }
        ready = false;
        callbacks.add(done);
        if (running)
            return;
        running = true;
        failure = null;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean interrupted = HolidayPresetTransaction.hasRecovery(PreviewApplication.this);
                    HolidayPresetTransaction.recover(PreviewApplication.this);
                    if (interrupted)
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast
                                    .makeText(PreviewApplication.this, "Interrupted holiday change restored.",
                                        Toast.LENGTH_LONG)
                                    .show();
                            }
                        });
                    ready = true;
                } catch (Exception error) {
                    failure = "Holiday settings recovery failed. Try again.";
                }
                synchronized (PreviewApplication.this) {
                    running = false;
                    for (Runnable callback : callbacks) main.post(callback);
                    callbacks.clear();
                }
            }
        }).start();
    }
}
