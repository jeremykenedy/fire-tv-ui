package com.jeremykenedy.firetv.ui.nativeapp.device.protection;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import com.jeremykenedy.firetv.ui.nativeapp.SourceStartup;
import com.jeremykenedy.firetv.ui.nativeapp.device.screensavers.ScreensaverEntry;
import com.jeremykenedy.firetv.ui.nativeapp.device.screensavers.ScreensaverSystem;
import java.util.Collections;

/** Production-only adapter. A lifecycle owner must call this after migration succeeds. */
public final class AndroidLegacyDeviceProtection {
    private AndroidLegacyDeviceProtection() {}
    public static LegacyDeviceProtection.Result repair(final Context context) {
        if (!"com.jeremykenedy.firetv.ui".equals(context.getPackageName()) || !SourceStartup.ready(context))
            throw new IllegalStateException("Device protection requires completed production startup.");
        return LegacyDeviceProtection.repair(store(context), preferences(context));
    }

    public static LegacyDeviceProtection.Result inspect(final Context context) {
        if (!"com.jeremykenedy.firetv.ui".equals(context.getPackageName()))
            throw new IllegalStateException("Device protection requires production identity.");
        return LegacyDeviceProtection.inspect(store(context), preferences(context));
    }

    static java.util.Map<String, ?> preferences(Context context) {
        return Collections.unmodifiableMap(context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).getAll());
    }

    static LegacyDeviceProtection.Store store(final Context context) {
        return new LegacyDeviceProtection.Store() {
            @Override public boolean writable() {
                return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
            }
            @Override public boolean fireTv() { return context.getPackageManager().hasSystemFeature("amazon.hardware.fire_tv"); }
            @Override public String read(String namespace, String key) {
                if ("secure".equals(namespace)) return Settings.Secure.getString(context.getContentResolver(), key);
                if ("global".equals(namespace)) return Settings.Global.getString(context.getContentResolver(), key);
                return Settings.System.getString(context.getContentResolver(), key);
            }
            @Override public boolean write(String namespace, String key, String value) {
                if ("secure".equals(namespace)) return Settings.Secure.putString(context.getContentResolver(), key, value);
                if ("global".equals(namespace)) return Settings.Global.putString(context.getContentResolver(), key, value);
                return Settings.System.putString(context.getContentResolver(), key, value);
            }
            @Override public boolean availableDream(String component) {
                String wanted = ScreensaverEntry.normalize(component);
                if (wanted == null || wanted.isEmpty()) return false;
                for (ScreensaverEntry entry : ScreensaverSystem.installed(context)) if (wanted.equals(entry.component)) return true;
                return false;
            }
        };
    }
}
