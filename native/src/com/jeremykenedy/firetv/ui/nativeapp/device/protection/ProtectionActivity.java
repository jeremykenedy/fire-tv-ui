package com.jeremykenedy.firetv.ui.nativeapp.device.protection;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import android.text.InputType;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import com.jeremykenedy.firetv.ui.nativeapp.SourceStartup;
import com.jeremykenedy.firetv.ui.nativeapp.device.DeviceRoutes;

public final class ProtectionActivity extends com.jeremykenedy.firetv.ui.nativeapp.MenuActivity {
    private boolean startupBlocked;
    private Button awake;
    private TextView status;
    private TextView details;
    private Button sleep;
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "Sleep and protection", 28));
        awake = Pages.button(this, "", new View.OnClickListener() {
            @Override public void onClick(View view) { chooseAwake(); }
        });
        awake.setTag("protection:awake");
        page.addView(awake);
        page.addView(Pages.text(this, "Opt in to keep the screen awake only while Fire TV UI Home is visible. This can prevent the system screensaver on Home. Off follows the current system behavior. Leaving Home releases the request.", 16));
        sleep = Pages.button(this, "Change system sleep timeout", new View.OnClickListener() {
            @Override public void onClick(View view) { chooseSleep(); }
        });
        sleep.setTag("protection:sleep");
        page.addView(sleep);
        Button settings = Pages.button(this, "Open TV system settings", new View.OnClickListener() {
            @Override public void onClick(View view) {
                DeviceRoutes.Result result = DeviceRoutes.open(ProtectionActivity.this, DeviceRoutes.Destination.SYSTEM_SETTINGS);
                status.setText(result == DeviceRoutes.Result.STARTED ? "Find sleep, display or power options in your TV's settings."
                        : "TV settings unavailable to apps. Open the TV's factory settings with your remote.");
            }
        });
        settings.setTag("protection:settings");
        page.addView(settings);
        if ("com.jeremykenedy.firetv.ui".equals(getPackageName())) {
            Button updates = Pages.button(this, "Amazon update protection", new View.OnClickListener() {
                @Override public void onClick(View view) {
                    startActivity(new Intent().setClassName(ProtectionActivity.this,
                            "com.jeremykenedy.firetv.ui.nativeapp.device.updates.UpdateBlockActivity"));
                }
            });
            updates.setTag("protection:update-blocking");
            page.addView(updates);
        }
        Button refresh = Pages.button(this, "Refresh system status", new View.OnClickListener() {
            @Override public void onClick(View view) {
                final boolean retrying = SourceStartup.protectionError(ProtectionActivity.this) != null;
                SourceStartup.startProtection(ProtectionActivity.this, new Runnable() {
                    @Override public void run() {
                        if (isFinishing() || isDestroyed()) return;
                        if (retrying && SourceStartup.protectionError(ProtectionActivity.this) == null)
                            status.setText("Saved device protection start requested.");
                        refresh();
                    }
                });
            }
        });
        refresh.setTag("protection:refresh");
        page.addView(refresh);
        status = Pages.text(this, "System sleep and power settings remain under TV control.", 16);
        status.setTag("protection:status");
        page.addView(status);
        ScrollView scroll = new ScrollView(this);
        details = Pages.text(this, "", 17);
        details.setTag("protection:details");
        scroll.addView(details);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        awake.requestFocus();
    }
    @Override protected void onResume() {
        if (startupBlocked) { super.onResume(); return; }
        super.onResume();
        refresh();
        SourceStartup.startProtection(this, new Runnable() {
            @Override public void run() { if (!isFinishing() && !isDestroyed()) refresh(); }
        });
    }
    private void refresh() {
        String protectionError = SourceStartup.protectionError(this);
        if (protectionError != null) status.setText(protectionError);
        awake.setText("Keep Home screen awake: " + (ProtectionPreferences.keepLauncherAwake(this) ? "On" : "Off"));
        details.setText(SleepStatus.read(this) + (SystemSleepTimer.available(this) ? "\n\nSystem sleep timeout editing is available with the TV's existing permission."
                : "\n\nDirect sleep timeout editing is unavailable. It requires an existing system setting and a permission already granted by the TV."));
    }
    private void chooseSleep() {
        if (!SystemSleepTimer.available(this)) {
            status.setText("Sleep timeout editing is unavailable. Use TV system settings. No permission or setting changed.");
            return;
        }
        final EditText minutes = new EditText(this);
        minutes.setInputType(InputType.TYPE_CLASS_NUMBER);
        minutes.setHint("Minutes (1 to 35791)");
        new AlertDialog.Builder(this).setTitle("Change system sleep timeout?")
                .setMessage("This changes the TV's system sleep timeout, independently of screensaver delay. No automatic restoration or protection is enabled. TV firmware determines the effect.")
                .setView(minutes).setNegativeButton("Cancel", null)
                .setPositiveButton("Save timeout", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        try {
                            SystemSleepTimer.Result result = SystemSleepTimer.setMinutes(ProtectionActivity.this, minutes.getText().toString());
                            status.setText(result == SystemSleepTimer.Result.SAVED ? "Sleep timeout saved and read back."
                                    : result == SystemSleepTimer.Result.UNAVAILABLE ? "Sleep timeout is unavailable. No change applied."
                                    : result == SystemSleepTimer.Result.FAILED_RESTORED ? "Change failed. Previous timeout read back."
                                    : "The previous timeout could not be confirmed. Inspect TV system settings before retrying.");
                            refresh();
                        } catch (IllegalArgumentException invalid) { status.setText(invalid.getMessage()); }
                    }
                }).show();
    }
    private void chooseAwake() {
        final boolean enable = !ProtectionPreferences.keepLauncherAwake(this);
        if (!enable) { save(false); return; }
        new AlertDialog.Builder(this).setTitle("Keep Home awake?")
                .setMessage("The system screensaver may not start while Home is visible. Other apps and TV sleep settings keep their own behavior.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Enable on Home", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { save(true); }
                }).show();
    }
    private void save(boolean enabled) {
        boolean saved = ProtectionPreferences.keepLauncherAwake(this, enabled);
        status.setText(saved ? "Saved. Applies when you return to Home." : "The option could not be saved. Try again.");
        refresh();
    }
}
