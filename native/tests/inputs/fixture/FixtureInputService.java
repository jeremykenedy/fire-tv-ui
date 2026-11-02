package com.jeremykenedy.firetv.ui.inputfixture;

import android.media.tv.TvInputService;

/** Isolated tuner discovery only; no tune operation or hardware control. */
public final class FixtureInputService extends TvInputService {
    @Override public Session onCreateSession(String inputId) { return null; }
}
