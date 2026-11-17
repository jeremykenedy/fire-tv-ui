package com.jeremykenedy.firetv.ui.nativeapp;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.accessibility.AccessibilityStyle;

public final class Pages {
    private Pages() {}

    public static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    public static LinearLayout page(Activity activity) { return page(activity, false); }

    public static LinearLayout sidePanel(Activity activity) { return page(activity, true); }

    private static LinearLayout page(Activity activity, boolean sidePanel) {
        activity.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        if (sidePanel) {
            body.setClipChildren(false); body.setClipToPadding(false);
            android.widget.FrameLayout root = new android.widget.FrameLayout(activity);
            android.graphics.drawable.GradientDrawable panel = new android.graphics.drawable.GradientDrawable();
            panel.setColor(0xff383838);
            panel.setCornerRadius(dp(activity, 18));
            body.setBackground(panel);
            body.setPadding(dp(activity, 32), dp(activity, 24), dp(activity, 32), dp(activity, 16));
            int width = Math.min(dp(activity, 448), activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 32));
            android.widget.FrameLayout.LayoutParams bounds = new android.widget.FrameLayout.LayoutParams(width, -1, android.view.Gravity.END);
            bounds.setMargins(dp(activity, 16), dp(activity, 16), dp(activity, 16), dp(activity, 16));
            root.addView(body, bounds);
            activity.setContentView(root);
        } else {
            body.setBackgroundColor(0xff101523);
            int pad = dp(activity, 32);
            body.setPadding(pad, pad, pad, pad);
            activity.setContentView(body);
        }
        body.getViewTreeObserver().addOnWindowFocusChangeListener(new ViewTreeObserver.OnWindowFocusChangeListener() {
            @Override public void onWindowFocusChanged(boolean hasFocus) {
                if (hasFocus) AccessibilityStyle.apply(activity, body);
            }
        });
        return body;
    }

    public static TextView text(Activity activity, String value, int size) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(Color.WHITE);
        text.setPadding(0, dp(activity, 6), 0, dp(activity, 12));
        AccessibilityStyle.apply(activity, text);
        return text;
    }

    public static Button menuButton(Activity activity, String label, View.OnClickListener click) {
        Button button = new Button(activity);
        button.setText(label); button.setAllCaps(false); button.setOnClickListener(click);
        button.setTextSize(20);
        button.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        button.setPadding(dp(activity, 10), 0, dp(activity, 10), 0);
        button.setMinHeight(dp(activity, 44)); button.setMinimumHeight(dp(activity, 44));
        button.setStateListAnimator(null);
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        for (boolean focused : new boolean[]{true, false}) {
            android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
            background.setColor(focused ? 0xffffffff : 0xff484848);
            background.setCornerRadius(dp(activity, 8));
            states.addState(focused ? new int[]{android.R.attr.state_focused} : new int[]{}, background);
        }
        button.setBackground(states);
        button.setTextColor(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_focused}, new int[]{}}, new int[]{0xff000000, 0xffd3d3d3}));
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean focused) {
                ((Button) view).setTypeface(null, focused ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                view.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f).setDuration(100).start();
            }
        });
        LinearLayout.LayoutParams bounds = new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT);
        bounds.bottomMargin = dp(activity, 6);
        button.setLayoutParams(bounds);
        AccessibilityStyle.apply(activity, button);
        return button;
    }

    public static Button button(Activity activity, String label, View.OnClickListener click) {
        Button button = new Button(activity);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(click);
        AccessibilityStyle.apply(activity, button);
        return button;
    }
}
