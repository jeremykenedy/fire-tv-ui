package com.jeremykenedy.firetv.ui.nativeapp;

import android.app.Activity;
import android.os.Bundle;

/** Shared lifecycle for launcher settings pages. */
public abstract class MenuActivity extends Activity {
    private boolean transitionReady;
    private float menuDim;
    private boolean panel;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        transitionReady = SourceStartup.ready(this);
        android.content.res.TypedArray attributes = obtainStyledAttributes(new int[]{
                android.R.attr.windowIsTranslucent, android.R.attr.backgroundDimAmount});
        panel = attributes.getBoolean(0, false);
        menuDim = attributes.getFloat(1, 0.32f);
        attributes.recycle();
        if (transitionReady) MenuTransition.apply(this, true);
    }
    @Override protected void onResume() {
        super.onResume();
        if (panel) {
            findViewById(android.R.id.content).setVisibility(android.view.View.VISIBLE);
            getWindow().setDimAmount(menuDim);
        }
    }
    @Override protected void onPause() {
        if (panel) {
            findViewById(android.R.id.content).setVisibility(android.view.View.INVISIBLE);
            getWindow().setDimAmount(0);
        }
        super.onPause();
    }
    @Override public void finish() {
        super.finish();
        if (transitionReady && SourceStartup.ready(this)) MenuTransition.apply(this, false);
    }
}
