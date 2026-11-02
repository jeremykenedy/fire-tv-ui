package com.jeremykenedy.firetv.ui.nativeapp.device.screensavers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ScreensaverSettingsActivity extends Activity {
    private boolean startupBlocked;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout rows;
    private ScrollView scroll;
    private TextView active;
    private TextView editedLabel;
    private TextView status;
    private TextView timer;
    private String focusKey = "screensaver:system";
    private String edited = "";
    private int scrollY;
    private int generation;
    private boolean destroyed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        if (state != null) {
            focusKey = state.getString("focus", focusKey);
            edited = state.getString("edited", "");
            scrollY = state.getInt("scroll", 0);
        }
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "Screensavers", 30));
        scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        active = Pages.text(this, "Reading active screensaver...", 18);
        active.setTag("screensaver:active");
        content.addView(active);
        editedLabel = Pages.text(this, "Editing: none", 16);
        editedLabel.setTag("screensaver:editing");
        content.addView(editedLabel);
        content.addView(action("Install screensavers", "screensaver:catalog", new Runnable() {
            @Override public void run() { startActivity(new Intent(ScreensaverSettingsActivity.this, ScreensaverCatalogActivity.class)); }
        }));
        content.addView(action("Memory and storage", "screensaver:memory-route", new Runnable() {
            @Override public void run() { startActivity(new Intent(ScreensaverSettingsActivity.this, ScreensaverMemoryActivity.class)); }
        }));
        content.addView(action("TV screensaver settings", "screensaver:system", new Runnable() {
            @Override public void run() { openSystem(); }
        }));
        content.addView(action("Change screensaver On or Off", "screensaver:enabled", new Runnable() {
            @Override public void run() {
                if (!ScreensaverSystem.canSelect(ScreensaverSettingsActivity.this)) {
                    status.setText("Change screensaver On or Off in the TV's own settings.");
                    openSystem();
                    return;
                }
                try {
                    ScreensaverSystem.enabled(ScreensaverSettingsActivity.this, !ScreensaverSystem.enabled(ScreensaverSettingsActivity.this));
                    refresh();
                } catch (RuntimeException failed) { status.setText(failed.getMessage()); }
            }
        }));
        timer = Pages.text(this, "", 16);
        timer.setTag("screensaver:idle");
        content.addView(timer);
        content.addView(action("Change shared idle delay", "screensaver:timer", new Runnable() {
            @Override public void run() { chooseDelay(); }
        }));
        content.addView(Pages.text(this, "Configure and Preview keep the active selection. Only Set active changes it. The idle delay is shared with the TV system.", 16));
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        content.addView(rows);
        status = Pages.text(this, "", 16);
        status.setTag("screensaver:status");
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        page.addView(status);
    }

    @Override protected void onResume() {
        if (startupBlocked) { super.onResume(); return; } super.onResume(); refresh(); }

    private void refresh() {
        final int request = ++generation;
        worker.execute(new Runnable() {
            @Override public void run() {
                try {
                    final List<ScreensaverEntry> entries = ScreensaverSystem.installed(getApplicationContext());
                    final String selected = ScreensaverSystem.active(getApplicationContext());
                    final boolean enabled = ScreensaverSystem.enabled(getApplicationContext());
                    final int delay = ScreensaverSystem.idleDelay(getApplicationContext());
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (destroyed || generation != request) return;
                            String label = selected.isEmpty() ? "None selected" : selected;
                            for (ScreensaverEntry entry : entries) if (entry.component.equals(selected)) label = entry.label;
                            active.setText("Active screensaver: " + label + (enabled ? " (On)" : " (Off)"));
                            editedLabel.setText("Editing: " + (edited.isEmpty() ? "none" : edited));
                            timer.setText("Shared idle delay: " + (delay > 0 ? (delay / 1000) + " seconds" : "System default or disabled")
                                    + ". Used by system idle handling.");
                            rows.removeAllViews();
                            for (ScreensaverEntry entry : entries) row(entry, entry.component.equals(selected));
                            if (entries.isEmpty()) rows.addView(Pages.text(ScreensaverSettingsActivity.this,
                                    "No accessible installed screensavers were found. Install a screensaver using its provider, then return here.", 18));
                            scroll.post(new Runnable() {
                                @Override public void run() {
                                    if (destroyed || request != generation) return;
                                    View target = getWindow().getDecorView().findViewWithTag(focusKey);
                                    if (target == null) target = getWindow().getDecorView().findViewWithTag("screensaver:system");
                                    target.requestFocus();
                                    scroll.scrollTo(0, scrollY);
                                }
                            });
                        }
                    });
                } catch (final RuntimeException unavailable) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (!destroyed && generation == request) status.setText("Installed screensavers could not be read. Try the TV screensaver settings.");
                        }
                    });
                }
            }
        });
    }

    private void row(final ScreensaverEntry entry, boolean selected) {
        rows.addView(Pages.text(this, entry.label + (selected ? " (Active)" : ""), 20));
        final Intent configuration = ScreensaverSystem.configuration(this, entry.component);
        final Intent preview = ScreensaverSystem.preview(this, entry.component);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(action("Configure", "configure:" + entry.component, new Runnable() {
            @Override public void run() { edit(entry, configuration, "This saver has no accessible configuration screen."); }
        }), new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(action(preview == null ? "Preview unavailable" : ScreensaverSystem.directPreview(entry.component, preview)
                ? "Preview" : "Preview controls", "preview:" + entry.component, new Runnable() {
            @Override public void run() { edit(entry, preview, "This saver has no accessible preview. Use its configuration or TV screensaver settings."); }
        }), new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(action(selected ? "Active" : "Set active", "select:" + entry.component, new Runnable() {
            @Override public void run() {
                if (!ScreensaverSystem.canSelect(ScreensaverSettingsActivity.this)) {
                    status.setText("Direct selection is unavailable. Select " + entry.label + " in TV screensaver settings.");
                    openSystem();
                    return;
                }
                try {
                    ScreensaverSystem.setActive(ScreensaverSettingsActivity.this, entry.component);
                    status.setText("Active screensaver set to " + entry.label + ".");
                    refresh();
                } catch (RuntimeException failed) { status.setText(failed.getMessage()); }
            }
        }), new LinearLayout.LayoutParams(0, -2, 1));
        rows.addView(actions);
    }

    private void edit(ScreensaverEntry entry, Intent intent, String unavailable) {
        edited = entry.label;
        editedLabel.setText("Editing: " + edited + ". Active selection unchanged.");
        if (intent == null) { status.setText(unavailable); return; }
        try {
            startActivity(intent);
        } catch (RuntimeException failed) { status.setText("This saver could not open its controls. It may have been removed or restricted."); }
    }

    private void openSystem() {
        Intent intent = ScreensaverSystem.systemSettings(this);
        if (intent == null) { status.setText("TV screensaver settings are unavailable on this device. Use the TV's own settings menu."); return; }
        try { startActivity(intent); }
        catch (RuntimeException failed) { status.setText("TV screensaver settings could not open. Use the TV's own settings menu."); }
    }

    private void chooseDelay() {
        if (!Settings.System.canWrite(this)) {
            status.setText("Allow system settings access to change the shared idle delay, then return here. The current delay is unchanged.");
            Intent setup = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            try { startActivity(setup); }
            catch (RuntimeException unavailable) { status.setText("System settings access is unavailable. Change the delay in the TV's own settings."); }
            return;
        }
        final int[] values = {60000, 120000, 300000, 600000, 900000, 1800000};
        String[] labels = {"1 minute", "2 minutes", "5 minutes", "10 minutes", "15 minutes", "30 minutes"};
        new AlertDialog.Builder(this).setTitle("Shared system idle delay").setItems(labels,
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        try {
                            ScreensaverSystem.idleDelay(ScreensaverSettingsActivity.this, values[which]);
                            status.setText("Shared system idle delay saved.");
                            refresh();
                        } catch (RuntimeException failed) { status.setText(failed.getMessage()); }
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private Button action(String label, final String key, final Runnable task) {
        Button button = Pages.button(this, label, new View.OnClickListener() {
            @Override public void onClick(View view) { focusKey = key; task.run(); }
        });
        button.setTag(key);
        return button;
    }

    private void remember() {
        View focused = getCurrentFocus();
        if (focused != null && focused.getTag() instanceof String) focusKey = (String) focused.getTag();
        scrollY = scroll.getScrollY();
    }

    @Override protected void onPause() {
        if (startupBlocked) { super.onPause(); return; } remember(); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (startupBlocked) { super.onSaveInstanceState(state); return; }
        remember();
        state.putString("focus", focusKey);
        state.putString("edited", edited);
        state.putInt("scroll", scrollY);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        if (startupBlocked) { super.onDestroy(); return; } destroyed = true; generation++; worker.shutdownNow(); super.onDestroy(); }
}
