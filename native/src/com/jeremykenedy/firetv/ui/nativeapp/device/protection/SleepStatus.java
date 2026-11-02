package com.jeremykenedy.firetv.ui.nativeapp.device.protection;

import android.content.Context;
import android.provider.Settings;

public final class SleepStatus {
    private SleepStatus() {}
    public static String read(Context context) {
        StringBuilder value = new StringBuilder();
        value.append("System screen idle timeout: ").append(timeout(system(context, "screen_off_timeout", false)));
        value.append("\nSystem sleep timeout: ").append(timeout(system(context, "sleep_timeout", true)));
        String autoWake = system(context, "str.auto_wake_up_enabled", true);
        value.append("\nFire OS Alexa auto-wake setting: ").append(autoWake == null ? "Not reported"
                : "1".equals(autoWake) ? "Enabled" : "0".equals(autoWake) ? "Disabled" : "Unknown value");
        value.append("\n\nThese settings do not prove that the TV, Alexa or connected equipment will wake or sleep. The launcher-only option above has no effect while another app is in front.");
        return value.toString();
    }
    private static String system(Context context, String key, boolean secure) {
        try { return secure ? Settings.Secure.getString(context.getContentResolver(), key)
                : Settings.System.getString(context.getContentResolver(), key); }
        catch (RuntimeException unavailable) { return null; }
    }
    static String timeout(String raw) {
        if (raw == null) return "Not reported";
        try {
            long value = Long.parseLong(raw);
            if (value < 0) return "System default (" + value + ")";
            if (value == 0) return "0 ms (disabled or system-defined behavior)";
            return value % 60000 == 0 ? (value / 60000) + " minutes" : value + " ms";
        } catch (NumberFormatException unknown) { return "Unrecognized value"; }
    }
}
