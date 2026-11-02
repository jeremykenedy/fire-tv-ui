package com.jeremykenedy.firetv.nowplayingfixture;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public final class FixtureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        FixtureSession.publish(this);
        TextView label = new TextView(this);
        label.setText("Synthetic MediaSession fixture. No audio or video playback.");
        label.setTextSize(24);
        setContentView(label);
    }
}
