package com.jeremykenedy.firetv.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class SystemControlsActivity extends CardActivity {
    private static final int VPN = 10;
    private TextView home;
    private TextView updates;
    private TextView appstore;
    private TextView protection;
    private TextView debugging;
    private TextView state;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        title("Startup and protection");
        LinearLayout body = scrollBody();
        state = text("", 16, MUTED);
        state.setPadding(0, 0, 0, dp(12));
        body.addView(state);
        home = button("", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    HomeControl.setEnabled(SystemControlsActivity.this,
                            !HomeControl.isEnabled(SystemControlsActivity.this), true);
                    ProtectionService.start(SystemControlsActivity.this);
                    refresh();
                } catch (Exception failed) {
                    error(failed);
                }
            }
        });
        updates = button("", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (DeviceSettings.prefs(SystemControlsActivity.this)
                        .getBoolean(ProtectionService.BLOCK_UPDATES, false)) {
                    block(false);
                } else {
                    enableBlocker();
                }
            }
        });
        appstore = button("", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean before = DeviceSettings.prefs(SystemControlsActivity.this)
                        .getBoolean(ProtectionService.BLOCK_APPSTORE, false);
                if (before) {
                    appstore(false);
                } else {
                    new AlertDialog.Builder(SystemControlsActivity.this).setTitle("Block Appstore auto-updates?")
                            .setMessage("This blocks Amazon Appstore traffic, including manual downloads. "
                                    + "Turn it off when you want to use the Appstore. Streaming, weather, "
                                    + "and screensaver downloads in Fire TV UI remain available.")
                            .setNegativeButton("Cancel", null).setPositiveButton("Block", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    appstore(true);
                                }
                            }).show();
                }
            }
        });
        protection = button("", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean before = DeviceSettings.prefs(SystemControlsActivity.this).getBoolean(DeviceSettings.PROTECT, false);
                if (!before) {
                    DeviceSettings.capture(SystemControlsActivity.this);
                }
                DeviceSettings.prefs(SystemControlsActivity.this).edit().putBoolean(DeviceSettings.PROTECT, !before).commit();
                ProtectionService.start(SystemControlsActivity.this);
                refresh();
            }
        });
        addButton(body, home);
        addButton(body, protection);
        if (DeviceSettings.hasWifiAdb(this)) {
            debugging = button("", new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    try {
                        DeviceSettings.keepAdb(SystemControlsActivity.this,
                                !DeviceSettings.prefs(SystemControlsActivity.this)
                                        .getBoolean(DeviceSettings.KEEP_ADB, true));
                        ProtectionService.start(SystemControlsActivity.this);
                        refresh();
                    } catch (Exception failed) {
                        error(failed);
                    }
                }
            });
            addButton(body, debugging);
        }
        addButton(body, updates);
        addButton(body, appstore);
        TextView hint = text("Home routing returns to Fire TV UI and starts it after boot. Protection checks "
                + "your screensaver and timers locally. ADB protection keeps network debugging enabled "
                + "after restart and if Fire OS changes it. Turning it off allows the TV's debugging switch to change. "
                + "Update protection discards traffic only from "
                + "Amazon update packages; it sends no data to a VPN server. Android allows one active VPN.", 16, MUTED);
        hint.setPadding(0, dp(12), 0, dp(12));
        body.addView(hint);
        home.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean blocked = DeviceSettings.prefs(this).getBoolean(ProtectionService.BLOCK_UPDATES, false);
        home.setText(HomeControl.isEnabled(this) ? "Disable Fire TV UI as startup and Home app"
                : "Enable Fire TV UI as startup and Home app");
        protection.setText("Protect Home, screensaver and timers: "
                + (DeviceSettings.prefs(this).getBoolean(DeviceSettings.PROTECT, false) ? "On" : "Off"));
        updates.setText("Block Amazon system auto-updates: "
                + (ProtectionService.updatesBlocked() ? "On" : blocked ? "Waiting for permission" : "Off"));
        appstore.setText("Include Amazon Appstore updates: "
                + (DeviceSettings.prefs(this).getBoolean(ProtectionService.BLOCK_APPSTORE, false) ? "On" : "Off"));
        state.setText(ProtectionService.status());
        if (debugging != null) {
            debugging.setText("Keep ADB debugging enabled: "
                    + (DeviceSettings.prefs(this).getBoolean(DeviceSettings.KEEP_ADB, true) ? "On" : "Off"));
        }
    }

    private void enableBlocker() {
        String always = Settings.Secure.getString(getContentResolver(), "always_on_vpn_app");
        if (always != null && !always.isEmpty() && !"null".equals(always) && !getPackageName().equals(always)) {
            error(new IllegalStateException("Another app is the TV's always-on VPN. Change that VPN setting before enabling update protection."));
            return;
        }
        Intent permission = VpnService.prepare(this);
        if (permission != null) {
            startActivityForResult(permission, VPN);
        } else {
            block(true);
        }
    }

    private void block(boolean enabled) {
        DeviceSettings.prefs(this).edit().putBoolean(ProtectionService.BLOCK_UPDATES, enabled).commit();
        if (DeviceSettings.canWrite(this)) {
            String always = Settings.Secure.getString(getContentResolver(), "always_on_vpn_app");
            if (enabled || getPackageName().equals(always)) {
                Settings.Secure.putString(getContentResolver(), "always_on_vpn_app", enabled ? getPackageName() : null);
                Settings.Secure.putInt(getContentResolver(), "always_on_vpn_lockdown", 0);
            }
        }
        ProtectionService.start(this);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        }, 400);
    }

    private void appstore(boolean enabled) {
        DeviceSettings.prefs(this).edit().putBoolean(ProtectionService.BLOCK_APPSTORE, enabled).commit();
        if (DeviceSettings.prefs(this).getBoolean(ProtectionService.BLOCK_UPDATES, false)) {
            ProtectionService.start(this);
        }
        refresh();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == VPN && result == RESULT_OK) {
            block(true);
        }
    }
}
