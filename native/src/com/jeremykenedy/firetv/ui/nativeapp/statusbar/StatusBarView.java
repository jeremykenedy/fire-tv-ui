package com.jeremykenedy.firetv.ui.nativeapp.statusbar;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import com.jeremykenedy.firetv.ui.nativeapp.widget.ClockWidgetView;

/** Compact launcher status controls with independently drawn icons. */
public final class StatusBarView extends LinearLayout {
    private final Activity activity;
    private StatusIcon network;
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
        content.setPadding(0, 0, Pages.dp(activity, 4), 0);
        if (options.tinted) {
            GradientDrawable background = new GradientDrawable();
            boolean lightTheme = "still_light".equals(activity.getSharedPreferences("source_appearance", Context.MODE_PRIVATE).getAll().get("base"));
            background.setColor(lightTheme ? 0x33ffffff : 0x66000000);
            background.setCornerRadius(Pages.dp(activity, 32));
            content.setBackground(background);
        }
        addView(content, new LayoutParams(LayoutParams.WRAP_CONTENT, Pages.dp(activity, 44)));
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
        content.addView(clockView, new LayoutParams(Pages.dp(activity, 64), Pages.dp(activity, 44)));
        if (!options.minimal) {
            network = button("Network", "statusbar:network", StatusIcon.NETWORK, openNetwork);
            button("System settings", "statusbar:device-settings", StatusIcon.SETTINGS, openDeviceSettings);
            button("Launcher preferences", "statusbar:preferences", StatusIcon.PREFERENCES, openPreferences);
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

    private StatusIcon button(String label, String tag, int icon, final Runnable action) {
        StatusIcon button = new StatusIcon(activity, icon);
        button.setContentDescription(label);
        button.setTag(tag);
        button.setFocusable(true);
        button.setClickable(true);
        button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { if (action != null) action.run(); }
        });
        content.addView(button, new LayoutParams(Pages.dp(activity, 44), Pages.dp(activity, 44)));
        return button;
    }

    private void updateNetwork() {
        if (network == null) return;
        String label = "Network unavailable";
        int transport = StatusIcon.NONE;
        try {
            ConnectivityManager manager = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network active = manager == null ? null : manager.getActiveNetwork();
            NetworkCapabilities capabilities = active == null ? null : manager.getNetworkCapabilities(active);
            if (capabilities == null) label = "No network";
            else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) { label = "VPN"; transport = StatusIcon.VPN; }
            else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) { label = "Ethernet"; transport = StatusIcon.ETHERNET; }
            else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) { label = "Wi-Fi"; transport = StatusIcon.WIFI; }
            else label = "Network";
        } catch (RuntimeException unavailable) { }
        network.setTransport(transport);
        network.setContentDescription(label + ". Open network settings.");
    }

    private static final class StatusIcon extends View {
        static final int NETWORK = 0, SETTINGS = 1, PREFERENCES = 2;
        static final int NONE = 0, ETHERNET = 1, WIFI = 2, VPN = 3;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int icon;
        private int transport;

        StatusIcon(Context context, int icon) { super(context); this.icon = icon; }
        void setTransport(int value) { transport = value; invalidate(); }
        @Override public CharSequence getAccessibilityClassName() { return "android.widget.Button"; }
        @Override protected void onFocusChanged(boolean focused, int direction, android.graphics.Rect previous) {
            super.onFocusChanged(focused, direction, previous);
            invalidate();
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int save = canvas.save();
            float size = 20 * getResources().getDisplayMetrics().density;
            canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
            canvas.scale(size / 24f, size / 24f);
            paint.setColor(isFocused() ? 0xfff2efff : 0xff777288);
            paint.setStrokeWidth(2.3f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStyle(Paint.Style.STROKE);
            if (icon == SETTINGS) drawSettings(canvas);
            else if (icon == PREFERENCES) drawPreferences(canvas);
            else drawNetwork(canvas);
            canvas.restoreToCount(save);
        }
        private void drawSettings(Canvas canvas) {
            Path gear = new Path();
            for (int i = 0; i < 32; i++) {
                double angle = Math.PI * i / 16 - Math.PI / 2;
                float radius = (i % 4 == 0 || i % 4 == 1) ? 10.5f : 8f;
                float x = 12 + (float) Math.cos(angle) * radius;
                float y = 12 + (float) Math.sin(angle) * radius;
                if (i == 0) gear.moveTo(x, y); else gear.lineTo(x, y);
            }
            gear.close();
            gear.addCircle(12, 12, 3.8f, Path.Direction.CW);
            gear.setFillType(Path.FillType.EVEN_ODD);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(gear, paint);
        }
        private void drawPreferences(Canvas canvas) {
            canvas.drawLine(3, 6, 21, 6, paint);
            canvas.drawLine(3, 12, 21, 12, paint);
            canvas.drawLine(3, 18, 21, 18, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(8, 6, 2.6f, paint);
            canvas.drawCircle(16, 12, 2.6f, paint);
            canvas.drawCircle(10, 18, 2.6f, paint);
        }
        private void drawNetwork(Canvas canvas) {
            if (transport == WIFI) {
                canvas.drawArc(new RectF(2, 3, 22, 21), 225, 90, false, paint);
                canvas.drawArc(new RectF(6, 8, 18, 20), 225, 90, false, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(12, 19, 1.5f, paint);
            } else if (transport == VPN) {
                canvas.drawRoundRect(new RectF(6, 10, 18, 21), 2, 2, paint);
                canvas.drawArc(new RectF(8, 3, 16, 15), 180, 180, false, paint);
            } else if (transport == ETHERNET) {
                Path cable = new Path();
                cable.moveTo(5, 18); cable.lineTo(5, 7); cable.cubicTo(5, 2, 12, 2, 12, 7);
                cable.lineTo(12, 17); cable.cubicTo(12, 22, 19, 22, 19, 17); cable.lineTo(19, 6);
                canvas.drawPath(cable, paint);
                canvas.drawLine(3, 18, 7, 18, paint); canvas.drawLine(17, 6, 21, 6, paint);
            } else {
                canvas.drawCircle(12, 12, 8, paint);
                canvas.drawLine(6, 18, 18, 6, paint);
            }
        }
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
