package com.jeremykenedy.firetv.ui.nativeapp.device;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.MenuActivity;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import com.jeremykenedy.firetv.ui.nativeapp.SourceStartup;
import com.jeremykenedy.firetv.ui.nativeapp.device.remotebuttons.RemoteButtonsActivity;
import java.util.Map;

/** Remote and boot choices; opening the screen never changes system settings. */
public final class RemoteControlsActivity extends MenuActivity {
    private boolean startupBlocked;
    private Button service, back, launcher, boot, enhanced;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        LinearLayout panel = Pages.sidePanel(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setClipChildren(false); scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false); rows.setClipToPadding(false);
        rows.setPadding(0, Pages.dp(this, 32), 0, Pages.dp(this, 12));
        scroll.addView(rows); panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView heading = Pages.text(this, "Remote & Boot Controls", 22);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setPadding(0, 0, 0, Pages.dp(this, 20));
        rows.addView(heading);
        service = Pages.menuButton(this, "Accessibility Service", new View.OnClickListener() {
            @Override public void onClick(View view) { openAccessibility(); }
        });
        LinearLayout.LayoutParams serviceBounds = (LinearLayout.LayoutParams) service.getLayoutParams();
        serviceBounds.height = Pages.dp(this, 76);
        serviceBounds.bottomMargin = Pages.dp(this, 12);
        service.setTextSize(18);
        rows.addView(service);
        TextView info = Pages.text(this,
                "Enable the Fire TV UI accessibility service to allow Home interception and startup. The service must be enabled in Android settings.", 15);
        info.setTextColor(0xffaaaaaa);
        info.setPadding(Pages.dp(this, 4), Pages.dp(this, 8), Pages.dp(this, 4), Pages.dp(this, 32));
        rows.addView(info);
        back = option(rows, "Override back button exit", new View.OnClickListener() {
            @Override public void onClick(View view) { toggleBack(); }
        });
        launcher = option(rows, "Override Current Launcher", new View.OnClickListener() {
            @Override public void onClick(View view) { change("override_home_button"); }
        });
        boot = option(rows, "Start on boot", new View.OnClickListener() {
            @Override public void onClick(View view) { change("start_on_boot"); }
        });
        enhanced = option(rows, "Enhanced home detection", new View.OnClickListener() {
            @Override public void onClick(View view) { change("better_home_detection"); }
        });
        option(rows, "Branded remote buttons", new View.OnClickListener() {
            @Override public void onClick(View view) {
                startActivity(new Intent(RemoteControlsActivity.this, RemoteButtonsActivity.class));
            }
        });
        update();
        service.requestFocus();
    }

    private Button option(LinearLayout rows, String label, View.OnClickListener click) {
        Button button = Pages.menuButton(this, label, click);
        ((LinearLayout.LayoutParams) button.getLayoutParams()).bottomMargin = Pages.dp(this, 16);
        rows.addView(button);
        return button;
    }

    @Override protected void onResume() {
        super.onResume();
        if (startupBlocked) return;
        if (!SourceStartup.ready(this)) {
            startupBlocked = SourceStartup.redirectIfBlocked(this);
            return;
        }
        if (service != null) update();
    }

    private boolean production() { return HomeRoutingPolicy.PRODUCTION_PACKAGE.equals(getPackageName()); }

    private boolean[] serviceState() {
        boolean enabled = false, keys = false;
        AccessibilityManager manager = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager != null && manager.isEnabled()) {
            ComponentName expected = new ComponentName(getPackageName(), getPackageName() + ".Hra");
            for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                if (expected.equals(ComponentName.unflattenFromString(info.getId()))) {
                    enabled = true;
                    keys = (info.getCapabilities() & AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS) != 0
                            && (info.flags & AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0;
                }
            }
        }
        return new boolean[]{enabled, keys};
    }

    private void update() {
        boolean[] serviceStatus = serviceState();
        boolean available = production() && serviceStatus[0] && serviceStatus[1];
        Map<String, ?> values = getSharedPreferences("launcher_prefs", MODE_PRIVATE).getAll();
        HomeSetupPolicy choices = HomeSetupPolicy.read(values);
        service.setText("Accessibility Service   " + (serviceStatus[0] ? "ON" : "OFF")
                + "\n" + (production() ? (serviceStatus[0] ? "Enabled" : "Disabled - select to open Accessibility Settings")
                : "Preview: production service unavailable"));
        Object backValue = values.get("override_back_button");
        boolean backValid = !values.containsKey("override_back_button") || backValue instanceof Boolean;
        setOption(back, "Override back button exit", backValid, !Boolean.FALSE.equals(backValue));
        setOption(launcher, "Override Current Launcher", available, choices.keys);
        setOption(boot, "Start on boot", available, Boolean.TRUE.equals(values.get("start_on_boot")));
        setOption(enhanced, "Enhanced home detection", available, choices.windows);
    }

    private void setOption(Button button, String label, boolean available, boolean checked) {
        button.setText(label + (available ? (checked ? "   ✓" : "   ○") : "   -"));
        button.setEnabled(available);
        button.setAlpha(available ? 1f : 0.45f);
        button.setContentDescription(label + ", " + (available ? (checked ? "on" : "off") : "unavailable"));
    }

    private void openAccessibility() {
        if (!production()) { message("Accessibility setup is unavailable in the preview."); return; }
        Intent intent = HomeSetup.accessibilitySettings(this);
        if (intent == null) { message("Accessibility settings are unavailable on this device."); return; }
        try { startActivity(intent); }
        catch (RuntimeException unavailable) { message("Accessibility settings are unavailable on this device."); }
    }

    private void toggleBack() {
        Map<String, ?> values = getSharedPreferences("launcher_prefs", MODE_PRIVATE).getAll();
        Object old = values.get("override_back_button");
        if (values.containsKey("override_back_button") && !(old instanceof Boolean)) {
            message("The Back setting is invalid and was not changed.");
            return;
        }
        if (!getSharedPreferences("launcher_prefs", MODE_PRIVATE).edit()
                .putBoolean("override_back_button", Boolean.FALSE.equals(old)).commit()) {
            message("The Back setting could not be saved.");
        }
        update();
    }

    private void change(String key) {
        if (!production()) return;
        boolean[] state = serviceState();
        if (!state[0] || !state[1]) { update(); return; }
        try {
            if (!HomeCommandHandler.recover(this)) throw new IllegalStateException();
            Map<String, ?> values = getSharedPreferences("launcher_prefs", MODE_PRIVATE).getAll();
            HomeSetupPolicy choices = HomeSetupPolicy.read(values);
            for (String name : new String[]{HomeSetupPolicy.LEGACY_HOME, HomeSetupPolicy.HOME_KEYS,
                    HomeSetupPolicy.HOME_WINDOWS, "override_home_button", "better_home_detection", "start_on_boot"}) {
                if (values.containsKey(name) && !(values.get(name) instanceof Boolean)) throw new IllegalStateException();
            }
            if ("start_on_boot".equals(key)) {
                Object old = values.get(key);
                if (values.containsKey(key) && !(old instanceof Boolean)) throw new IllegalStateException();
                if (!getSharedPreferences("launcher_prefs", MODE_PRIVATE).edit()
                        .putBoolean(key, !Boolean.TRUE.equals(old)).commit()) throw new IllegalStateException();
            } else {
                boolean keys = "override_home_button".equals(key) ? !choices.keys : choices.keys;
                boolean windows = "better_home_detection".equals(key) ? !choices.windows : choices.windows;
                if (!HomeSetup.saveChoices(this, keys, windows)) throw new IllegalStateException();
            }
            update();
        } catch (RuntimeException failed) {
            message("Home settings could not be updated. Review or recover them first.");
            update();
        }
    }

    private void message(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
}
