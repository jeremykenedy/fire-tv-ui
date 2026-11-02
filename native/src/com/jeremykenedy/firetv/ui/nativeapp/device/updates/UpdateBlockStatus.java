package com.jeremykenedy.firetv.ui.nativeapp.device.updates;

import java.util.concurrent.CopyOnWriteArraySet;

/** Process-local status notification; listeners are not invoked while holding a lock. */
public final class UpdateBlockStatus {
    public interface Listener { void onStatusChanged(); }
    private final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();
    private volatile String text = "Update protection is off.";
    public String text() { return text; }
    public void add(Listener listener) { if (listener != null) listeners.add(listener); }
    public void remove(Listener listener) { listeners.remove(listener); }
    public void report(String value) {
        text = value == null || value.isEmpty() ? "Update protection is unavailable." : value;
        for (Listener listener : listeners) {
            try { listener.onStatusChanged(); } catch (RuntimeException ignored) { }
        }
    }
}
