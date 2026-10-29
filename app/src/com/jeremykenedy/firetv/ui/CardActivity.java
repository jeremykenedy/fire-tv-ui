package com.jeremykenedy.firetv.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared TV card, focus treatment, and error presentation. */
public abstract class CardActivity extends Activity {
    protected LinearLayout card;
    private MenuBackgroundView menuBackground;
    protected static final int INK = Color.WHITE;
    protected static final int MUTED = Color.rgb(207, 207, 207);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root = new FrameLayout(this);
        menuBackground = new MenuBackgroundView(this, false);
        root.addView(menuBackground, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout page = new FrameLayout(this);
        page.setPadding(dp(32), dp(24), dp(32), dp(24));
        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(24), dp(20), dp(24), dp(20));
        page.addView(card, new FrameLayout.LayoutParams(-1, -1));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        refreshMenuBackground();
        MenuMotion.apply(this, true);
    }

    @Override public void finish() {
        super.finish();
        MenuMotion.apply(this, false);
    }

    @Override protected void onResume() {
        super.onResume();
        menuBackground.menuResumed(true);
        refreshMenuBackground();
    }

    @Override protected void onPause() {
        menuBackground.menuResumed(false);
        super.onPause();
    }

    protected void refreshMenuBackground() {
        menuBackground.refresh();
        card.setBackground(background(MenuBackgroundView.enabled(this)
                ? Color.argb(220, 28, 31, 42) : Color.rgb(59, 59, 59), 16));
    }

    protected void title(String value) {
        TextView heading = text(value, 28, INK);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(0, 0, 0, dp(8));
        card.addView(heading);
    }

    protected LinearLayout scrollBody() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        card.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        return body;
    }

    protected TextView button(String label, View.OnClickListener action) {
        final TextView button = text(label, 17, INK);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(14), dp(8), dp(14), dp(8));
        button.setMinHeight(dp(42));
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setClickable(true);
        button.setBackground(background(Color.argb(28, 255, 255, 255), 8));
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean focused) {
                button.setTextColor(focused ? Color.rgb(35, 35, 35) : INK);
                button.setBackground(background(focused ? Color.WHITE
                        : Color.argb(28, 255, 255, 255), 8));
            }
        });
        button.setOnClickListener(action);
        return button;
    }

    protected void addButton(LinearLayout body, TextView button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(6);
        body.addView(button, params);
    }

    protected TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    protected GradientDrawable background(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    protected int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    protected void error(Exception error) {
        new AlertDialog.Builder(this).setTitle("Could not complete the change")
                .setMessage(error.getMessage()).setPositiveButton("OK", null).show();
    }
}
