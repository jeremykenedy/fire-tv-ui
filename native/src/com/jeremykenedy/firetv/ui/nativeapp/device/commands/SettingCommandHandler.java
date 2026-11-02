package com.jeremykenedy.firetv.ui.nativeapp.device.commands;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import com.jeremykenedy.firetv.ui.nativeapp.device.HomeRoutingPolicy;
import com.jeremykenedy.firetv.ui.nativeapp.device.screensavers.ScreensaverEntry;
import com.jeremykenedy.firetv.ui.nativeapp.device.screensavers.ScreensaverSystem;

/** Called only by the explicit, ordered, DUMP-protected production receiver. */
public final class SettingCommandHandler {
    private SettingCommandHandler() {}

    public static String execute(final Context context, Intent intent) {
        if (context == null || !HomeRoutingPolicy.PRODUCTION_PACKAGE.equals(context.getPackageName())) {
            throw new SecurityException("Setting commands require the production application identity.");
        }
        if (intent == null || !"setting".equals(intent.getStringExtra("operation"))) {
            throw new IllegalArgumentException("Unsupported setting command.");
        }
        return execute(context, intent.getStringExtra("namespace"), intent.getStringExtra("key"), intent.getStringExtra("value"));
    }

    public static String execute(final Context context, String namespace, String key, String value) {
        if (context == null || !HomeRoutingPolicy.PRODUCTION_PACKAGE.equals(context.getPackageName()))
            throw new SecurityException("Setting commands require the production application identity.");
        return LegacySettingTransaction.execute(new SettingCommandPolicy.Store() {
            @Override public boolean permitted(String namespace) {
                boolean secure = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
                return "secure".equals(namespace) ? secure : secure || Settings.System.canWrite(context);
            }
            @Override public String read(String namespace, String key) {
                return "secure".equals(namespace) ? Settings.Secure.getString(context.getContentResolver(), key)
                        : Settings.System.getString(context.getContentResolver(), key);
            }
            @Override public boolean write(String namespace, String key, String value) {
                return "secure".equals(namespace) ? Settings.Secure.putString(context.getContentResolver(), key, value)
                        : Settings.System.putString(context.getContentResolver(), key, value);
            }
            @Override public boolean componentAvailable(String component) {
                String normalized = ScreensaverEntry.normalize(component);
                for (ScreensaverEntry entry : ScreensaverSystem.installed(context)) {
                    if (entry.component.equals(normalized)) return true;
                }
                return false;
            }
        }, new LegacySettingTransaction.Preferences() {
            private android.content.SharedPreferences preferences() { return context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE); }
            @Override public java.util.Map<String, ?> read() { return preferences().getAll(); }
            @Override public boolean write(java.util.Map<String, Object> values) {
                android.content.SharedPreferences.Editor editor = preferences().edit();
                for (java.util.Map.Entry<String, Object> entry : values.entrySet()) {
                    Object value = entry.getValue();
                    if (value == null) editor.remove(entry.getKey());
                    else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
                    else if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
                    else if (value instanceof String) editor.putString(entry.getKey(), (String) value);
                    else throw new IllegalArgumentException("Invalid saved setting type.");
                }
                return editor.commit();
            }
        }, namespace, key, value);
    }
}
