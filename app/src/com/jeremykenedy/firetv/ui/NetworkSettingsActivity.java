package com.jeremykenedy.firetv.ui;

import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class NetworkSettingsActivity extends CardActivity {
    private TextView details;
    private NetworkStatus status;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        title("Network and IP");
        LinearLayout body = scrollBody();
        TextView configure = button("Configure IP in TV settings", new View.OnClickListener() {
            @Override public void onClick(View view) { openNetworkSettings(); }
        });
        addButton(body, configure);
        addButton(body, button("Refresh network details", new View.OnClickListener() {
            @Override public void onClick(View view) { refresh(); }
        }));
        details = text("", 17, INK);
        details.setPadding(0, dp(16), 0, dp(16));
        details.setLineSpacing(dp(3), 1);
        body.addView(details);
        TextView help = text("For a fixed address, reserve this TV's IP in your router, or choose Static in the TV's network settings if available. "
                + "To return to automatic addressing, choose Automatic or DHCP there. "
                + "Fire TV UI shows the current connection; it does not change your router or network configuration.", 16, MUTED);
        body.addView(help);
        configure.requestFocus();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        status = NetworkStatus.read(this);
        details.setText(status.details);
    }

    private void openNetworkSettings() {
        String[] actions = status != null && status.ethernet
                ? new String[]{"android.settings.ETHERNET_SETTINGS", Settings.ACTION_WIFI_IP_SETTINGS,
                        Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_WIFI_SETTINGS}
                : new String[]{Settings.ACTION_WIFI_IP_SETTINGS, Settings.ACTION_WIFI_SETTINGS,
                        Settings.ACTION_WIRELESS_SETTINGS};
        if (!NetworkSettings.openAvailable(this, actions)) {
            error(new IllegalStateException("This TV does not expose network settings to apps. Open Network from the TV's factory settings."));
        }
    }

}
