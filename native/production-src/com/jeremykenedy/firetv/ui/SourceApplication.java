package com.jeremykenedy.firetv.ui;

import android.app.Application;
import android.app.AppOpsManager;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.os.Process;
import android.util.AtomicFile;
import com.jeremykenedy.firetv.ui.nativeapp.SourceStartup;
import com.jeremykenedy.firetv.ui.nativeapp.migration.AndroidProductionMigration;
import com.jeremykenedy.firetv.ui.nativeapp.device.protection.LegacyDeviceProtection;
import com.jeremykenedy.firetv.ui.nativeapp.device.protection.LegacyProtectionService;
import com.jeremykenedy.firetv.ui.nativeapp.device.updates.UpdateBlockPolicy;
import com.jeremykenedy.firetv.ui.nativeapp.device.updates.UpdateBlockService;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Serializes the production migration before any launcher component accesses preferences. */
public final class SourceApplication extends Application implements SourceStartup.Gate {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Runnable> callbacks = new ArrayList<>();
    private volatile boolean ready;
    private volatile String failure;
    private volatile String protectionFailure;
    private boolean running;

    @Override public boolean isReady() { return ready; }
    @Override public String error() { return failure; }
    @Override public String protectionError() { return protectionFailure; }

    @Override public synchronized void start(Runnable complete) {
        if (ready) { main.post(complete); return; }
        callbacks.add(complete);
        if (running) return;
        running = true;
        failure = null;
        executor.execute(new Runnable() {
            @Override public void run() {
                try {
                    File data = new File(getApplicationInfo().dataDir);
                    File markerPath = new File(new File(data, "no_backup"), "source-startup-v1");
                    int marker = readMarker(markerPath);
                    if (marker != 1) {
                        boolean fresh = marker == 0 || (empty(data, "shared_prefs") && empty(data, "files")
                                && empty(data, "databases") && empty(data, "no_backup"));
                        if (fresh && marker == -1) writeMarker(markerPath, 0);
                        AndroidProductionMigration.apply(SourceApplication.this, fresh);
                        writeMarker(markerPath, 1);
                    }
                    ready = true;
                } catch (Exception error) {
                    failure = error.getMessage() == null ? "Settings could not be prepared." : error.getMessage();
                }
                synchronized (SourceApplication.this) {
                    running = false;
                    for (Runnable callback : callbacks) main.post(callback);
                    callbacks.clear();
                }
            }
        });
    }

    @Override public void startProtection(final Runnable complete, final AtomicBoolean expired) {
        if (!ready) { main.post(complete); return; }
        executor.execute(new Runnable() {
            @Override public void run() {
                boolean wanted = false, updateWanted = false;
                String error = null, updateError = null;
                try {
                    java.util.Map<String, ?> saved = getSharedPreferences("launcher_prefs", MODE_PRIVATE).getAll();
                    wanted = LegacyDeviceProtection.hasSavedChoices(saved);
                    if (wanted && checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
                        error = "Saved device protection needs the TV's existing secure settings permission.";
                        wanted = false;
                    }
                    try {
                        updateWanted = UpdateBlockPolicy.enabled(saved);
                        if (updateWanted) {
                            UpdateBlockPolicy.includesAppstore(saved);

                        }
                    } catch (RuntimeException invalid) {
                        updateWanted = false;
                        updateError = "Saved update protection could not be checked. Existing settings were kept.";
                    }
                } catch (RuntimeException unavailable) {
                    error = "Saved device protection could not be checked. Open Sleep and protection, then select Refresh system status.";
                    updateError = "Saved update protection could not be checked. Existing settings were kept.";
                }
                final boolean shouldStart = wanted, shouldStartUpdates = updateWanted;
                final String inspectionError = error, updateInspectionError = updateError;
                main.post(new Runnable() {
                    @Override public void run() {
                        try {
                            if (expired != null && expired.get()) return;
                            protectionFailure = inspectionError;
                            try {
                                if (shouldStart) {
                                    if (foregroundDenied()) protectionFailure = "The TV did not allow saved device protection to start. Open Sleep and protection, then select Refresh system status.";
                                    else startForegroundService(new Intent(SourceApplication.this, LegacyProtectionService.class));
                                }
                            } catch (RuntimeException unavailable) {
                                protectionFailure = "Saved device protection could not start. Open Sleep and protection, then select Refresh system status.";
                                android.util.Log.w("FireTvProtection", protectionFailure, unavailable);
                            }
                            if (updateInspectionError != null) UpdateBlockService.reportPending(updateInspectionError);
                            else if (shouldStartUpdates && !UpdateBlockService.active())
                                UpdateBlockService.reportPending("Saved update protection needs an explicit retry. The saved choice was kept.");
                        } finally { complete.run(); }
                    }
                });
            }
        });
    }

    private boolean foregroundDenied() {
        if (Build.VERSION.SDK_INT < 28) return false;
        AppOpsManager ops = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        if (ops == null) return true;
        try {
            // AOSP exposes this operation name only through the system SDK; some TV builds may omit it.
            int mode = ops.checkOpNoThrow("android:start_foreground", Process.myUid(), getPackageName());
            return mode == AppOpsManager.MODE_IGNORED || mode == AppOpsManager.MODE_ERRORED;
        } catch (IllegalArgumentException unavailable) { return false; }
    }

    private static boolean empty(File data, String name) throws IOException {
        File directory = new File(data, name);
        if (!directory.exists()) return true;
        String[] entries = directory.list();
        if (entries == null) throw new IOException("Could not inspect existing launcher data.");
        return entries.length == 0;
    }

    private static int readMarker(File path) throws IOException {
        if (!path.exists() && !new File(path + ".bak").exists() && !new File(path + ".new").exists()) return -1;
        try (FileInputStream input = new AtomicFile(path).openRead()) {
            int value = input.read();
            if ((value != 0 && value != 1) || input.read() != -1) throw new IOException("Startup marker is invalid. Existing data was kept.");
            return value;
        }
    }

    private static void writeMarker(File path, int value) throws IOException {
        File directory = path.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Could not save startup status.");
        AtomicFile marker = new AtomicFile(path);
        FileOutputStream output = null;
        try { output = marker.startWrite(); output.write(value); marker.finishWrite(output); }
        catch (IOException failed) { if (output != null) marker.failWrite(output); throw failed; }
        if (readMarker(path) != value) throw new IOException("Could not verify startup status.");
    }
}
