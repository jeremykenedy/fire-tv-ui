package com.jeremykenedy.firetv.ui.nativeapp.device.network;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.StatFs;
import java.security.MessageDigest;
import java.util.Locale;

/** Allowlisted public build and aggregate runtime values. No preferences, identifiers or activity history. */
public final class DiagnosticReport {
    private DiagnosticReport() {}

    public static String read(Context context) {
        StringBuilder out = new StringBuilder("Fire TV UI diagnostics\nRead-only point-in-time snapshot\n\n");
        out.append("Device: ").append(clean(Build.MANUFACTURER)).append(' ').append(clean(Build.MODEL));
        out.append("\nAndroid API: ").append(Build.VERSION.SDK_INT);
        out.append("\nAndroid release: ").append(clean(Build.VERSION.RELEASE));
        out.append("\nApp package: ").append(clean(context.getPackageName()));
        try {
            int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), flags);
            out.append("\nApp version: ").append(clean(info.versionName)).append(" (")
                    .append(Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode).append(')');
            Signature[] signatures = Build.VERSION.SDK_INT >= 28 && info.signingInfo != null
                    ? info.signingInfo.getApkContentsSigners() : info.signatures;
            if (signatures == null || signatures.length == 0) out.append("\nSigner SHA-256: Not reported");
            else for (Signature signature : signatures) out.append("\nSigner SHA-256: ").append(sha256(signature.toByteArray()));
        } catch (Exception unavailable) { out.append("\nApp signing/version details: Unavailable"); }
        try {
            StatFs storage = new StatFs(context.getFilesDir().getAbsolutePath());
            out.append("\n\nApp storage volume total bytes: ").append(storage.getTotalBytes());
            out.append("\nApp storage volume available bytes: ").append(storage.getAvailableBytes());
        } catch (RuntimeException unavailable) { out.append("\n\nStorage measurement: Unavailable"); }
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        try {
            if (manager == null) out.append("\nSystem memory: Unavailable");
            else {
                ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
                manager.getMemoryInfo(memory);
                out.append("\nSystem memory total bytes: ").append(memory.totalMem);
                out.append("\nSystem memory available bytes: ").append(memory.availMem);
                out.append("\nSystem low-memory flag: ").append(memory.lowMemory);
            }
        } catch (RuntimeException unavailable) { out.append("\nSystem memory: Unavailable"); }
        Runtime runtime = Runtime.getRuntime();
        out.append("\nApp Java heap used bytes: ").append(runtime.totalMemory() - runtime.freeMemory());
        out.append("\nApp Java heap limit bytes: ").append(runtime.maxMemory());
        out.append("\n\nValues do not measure rendering FPS, remote latency, hardware health or other apps.\n");
        out.append("Export excludes network addresses, serial numbers, account data, preferences, logs and activity history.\n");
        return out.toString();
    }

    static String clean(String value) {
        if (value == null) return "Not reported";
        return value.replaceAll("[\\p{Cntrl}]", " ").substring(0, Math.min(value.length(), 160));
    }

    static String sha256(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte part : MessageDigest.getInstance("SHA-256").digest(bytes))
            result.append(String.format(Locale.ROOT, "%02x", part & 255));
        return result.toString();
    }
}
