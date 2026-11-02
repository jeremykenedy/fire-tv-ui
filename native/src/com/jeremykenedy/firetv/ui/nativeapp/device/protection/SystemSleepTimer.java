package com.jeremykenedy.firetv.ui.nativeapp.device.protection;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;

/** Explicit single-setting writes with readback; no startup enforcement or permission bypass. */
public final class SystemSleepTimer {
    public enum Result { SAVED, UNAVAILABLE, FAILED_RESTORED, RECOVERY_REQUIRED }
    interface Store {
        boolean permitted();
        String read();
        boolean write(String value);
    }
    private SystemSleepTimer() {}
    public static boolean available(Context context) {
        return supported(store(context));
    }
    public static Result setMinutes(Context context, String minutes) {
        return write(store(context), milliseconds(minutes));
    }
    static int milliseconds(String minutes) {
        if (minutes == null || !minutes.matches("[0-9]{1,5}"))
            throw new IllegalArgumentException("Enter whole minutes from 1 to 35791.");
        int value = Integer.parseInt(minutes);
        if (value < 1 || value > 35791) throw new IllegalArgumentException("Choose 1 to 35791 minutes.");
        return value * 60000;
    }
    private static boolean supported(Store store) {
        try {
            if (!store.permitted()) return false;
            String previous = store.read();
            if (previous == null) return false;
            Integer.parseInt(previous);
            return true;
        } catch (RuntimeException unavailable) { return false; }
    }
    static Result write(Store store, int milliseconds) {
        if (milliseconds <= 0) throw new IllegalArgumentException("Choose a positive sleep timeout.");
        if (!supported(store)) return Result.UNAVAILABLE;
        final String before;
        try { before = store.read(); }
        catch (RuntimeException unavailable) { return Result.UNAVAILABLE; }
        if (before == null) return Result.UNAVAILABLE;
        String wanted = String.valueOf(milliseconds);
        if (before.equals(wanted)) return Result.SAVED;
        try {
            if (store.write(wanted) && wanted.equals(store.read())) return Result.SAVED;
        } catch (RuntimeException denied) { }
        try {
            String current = store.read();
            if (before.equals(current)) return Result.FAILED_RESTORED;
            // Do not overwrite an intervening system or owner change.
            if (!wanted.equals(current)) return Result.RECOVERY_REQUIRED;
            if (store.write(before) && before.equals(store.read())) return Result.FAILED_RESTORED;
        } catch (RuntimeException denied) { }
        return Result.RECOVERY_REQUIRED;
    }
    private static Store store(final Context context) {
        return new Store() {
            @Override public boolean permitted() {
                return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
            }
            @Override public String read() { return Settings.Secure.getString(context.getContentResolver(), "sleep_timeout"); }
            @Override public boolean write(String value) { return Settings.Secure.putString(context.getContentResolver(), "sleep_timeout", value); }
        };
    }
}
