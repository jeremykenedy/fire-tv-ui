package com.jeremykenedy.firetv.ui;

/** Original window-only adapter preserving an existing enabled accessibility component. */
public final class HomeRedirectService extends com.jeremykenedy.firetv.ui.nativeapp.device.HomeKeyService {
    @Override protected boolean filtersHomeKeys() { return false; }
}
