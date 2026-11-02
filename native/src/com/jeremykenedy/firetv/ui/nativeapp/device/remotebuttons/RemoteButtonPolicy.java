package com.jeremykenedy.firetv.ui.nativeapp.device.remotebuttons;

import java.util.HashSet;
import java.util.Set;

/** Only Android's dedicated video/featured application keys are eligible. */
public final class RemoteButtonPolicy {
    private final Set<Integer> consumed = new HashSet<>();
    public static boolean supported(int key) { return key >= 289 && key <= 300; }
    public interface Launch { boolean open(); }
    public boolean dispatch(int key, int action, int repeats, boolean cancelled, Launch launch) {
        if (!supported(key)) return false;
        if (action == 1) return consumed.remove(key);
        if (action != 0) return false;
        if (repeats > 0) return consumed.contains(key);
        consumed.remove(key);
        if (cancelled || launch == null || !launch.open()) return false;
        consumed.add(key);
        return true;
    }
    public void clear() { consumed.clear(); }
}
