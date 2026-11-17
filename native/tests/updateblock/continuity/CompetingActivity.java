package com.jeremykenedy.firetv.updatecontinuityproof;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

public final class CompetingActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        Button start = new Button(this); start.setText("Start competing VPN");
        start.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View view) {
            Intent consent = VpnService.prepare(CompetingActivity.this);
            if (consent == null) begin(); else startActivityForResult(consent, 901);
        } }); page.addView(start);
        Button stop = new Button(this); stop.setText("Stop competing VPN");
        stop.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View view) { stopService(new Intent(CompetingActivity.this, CompetingVpn.class)); } }); page.addView(stop);
        setContentView(page);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 901 && result == RESULT_OK) begin();
    }
    private void begin() { startForegroundService(new Intent(this, CompetingVpn.class)); }
}
