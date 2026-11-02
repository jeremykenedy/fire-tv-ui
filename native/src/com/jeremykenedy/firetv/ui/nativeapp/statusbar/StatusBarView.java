package com.jeremykenedy.firetv.ui.nativeapp.statusbar;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import com.jeremykenedy.firetv.ui.nativeapp.widget.ClockWidgetView;

/** A launcher status row shown only when an explicit retained choice enables it. */
public final class StatusBarView extends LinearLayout {
    private final Activity activity;
    private Button network;
    private View clock;
    private final LinearLayout content;
    private boolean receiverRegistered;
    private final boolean hideUnlessFocused;
    private final ViewTreeObserver.OnGlobalFocusChangeListener focusChanged = new ViewTreeObserver.OnGlobalFocusChangeListener() {
        @Override public void onGlobalFocusChanged(View oldFocus, View newFocus) {
            if (hideUnlessFocused && !hasFocus()) conceal();
        }
    };
    private final BroadcastReceiver networkChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { updateNetwork(); }
    };

    public StatusBarView(Activity activity, StatusBarOptions options, Runnable openNetwork,
            Runnable openDeviceSettings, Runnable openPreferences, final Runnable openQuickPanel) {
        super(activity);
        if (!options.configured) throw new IllegalArgumentException("Status bar choice is unavailable.");
        this.activity = activity;
        this.hideUnlessFocused = options.hideUnlessFocused;
        setOrientation(HORIZONTAL);
        setGravity(("Left".equals(options.position) ? Gravity.START
                : "Right".equals(options.position) ? Gravity.END : 0) | Gravity.CENTER_VERTICAL);
        setTag("statusbar");
        content = new LinearLayout(activity);
        content.setOrientation(HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        if (options.tinted) {
            GradientDrawable background = new GradientDrawable();
            background.setColor(0x99000000);
            background.setCornerRadius(Pages.dp(activity, 32));
            content.setBackground(background);
        }
        addView(content, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        ClockWidgetView clockView = new ClockWidgetView(activity);
        clock = clockView;
        clockView.setCompact();
        clockView.setTag("statusbar:clock");
        if (options.minimal) {
            clockView.setFocusable(true);
            clockView.setClickable(true);
            clockView.setContentDescription("Clock. Open quick panel.");
            clockView.setOnFocusChangeListener(new OnFocusChangeListener() {
                @Override public void onFocusChange(View view, boolean focused) {
                    if (focused) view.setContentDescription(clockView.timeText() + ". Open quick panel.");
                }
            });
            clockView.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View view) { if (openQuickPanel != null) openQuickPanel.run(); }
            });
        }
        content.addView(clockView, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        if (!options.minimal) {
            network = button("Network", "statusbar:network", openNetwork);
            button("Settings", "statusbar:device-settings", openDeviceSettings);
            button("Preferences", "statusbar:preferences", openPreferences);
        }
        if (hideUnlessFocused) {
            setFocusable(true);
            setOnFocusChangeListener(new OnFocusChangeListener() {
                @Override public void onFocusChange(View view, boolean focused) {
                    if (focused) reveal();
                }
            });
            conceal();
        }
        updateNetwork();
    }

    private void conceal() {
        setAlpha(0f);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
    }

    private void reveal() {
        setAlpha(1f);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        if (network != null) network.requestFocus();
        else if (clock != null) clock.requestFocus();
    }

    private Button button(String label, String tag, final Runnable action) {
        Button button = Pages.button(activity, label, new View.OnClickListener() {
            @Override public void onClick(View view) { if (action != null) action.run(); }
        });
        button.setTag(tag);
        content.addView(button, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        return button;
    }

    private void updateNetwork() {
        if (network == null) return;
        String label = "Network unavailable";
        try {
            ConnectivityManager manager = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network active = manager == null ? null : manager.getActiveNetwork();
            NetworkCapabilities capabilities = active == null ? null : manager.getNetworkCapabilities(active);
            if (capabilities == null) label = "No network";
            else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) label = "VPN";
            else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) label = "Ethernet";
            else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) label = "Wi-Fi";
            else label = "Network";
        } catch (RuntimeException unavailable) { }
        network.setText(label);
        network.setContentDescription(label + ". Open network settings.");
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (hideUnlessFocused) getViewTreeObserver().addOnGlobalFocusChangeListener(focusChanged);
        if (network == null || receiverRegistered) return;
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        if (Build.VERSION.SDK_INT >= 33) activity.registerReceiver(networkChanged, filter, Context.RECEIVER_NOT_EXPORTED);
        else activity.registerReceiver(networkChanged, filter);
        receiverRegistered = true;
        updateNetwork();
    }
    @Override protected void onDetachedFromWindow() {
        if (hideUnlessFocused && getViewTreeObserver().isAlive())
            getViewTreeObserver().removeOnGlobalFocusChangeListener(focusChanged);
        if (receiverRegistered) {
            activity.unregisterReceiver(networkChanged);
            receiverRegistered = false;
        }
        super.onDetachedFromWindow();
    }
}
