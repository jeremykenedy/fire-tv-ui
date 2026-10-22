package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;
import android.view.InputDevice;

/** Preserves native recognition for the same previously supported remote. */
final class EquipmentControl {
    static final String RECOGNIZED = "show_dc_settings";
    static final String REMOTE = "fire_tv_ui_equipment_remote";

    private EquipmentControl() {
    }

    static void repair(Context context) {
        if (!DeviceSettings.hasWifiAdb(context)) {
            return;
        }
        InputDevice remote = null;
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice candidate = InputDevice.getDevice(id);
            if (candidate == null || !candidate.isExternal() || candidate.isVirtual()
                    || !"Amazon Fire TV Remote".equals(candidate.getName())
                    || candidate.getVendorId() == 0 || candidate.getProductId() == 0) {
                continue;
            }
            // With multiple remotes, the native flag does not identify which
            // one supports equipment control. Do not infer a new identity.
            if (remote != null) {
                return;
            }
            remote = candidate;
        }
        if (remote == null || remote.getDescriptor() == null || remote.getDescriptor().isEmpty()) {
            return;
        }
        String descriptor = remote.getDescriptor();
        if (Settings.Global.getInt(context.getContentResolver(), RECOGNIZED, 0) == 1) {
            if (!descriptor.equals(DeviceSettings.prefs(context).getString(REMOTE, ""))) {
                DeviceSettings.prefs(context).edit().putString(REMOTE, descriptor).commit();
            }
            return;
        }
        if (!descriptor.equals(DeviceSettings.prefs(context).getString(REMOTE, ""))) {
            return;
        }
        if (!Settings.Global.putInt(context.getContentResolver(), RECOGNIZED, 1)
                || Settings.Global.getInt(context.getContentResolver(), RECOGNIZED, 0) != 1) {
            throw new IllegalStateException("The TV did not restore recognition of the connected remote.");
        }
        Log.i("FireTvEquipmentControl", "Restored recognition of the previously supported connected remote.");
    }
}
