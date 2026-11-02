package com.jeremykenedy.firetv.ui.providerfixture;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.widget.TextView;

public final class FixtureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        showIntent(getIntent());
    }
    @Override public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showIntent(intent);
    }
    private void showIntent(Intent intent) {
        String data = intent.getDataString();
        getSharedPreferences("fixture", MODE_PRIVATE).edit().putString("opened", data == null ? "app" : data).commit();
        TextView title = new TextView(this);
        title.setText(data == null ? "Recommendation producer app" : "Published title: " + data);
        title.setTextSize(32);
        title.setPadding(64, 64, 64, 64);
        setContentView(title);
    }
}
