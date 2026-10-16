package com.jeremykenedy.firetv.ui;

import android.Manifest;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Display;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** User-requested local inspection. Never changes device settings or sends a report. */
public final class DiagnosticsActivity extends CardActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView report;
    private TextView status;
    private TextView refresh;
    private TextView save;
    private String snapshot;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        title("Diagnostics");
        LinearLayout body = scrollBody();
        body.addView(
                text(
                        "Read-only checks. Nothing is repaired, reset or uploaded. "
                                + "Unavailable measurements are not treated as successful checks.",
                        17,
                        MUTED));
        refresh =
                button(
                        "Refresh report",
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                collect();
                            }
                        });
        save =
                button(
                        "Save report on TV",
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                saveReport();
                            }
                        });
        addButton(body, refresh);
        addButton(body, save);
        status = text("", 16, MUTED);
        body.addView(status);
        report = text("", 17, INK);
        report.setPadding(0, dp(14), 0, dp(14));
        body.addView(report);
        refresh.requestFocus();
        card.addOnLayoutChangeListener(
                new View.OnLayoutChangeListener() {
                    @Override
                    public void onLayoutChange(
                            View view,
                            int left,
                            int top,
                            int right,
                            int bottom,
                            int oldLeft,
                            int oldTop,
                            int oldRight,
                            int oldBottom) {
                        if (right > left && bottom > top) {
                            view.removeOnLayoutChangeListener(this);
                            collect();
                        }
                    }
                });
    }

    private void collect() {
        refresh.setEnabled(false);
        save.setEnabled(false);
        status.setText("Reading TV status...");
        final int contentWidth = card.getWidth(), contentHeight = card.getHeight();
        worker.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        final String result = inspect(contentWidth, contentHeight);
                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        if (isDestroyed()) return;
                                        snapshot = result;
                                        report.setText(result);
                                        status.setText(
                                                "Report ready. Values are a point-in-time"
                                                        + " snapshot.");
                                        refresh.setEnabled(true);
                                        save.setEnabled(true);
                                    }
                                });
                    }
                });
    }

    private String inspect(final int contentWidth, final int contentHeight) {
        StringBuilder text = new StringBuilder("Fire TV UI diagnostics\n");
        text.append(DateFormat.getDateTimeInstance().format(new Date())).append('\n');
        text.append("Android API ").append(Build.VERSION.SDK_INT).append("\n\n");
        section(
                text,
                "Selected screensaver",
                new Reading() {
                    @Override
                    public String read() {
                        String selected =
                                Settings.Secure.getString(
                                        getContentResolver(), "screensaver_components");
                        return "Selected: "
                                + (selected == null ? "None reported" : selected)
                                + "\nEnabled: "
                                + DiagnosticValues.enabled(
                                        Settings.Secure.getString(
                                                getContentResolver(), "screensaver_enabled"));
                    }
                });
        section(
                text,
                "Active DreamService",
                new Reading() {
                    @Override
                    public String read() throws Exception {
                        if (checkSelfPermission(Manifest.permission.DUMP)
                                != PackageManager.PERMISSION_GRANTED)
                            return "Unavailable: Android does not grant this app access to the"
                                    + " active DreamService. Use Toolkit diagnostics over ADB"
                                    + " for system-wide status.";
                        return "Current system Dream record: "
                                + DiagnosticValues.currentDream(dreamDump())
                                + "\nA bound record does not prove that frames are rendering.";
                    }
                });
        section(
                text,
                "Installed screensavers",
                new Reading() {
                    @Override
                    public String read() throws Exception {
                        PackageManager pm = getPackageManager();
                        TreeMap<String, String> installed = new TreeMap<>();
                        for (ResolveInfo result :
                                pm.queryIntentServices(
                                        new Intent("android.service.dreams.DreamService"), 0)) {
                            ServiceInfo service = result.serviceInfo;
                            if (service == null || !Screensavers.isScreensaver(service.permission))
                                continue;
                            PackageInfo info = pm.getPackageInfo(service.packageName, 0);
                            long code =
                                    Build.VERSION.SDK_INT >= 28
                                            ? info.getLongVersionCode()
                                            : info.versionCode;
                            installed.put(
                                    service.packageName,
                                    result.loadLabel(pm)
                                            + "\n  "
                                            + service.packageName
                                            + "\n  Version "
                                            + info.versionName
                                            + " ("
                                            + code
                                            + ")");
                        }
                        return installed.isEmpty()
                                ? "No visible DreamServices reported by Android."
                                : android.text.TextUtils.join("\n\n", installed.values());
                    }
                });
        section(
                text,
                "Display and rendering",
                new Reading() {
                    @Override
                    public String read() {
                        Display display = getWindowManager().getDefaultDisplay();
                        Display.Mode current = display.getMode();
                        StringBuilder value = new StringBuilder("Android-reported display mode: ");
                        value.append(mode(current)).append("\nSupported modes:");
                        for (Display.Mode mode : display.getSupportedModes())
                            value.append("\n  ").append(mode(mode));
                        value.append("\nFire TV UI content size: ")
                                .append(DiagnosticValues.contentSize(contentWidth, contentHeight));
                        return value
                                + "\n"
                                + "Actual screensaver surface buffer: unavailable from this app. A"
                                + " 4K display mode does not prove a 4K rendering buffer.\n"
                                + "Screensaver FPS: unavailable. Display refresh rate and Android"
                                + " UI frame statistics do not measure OpenGL, Filament or video"
                                + " playback FPS.";
                    }
                });
        section(
                text,
                "Memory and storage",
                new Reading() {
                    @Override
                    public String read() {
                        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
                        ((ActivityManager) getSystemService(Context.ACTIVITY_SERVICE))
                                .getMemoryInfo(memory);
                        StatFs disk = new StatFs(Environment.getDataDirectory().getPath());
                        return "TV RAM: "
                                + PackageStorage.size(memory.availMem)
                                + " available / "
                                + PackageStorage.size(memory.totalMem)
                                + " total\nLow-memory state: "
                                + memory.lowMemory
                                + "\nTV internal storage: "
                                + PackageStorage.size(disk.getAvailableBytes())
                                + " available / "
                                + PackageStorage.size(disk.getTotalBytes())
                                + " total";
                    }
                });
        section(
                text,
                "Recent Fire TV UI exits only",
                new Reading() {
                    @Override
                    public String read() {
                        if (Build.VERSION.SDK_INT < 30)
                            return "Unavailable: Android API 30 or later is required.";
                        ActivityManager manager =
                                (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                        List<ApplicationExitInfo> exits =
                                manager.getHistoricalProcessExitReasons(getPackageName(), 0, 5);
                        StringBuilder value = new StringBuilder();
                        for (ApplicationExitInfo exit : exits) {
                            value.append(
                                            DateFormat.getDateTimeInstance()
                                                    .format(new Date(exit.getTimestamp())))
                                    .append(" | reason ")
                                    .append(exit.getReason())
                                    .append(" | status ")
                                    .append(exit.getStatus())
                                    .append('\n');
                        }
                        if (exits.isEmpty())
                            value.append("No Fire TV UI exits returned by Android.\n");
                        return value
                                + "Screensaver app crashes and exits: unavailable without"
                                + " system-wide access. This is not a screensaver crash check.";
                    }
                });
        return text.toString();
    }

    private static String mode(Display.Mode mode) {
        return mode.getPhysicalWidth()
                + "x"
                + mode.getPhysicalHeight()
                + " at "
                + String.format(java.util.Locale.ROOT, "%.2f", mode.getRefreshRate())
                + " Hz";
    }

    private static void section(StringBuilder report, String title, Reading reading) {
        report.append(title).append('\n');
        try {
            report.append(reading.read());
        } catch (Exception unavailable) {
            report.append("Unavailable: ").append(unavailable.getClass().getSimpleName());
        }
        report.append("\n\n");
    }

    private static String dreamDump() throws Exception {
        Process process =
                new ProcessBuilder("/system/bin/dumpsys", "dreams")
                        .redirectErrorStream(true)
                        .start();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS))
                throw new java.io.IOException("System inspection timed out");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream stream = process.getInputStream()) {
                byte[] buffer = new byte[1024];
                int count;
                while ((count = stream.read(buffer)) != -1 && bytes.size() < 16384)
                    bytes.write(buffer, 0, count);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            process.destroy();
        }
    }

    private void saveReport() {
        if (snapshot == null) return;
        final byte[] bytes = snapshot.getBytes(StandardCharsets.UTF_8);
        save.setEnabled(false);
        worker.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        String message;
                        try {
                            String name =
                                    "fire-tv-ui-diagnostics-" + System.currentTimeMillis() + ".txt";
                            if (Build.VERSION.SDK_INT >= 29) {
                                ContentValues values = new ContentValues();
                                values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                                values.put(
                                        MediaStore.MediaColumns.RELATIVE_PATH,
                                        Environment.DIRECTORY_DOWNLOADS);
                                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                                Uri uri =
                                        getContentResolver()
                                                .insert(
                                                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                                        values);
                                if (uri == null)
                                    throw new java.io.IOException("Downloads is unavailable");
                                try {
                                    try (OutputStream stream =
                                            getContentResolver().openOutputStream(uri)) {
                                        stream.write(bytes);
                                    }
                                    values.clear();
                                    values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                                    getContentResolver().update(uri, values, null, null);
                                } catch (Exception failed) {
                                    getContentResolver().delete(uri, null, null);
                                    throw failed;
                                }
                            } else {
                                File folder =
                                        Environment.getExternalStoragePublicDirectory(
                                                Environment.DIRECTORY_DOWNLOADS);
                                if (!folder.isDirectory() && !folder.mkdirs())
                                    throw new java.io.IOException("Downloads is unavailable");
                                try (OutputStream stream =
                                        new FileOutputStream(new File(folder, name))) {
                                    stream.write(bytes);
                                }
                            }
                            message = "Saved to TV Downloads/" + name + ". Nothing was uploaded.";
                        } catch (Exception failed) {
                            message =
                                    "Could not save locally: " + failed.getClass().getSimpleName();
                        }
                        final String result = message;
                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!isDestroyed()) {
                                            status.setText(result);
                                            save.setEnabled(true);
                                        }
                                    }
                                });
                    }
                });
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private interface Reading {
        String read() throws Exception;
    }
}
