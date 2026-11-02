package com.jeremykenedy.firetv.ui.nativeapp.device;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** User-triggered native device screens, without Home ownership or device configuration writes. */
public final class DeviceSettingsActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final DeviceRoutes.Destination[] destinations = {DeviceRoutes.Destination.FACTORY_UI,
            DeviceRoutes.Destination.SYSTEM_SETTINGS, DeviceRoutes.Destination.LIVE_TV};
    private final String[] labels = {"Factory UI", "System settings", "Live TV"};
    private final Button[] actions = new Button[destinations.length];
    private final TextView[] availability = new TextView[destinations.length];
    private TextView status;
    private int selected;
    private int generation;
    private boolean destroyed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) selected = Math.max(0, Math.min(actions.length - 1, state.getInt("selected", 0)));
        LinearLayout page = Pages.page(this);
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
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
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
        state.putInt("selected", selected);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        destroyed = true;
        generation++;
        worker.shutdownNow();
        super.onDestroy();
    }
}
