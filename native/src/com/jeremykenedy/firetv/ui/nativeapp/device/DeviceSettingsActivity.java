package com.jeremykenedy.firetv.ui.nativeapp.device;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** User-triggered native device screens, without Home ownership or device configuration writes. */
public final class DeviceSettingsActivity extends com.jeremykenedy.firetv.ui.nativeapp.MenuActivity {
    private boolean startupBlocked;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final DeviceRoutes.Destination[] destinations = {DeviceRoutes.Destination.FACTORY_UI,
            DeviceRoutes.Destination.SYSTEM_SETTINGS, DeviceRoutes.Destination.LIVE_TV};
    private final String[] labels = {"Factory UI", "System settings", "Live TV"};
    private final Button[] actions = new Button[destinations.length];
    private final TextView[] availability = new TextView[destinations.length];
    private TextView status;
    private TextView homeStatus;
    private Button homeKeys;
    private Button homeWindows;
    private int selected;
    private int generation;
    private boolean destroyed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        if (state != null) selected = Math.max(0, Math.min(actions.length - 1, state.getInt("selected", 0)));
        LinearLayout page = Pages.page(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        setContentView(scroll);
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        page.addView(Pages.text(this, "Device", 30));
        page.addView(Pages.text(this, "Open your TV's built-in screens. Availability depends on your device.", 18));
        for (int i = 0; i < destinations.length; i++) {
            final int index = i;
            Button action = Pages.button(this, labels[i], new View.OnClickListener() {
                @Override public void onClick(View view) { open(index); }
            });
            action.setId(0x3900 + i);
            action.setTag("device:" + destinations[i].name());
            action.setEnabled(false);
            action.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View view, boolean focused) { if (focused) selected = index; }
            });
            actions[i] = action;
            page.addView(action, new LinearLayout.LayoutParams(-1, -2));
            availability[i] = Pages.text(this, "Checking availability…", 16);
            availability[i].setTag("device:availability:" + destinations[i].name());
            page.addView(availability[i]);
        }
        status = Pages.text(this, "Choose a device screen.", 16);
        status.setTag("device:status");
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        page.addView(status);
        page.addView(Pages.text(this, "Home controls", 24));
        homeStatus = Pages.text(this, "", 16);
        homeStatus.setTag("device:home-status");
        homeStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        page.addView(homeStatus);
        if (HomeRoutingPolicy.PRODUCTION_PACKAGE.equals(getPackageName())) {
            homeKeys = Pages.button(this, "Home key routing", new View.OnClickListener() {
                @Override public void onClick(View view) { changeHome(true); }
            });
            homeKeys.setId(0x3903);
            homeKeys.setTag("device:home-keys");
            page.addView(homeKeys);
            homeWindows = Pages.button(this, "Home window fallback", new View.OnClickListener() {
                @Override public void onClick(View view) { changeHome(false); }
            });
            homeWindows.setId(0x3904);
            homeWindows.setTag("device:home-windows");
            page.addView(homeWindows);
            Button setup = Pages.button(this, "Open accessibility settings", new View.OnClickListener() {
                @Override public void onClick(View view) {
                    DeviceRoutes.Result result = DeviceRoutes.openSystemIntent(DeviceSettingsActivity.this,
                            HomeSetup.accessibilitySettings(DeviceSettingsActivity.this));
                    if (result != DeviceRoutes.Result.STARTED) status.setText("Accessibility settings are unavailable on this device.");
                }
            });
            setup.setId(0x3905);
            setup.setTag("device:home-accessibility");
            page.addView(setup);
            page.addView(Pages.text(this, "Choose your Home modes, then enable Fire TV UI Home controls in your TV's accessibility settings. Window fallback waits for the built-in Home screen. These choices do not enable a system service.", 16));
        }
    }

    @Override protected void onResume() {
        if (startupBlocked) { super.onResume(); return; }
        super.onResume();
        refresh();
        refreshHome();
    }

    private void refresh() {
        final int request = ++generation;
        worker.execute(new Runnable() {
            @Override public void run() {
                final boolean[] available = new boolean[destinations.length];
                for (int i = 0; i < destinations.length; i++) {
                    available[i] = DeviceRoutes.resolve(getApplicationContext(), destinations[i]) != null;
                }
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (destroyed || request != generation) return;
                        for (int i = 0; i < destinations.length; i++) {
                            actions[i].setEnabled(true);
                            availability[i].setText(available[i] ? "Available" : "Unavailable on this device");
                        }
                        if (!(getCurrentFocus() instanceof Button)) actions[selected].requestFocus();
                    }
                });
            }
        });
    }

    private void changeHome(boolean keys) {
        HomeSetup setup = HomeSetup.read(this);
        boolean saved = HomeSetup.saveChoices(this, keys ? !setup.choices.keys : setup.choices.keys,
                keys ? setup.choices.windows : !setup.choices.windows);
        refreshHome();
        status.setText(saved ? "Home choices saved. System accessibility selection stays under your control."
                : "Could not save Home choices. Existing settings were preserved; review your saved configuration.");
    }

    private void refreshHome() {
        HomeSetup setup = HomeSetup.read(this);
        String message;
        switch (setup.state) {
            case PREVIEW: message = "Home routing is unavailable in Source Preview. Your TV's selected Home stays in control."; break;
            case OFF: message = "Home routing is off."; break;
            case REVIEW_LEGACY: message = "Review your Home choices before enabling routing."; break;
            case NEEDS_SERVICE: message = "Enable Fire TV UI Home controls in accessibility settings to use these choices."; break;
            case NEEDS_KEY_CAPABILITY: message = "The TV has not granted Home key filtering. Keep Factory UI available and review accessibility settings."; break;
            case RECOVERY_REQUIRED: message = "Interrupted Home settings need recovery before routing can run. Existing choices remain protected."; break;
            default: message = "Home controls are configured. Test Home on your remote to confirm this TV delivers it.";
        }
        homeStatus.setText(message);
        if (homeKeys != null) {
            homeKeys.setText("Home key routing: " + (setup.choices.keys ? "On" : "Off"));
            homeWindows.setText("Home window fallback: " + (setup.choices.windows ? "On" : "Off"));
        }
    }

    private void open(int index) {
        selected = index;
        DeviceRoutes.Result result = DeviceRoutes.open(this, destinations[index]);
        if (result == DeviceRoutes.Result.UNAVAILABLE) {
            availability[index].setText("Unavailable on this device");
            status.setText(labels[index] + " is unavailable on this device.");
        } else if (result == DeviceRoutes.Result.FAILED) {
            status.setText("Could not open " + labels[index] + ". Try again from your TV's own menu.");
        } else {
            status.setText("Opening " + labels[index] + "…");
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (startupBlocked) { super.onSaveInstanceState(state); return; }
        state.putInt("selected", selected);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        if (startupBlocked) { super.onDestroy(); return; }
        destroyed = true;
        generation++;
        worker.shutdownNow();
        super.onDestroy();
    }
}
