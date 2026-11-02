package com.jeremykenedy.firetv.ui.nativeapp;

import android.app.Activity;
import android.os.Bundle;

/** Shared lifecycle for launcher settings pages. */
public abstract class MenuActivity extends Activity {
    private boolean transitionReady;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        transitionReady = SourceStartup.ready(this);
        if (transitionReady) MenuTransition.apply(this, true);
    }
    @Override public void finish() {
        super.finish();
        if (transitionReady && SourceStartup.ready(this)) MenuTransition.apply(this, false);
    }
}
