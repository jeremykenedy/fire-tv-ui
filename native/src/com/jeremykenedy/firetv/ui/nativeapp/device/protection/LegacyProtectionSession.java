package com.jeremykenedy.firetv.ui.nativeapp.device.protection;

/** Reconciles current settings before arming a saved protection choice. */
public final class LegacyProtectionSession {
    private boolean armed;
    public LegacyDeviceProtection.Result check(LegacyDeviceProtection.Store store, java.util.Map<String, ?> prefs) {
        synchronized (LegacyDeviceProtection.LOCK) {
            LegacyDeviceProtection.Result result = armed ? LegacyDeviceProtection.repair(store, prefs)
                    : LegacyDeviceProtection.inspect(store, prefs);
            armed = result.complete();
            return result;
        }
    }
    public void preferencesChanged() { armed = false; }
    public boolean armed() { return armed; }
}
