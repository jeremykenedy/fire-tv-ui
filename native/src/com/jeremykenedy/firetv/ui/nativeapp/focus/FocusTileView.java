package com.jeremykenedy.firetv.ui.nativeapp.focus;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A stable focus target with growth reserved inside its measured bounds. */
public final class FocusTileView extends FrameLayout {
    private final LinearLayout surface;
    private final ImageView icon;
    private final TextView title;
    private final GradientDrawable background = new GradientDrawable();
    private FocusOptions options;
    private ValueAnimator animation;
    private float progress;
    private boolean preview;

    public FocusTileView(Context context) {
        super(context);
        options = FocusPreferences.load(context);
        setFocusable(true);
        setClickable(true);
        setClipChildren(false);
        setClipToPadding(false);
        surface = new LinearLayout(context);
        surface.setOrientation(LinearLayout.VERTICAL);
        surface.setGravity(Gravity.CENTER);
        surface.setPadding(dp(12), dp(12), dp(12), dp(10));
        background.setCornerRadius(dp(14));
        surface.setBackground(background);
        addView(surface, new FrameLayout.LayoutParams(-1, -1));
        icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        surface.addView(icon, new LinearLayout.LayoutParams(-1, 0, 1));
        title = new TextView(context);
        title.setTextSize(17);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, dp(8), 0, 0);
        surface.addView(title, new LinearLayout.LayoutParams(-1, -2));
        updateVisual();
    }

    public void setTitle(String value) { setText(value); }

    public void setText(CharSequence value) {
        title.setText(value);
        setContentDescription(value);
    }

    public void setIcon(Drawable value) { icon.setImageDrawable(value); }

    public void setPreviewFocused(boolean value) {
        preview = value;
        animateFocus();
    }

    public void refreshPreferences() {
        options = FocusPreferences.load(getContext());
        cancelAnimation();
        progress = isFocused() || preview ? 1f : 0f;
        updateVisual();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        int horizontal = FocusOptions.growthInset(getMeasuredWidth());
        int vertical = FocusOptions.growthInset(getMeasuredHeight());
        if (getPaddingLeft() != horizontal || getPaddingTop() != vertical) {
            setPadding(horizontal, vertical, horizontal, vertical);
            super.onMeasure(widthSpec, heightSpec);
        }
    }

    @Override protected void onFocusChanged(boolean focused, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        if (surface != null) animateFocus();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refreshPreferences();
    }

    @Override protected void onDetachedFromWindow() {
        cancelAnimation();
        super.onDetachedFromWindow();
    }

    private void animateFocus() {
        options = FocusPreferences.load(getContext());
        cancelAnimation();
        float target = isFocused() || preview ? 1f : 0f;
        if (options.durationMillis == 0 || !ValueAnimator.areAnimatorsEnabled()) {
            progress = target;
            updateVisual();
            return;
        }
        animation = ValueAnimator.ofFloat(progress, target);
        animation.setDuration(options.durationMillis);
        animation.setInterpolator(new DecelerateInterpolator());
        animation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator value) {
                progress = (Float) value.getAnimatedValue();
                updateVisual();
            }
        });
        animation.start();
    }

    private void cancelAnimation() {
        if (animation != null) {
            animation.cancel();
            animation.removeAllUpdateListeners();
            animation = null;
        }
    }

    private void updateVisual() {
        float scale = options.scale(progress);
        surface.setScaleX(scale);
        surface.setScaleY(scale);
        int color = options.borderArgb();
        int alpha = options.borderEnabled ? Math.round(Color.alpha(color) * progress) : 0;
        background.setStroke(dp(3), (alpha << 24) | options.borderRgb);
        background.setColor(Color.rgb(Math.round(37 + progress * 23),
                Math.round(31 + progress * 19), Math.round(57 + progress * 24)));
        title.setTextColor(progress > 0.5f ? Color.WHITE : 0xffcec7df);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
