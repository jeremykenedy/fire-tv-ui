package com.jeremykenedy.firetv.ui.nativeapp.device.updates;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import com.jeremykenedy.firetv.ui.nativeapp.SourceStartup;

/** Controls the two existing owned update-protection choices. */
public final class UpdateBlockActivity extends Activity {
    private static final int VPN_REQUEST = 312;
    private boolean blocked, resumed;
    private final UpdateBlockStatus.Listener statusListener = new UpdateBlockStatus.Listener() {
        @Override public void onStatusChanged() {
            runOnUiThread(new Runnable() { @Override public void run() { if (resumed && !blocked && SourceStartup.ready(UpdateBlockActivity.this)) refresh(); } });
        }
    };
    private Button updates, appstore;
    private TextView status;
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { blocked = true; return; }
        if (!"com.jeremykenedy.firetv.ui".equals(getPackageName())) { blocked = true; finish(); return; }
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "Amazon update protection", 28));
        updates = Pages.button(this, "", new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (!valid()) return;
                if (UpdateBlockPolicy.enabled(prefs().getAll())) setUpdates(false);
                else requestVpn();
            }
        });
        updates.setTag("update-protection:updates"); page.addView(updates);
        appstore = Pages.button(this, "", new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (!valid()) return;
                if (UpdateBlockPolicy.includesAppstore(prefs().getAll())) setAppstore(false);
                else new AlertDialog.Builder(UpdateBlockActivity.this).setTitle("Include Amazon Appstore?")
                        .setMessage("This blocks Appstore traffic, including manual downloads, while update protection is on.")
                        .setNegativeButton("Cancel", null).setPositiveButton("Include", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) { setAppstore(true); }
                        }).show();
            }
        });
        appstore.setTag("update-protection:appstore"); page.addView(appstore);
        Button retry = Pages.button(this, "Retry update protection", new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (!valid()) return;
                if (UpdateBlockPolicy.enabled(prefs().getAll())) requestVpn();
                else status.setText("Turn on update protection before retrying.");
            }
        });
        retry.setTag("update-protection:retry"); page.addView(retry);
        page.addView(Pages.text(this, "Update protection uses the TV's VPN slot and discards traffic only from the selected Amazon packages. It does not use a remote VPN server. If VPN lockdown is on, other apps may remain offline after protection stops.", 16));
        status = Pages.text(this, "", 16); status.setTag("update-protection:status"); page.addView(status);
        updates.requestFocus();
    }
    @Override protected void onResume() {
        super.onResume(); resumed = true;
        if (!blocked && SourceStartup.ready(this)) { UpdateBlockService.addStatusListener(statusListener); refresh(); }
    }
    @Override protected void onPause() {
        resumed = false; UpdateBlockService.removeStatusListener(statusListener); super.onPause();
    }
    private boolean valid() {
        if (blocked || !SourceStartup.ready(this)) return false;
        try { return UpdateBlockPolicy.safeChoices(true, prefs().getAll()); }
        catch (RuntimeException invalid) { if (status != null) status.setText("Saved update protection settings need review. They were kept."); return false; }
    }
    private SharedPreferences prefs() { return getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE); }
    private void refresh() {
        try {
            boolean wanted = UpdateBlockPolicy.enabled(prefs().getAll());
            updates.setText("Block Amazon system updates: " + (wanted ? "On" : "Off"));
            appstore.setText("Include Amazon Appstore: " + (UpdateBlockPolicy.includesAppstore(prefs().getAll()) ? "On" : "Off"));
            String current = UpdateBlockService.status();
            if (wanted && !UpdateBlockService.active() && "Update protection is off.".equals(current))
                current = "Saved choice is on. Select Retry to review.";
            status.setText(wanted && !UpdateBlockService.active() && !current.startsWith("Saved choice")
                    ? "Saved choice is on. " + current : current);
        } catch (RuntimeException invalid) { status.setText("Saved update protection settings need review. They were kept."); }
    }
    private void requestVpn() {
        if (!valid()) return;
        new AlertDialog.Builder(this).setTitle("Start update protection?")
                .setMessage("This uses the TV's VPN slot and may disconnect another VPN. Review your TV's VPN settings before continuing.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (!valid()) return;
                        try {
                            Intent permission = VpnService.prepare(UpdateBlockActivity.this);
                            if (permission == null) setUpdates(true); else startActivityForResult(permission, VPN_REQUEST);
                        } catch (RuntimeException unavailable) {
                            status.setText("VPN permission could not be checked. Existing settings were kept.");
                        }
                    }
                }).show();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (blocked || !SourceStartup.ready(this) || status == null) return;
        if (request == VPN_REQUEST && valid()) {
            if (result == RESULT_OK) setUpdates(true);
            else status.setText("VPN permission was not granted. Existing settings were kept.");
        }
    }
    private void setUpdates(boolean enabled) {
        if (!valid()) return;
        if (!prefs().edit().putBoolean(UpdateBlockPolicy.UPDATES, enabled).commit()) {
            status.setText("The choice could not be saved. Review the current setting."); return;
        }
        if (!enabled) {
            stopService(serviceIntent());
            refresh(); status.setText("Update protection stopped. VPN lockdown may keep other apps offline. Review TV VPN settings."); return;
        }
        startProtection();
    }
    private void setAppstore(boolean enabled) {
        if (!valid()) return;
        if (!prefs().edit().putBoolean(UpdateBlockPolicy.APPSTORE, enabled).commit()) {
            status.setText("The choice could not be saved. Review the current setting."); return;
        }
        refresh();
    }
    private Intent serviceIntent() {
        return new Intent().setClassName(this, "com.jeremykenedy.firetv.ui.ProtectionService").setAction(UpdateBlockService.MANUAL_START);
    }
    private void startProtection() {
        try {
            if (UpdateBlockService.foregroundDenied(this)) {
                UpdateBlockService.reportPending("The TV did not allow update protection to start. Review app permissions and retry.");
                refresh(); return;
            }
            UpdateBlockService.reportPending("Starting update protection.");
            startForegroundService(serviceIntent()); refresh();
            updates.postDelayed(new Runnable() { @Override public void run() {
                if (!UpdateBlockService.active() && "Starting update protection.".equals(UpdateBlockService.status()))
                    UpdateBlockService.reportPending("Update protection could not be confirmed. Retry from settings.");
            } }, 3000);
        }
        catch (RuntimeException failure) { UpdateBlockService.reportPending("The choice was saved, but update protection could not start. Retry from settings."); refresh(); }
    }
}
