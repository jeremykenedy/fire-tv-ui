package com.jeremykenedy.firetv.migrationprobe;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

/** Original, account-free witness for an explicitly selected fixture component. */
public final class ProbeActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this);
        text.setBackgroundColor(0xff142035); text.setTextColor(0xffffffff);
        text.setTextSize(24); text.setGravity(Gravity.CENTER);
        text.setText("Migration fixture opened\n" + getIntent().getComponent().flattenToString());
        setContentView(text);
    }
}
