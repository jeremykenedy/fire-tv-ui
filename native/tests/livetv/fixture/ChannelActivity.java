package com.jeremykenedy.firetv.ui.livetvfixture;
public final class ChannelActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        android.widget.TextView title = new android.widget.TextView(this);
        title.setText("Fixture channel source link opened. No playback is provided.");
        title.setTextSize(28); setContentView(title);
    }
}
