package com.jeremykenedy.firetv.ui.nativeapp.widget.status;

import android.content.Context;
import com.jeremykenedy.firetv.ui.nativeapp.device.network.NetworkSnapshot;

public final class NetworkStatus {
    private NetworkStatus() {}
    public static StatusReport read(Context context) {
        try { return StatusReport.network(NetworkSnapshot.read(context)); }
        catch (SecurityException denied) {
            return new StatusReport(StatusReport.Availability.PERMISSION_DENIED, "Android denied access to network status. Open the TV's network settings to inspect it.");
        } catch (RuntimeException unavailable) {
            return new StatusReport(StatusReport.Availability.UNAVAILABLE, "Network status could not be read on this device.");
        }
    }
}
