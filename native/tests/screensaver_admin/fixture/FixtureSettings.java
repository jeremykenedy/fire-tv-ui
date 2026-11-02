package com.jeremykenedy.firetv.ui.saverfixture;
public final class FixtureSettings extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        android.widget.TextView label = new android.widget.TextView(this);
        label.setText("Administration fixture configuration");
        label.setTextSize(30);
        setContentView(label);
    }
}
