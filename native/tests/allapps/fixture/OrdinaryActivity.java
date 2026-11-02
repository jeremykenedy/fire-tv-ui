package com.example.allappsfixture;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public final class OrdinaryActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView label = new TextView(this);
        label.setText("Ordinary launcher fixture opened"); label.setTextSize(28);
        setContentView(label);
    }
}
