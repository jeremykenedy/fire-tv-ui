package com.jeremykenedy.firetv.ui.nativeapp.device.screensavers;

/** Coordinates the two existing selection writes without overwriting an intervening change. */
public final class LegacyDreamSelection {
    public interface Store {
        String component();
        int enabled();
        void component(String value);
        void enable();
    }
    private LegacyDreamSelection() {}
    public static void select(Store store, String component) {
        String previous = store.component();
        int wasEnabled = store.enabled();
        store.component(component);
        try { store.enable(); }
        catch (RuntimeException failed) {
            try {
                if (!ScreensaverEntry.normalize(component).equals(ScreensaverEntry.firstActive(store.component()))
                        || store.enabled() != wasEnabled)
                    throw new IllegalStateException("The current screensaver changed during the operation.");
                store.component(previous);
            } catch (RuntimeException recovery) {
                throw new IllegalStateException("The screensaver change could not be restored. Review TV settings.", recovery);
            }
            throw failed;
        }
    }
}
