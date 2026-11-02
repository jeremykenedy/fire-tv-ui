package com.jeremykenedy.firetv.ui.nativeapp.widget.quickactions;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

/** Emulator-only external app with one explicitly supported HTTPS content link. */
public final class QuickActionFixture extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this); text.setTextSize(28);
        text.setText(Intent.ACTION_VIEW.equals(getIntent().getAction()) ? "Fixture content link opened" : "Fixture app opened");
        setContentView(text);
    }
}
