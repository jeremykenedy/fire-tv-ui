package com.jeremykenedy.firetv.ui.nativeapp.focus;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
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
    private boolean compact;
    private int legacyArtworkWidth;
    private boolean legacyPresentation;
    private boolean darkLegacySurface;

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

    public void setIcon(Drawable value) {
        icon.setImageDrawable(value);
        if (legacyPresentation) {
            updateLegacyContrast(value);
            updateVisual();
        }
    }

    private void updateLegacyContrast(Drawable value) {
        darkLegacySurface = false;
        if (value == null) return;
        Bitmap sample = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888);
        Rect old = value.copyBounds();
        try {
            int width = Math.max(1, value.getIntrinsicWidth());
            int height = Math.max(1, value.getIntrinsicHeight());
            float fit = Math.min(64f / width, 36f / height);
            int w = Math.max(1, Math.round(width * fit)), h = Math.max(1, Math.round(height * fit));
            value.setBounds((64 - w) / 2, (36 - h) / 2, (64 - w) / 2 + w, (36 - h) / 2 + h);
            value.draw(new Canvas(sample));
            int[] pixels = new int[64 * 36];
            sample.getPixels(pixels, 0, 64, 0, 0, 64, 36);
            darkLegacySurface = FocusOptions.needsDarkLegacySurface(pixels);
        } catch (RuntimeException unavailable) {
            darkLegacySurface = true;
        } finally {
            value.setBounds(old);
            sample.recycle();
        }
    }

    public void setArtworkFit(boolean fit) {
        icon.setScaleType(fit ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
    }

    public void setCompact(boolean compact) {
        if (this.compact == compact) return;
        this.compact = compact;
        if (!legacyPresentation) configureNativeSurface();
    }

    private void configureNativeSurface() {
        surface.setOrientation(compact ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        surface.setPadding(dp(compact ? 4 : 12), dp(compact ? 4 : 12),
                dp(compact ? 4 : 12), dp(compact ? 4 : 10));
        icon.setLayoutParams(new LinearLayout.LayoutParams(compact ? 0 : -1, compact ? -1 : 0, 1));
        title.setLayoutParams(compact ? new LinearLayout.LayoutParams(0, -2, 2)
                : new LinearLayout.LayoutParams(-1, -2));
        title.setPadding(dp(compact ? 4 : 0), dp(compact ? 0 : 8), 0, 0);
    }

    /** Opt in only for an app tile with a measured legacy artwork width. */
    public void useLegacyDefaultAppPresentation(int artworkWidth) {
        if (artworkWidth <= 0) throw new IllegalArgumentException("Artwork width must be positive.");
        legacyArtworkWidth = artworkWidth;
        if (legacyPresentation) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) surface.getLayoutParams();
            params.width = artworkWidth;
            params.height = FocusOptions.legacyAppArtworkHeight(artworkWidth);
            params.topMargin = FocusOptions.legacyAppTopInset(params.height);
            surface.setLayoutParams(params);
        }
        updatePresentation();
        updateVisual();
    }

    private void updatePresentation() {
        boolean legacy = legacyArtworkWidth > 0 && FocusPreferences.legacyDefaultAppMode(getContext());
        if (legacy == legacyPresentation) return;
        legacyPresentation = legacy;
        if (legacy) updateLegacyContrast(icon.getDrawable());
        background.setCornerRadius(dp(legacy ? 16 : 14));
        if (legacy) {
            surface.setOrientation(LinearLayout.VERTICAL);
            surface.setPadding(0, 0, 0, 0);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(legacyArtworkWidth,
                    FocusOptions.legacyAppArtworkHeight(legacyArtworkWidth), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            params.topMargin = FocusOptions.legacyAppTopInset(params.height);
            surface.setLayoutParams(params);
            icon.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
            title.setVisibility(View.GONE);
        } else {
            surface.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
            title.setVisibility(View.VISIBLE);
            configureNativeSurface();
        }
        requestLayout();
    }

    public void setPreviewFocused(boolean value) {
        preview = value;
        animateFocus();
    }

    public void refreshPreferences() {
        options = FocusPreferences.load(getContext());
        updatePresentation();
        cancelAnimation();
        progress = isFocused() || preview ? 1f : 0f;
        updateVisual();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        int horizontal = legacyPresentation ? 0 : FocusOptions.growthInset(getMeasuredWidth());
        int vertical = legacyPresentation ? 0 : FocusOptions.growthInset(getMeasuredHeight());
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
        updatePresentation();
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
        float scale = legacyPresentation ? 1f + (FocusOptions.LEGACY_APP_SCALE - 1f) * progress : options.scale(progress);
        surface.setScaleX(scale);
        surface.setScaleY(scale);
        if (legacyPresentation) {
            background.setStroke(0, Color.TRANSPARENT);
            background.setColor((Math.round(255f * progress) << 24) | (darkLegacySurface ? 0x251f39 : 0xffffff));
            return;
        }
        int color = options.borderArgb();
        int alpha = options.borderEnabled ? Math.round(Color.alpha(color) * progress) : 0;
        background.setStroke(dp(3), (alpha << 24) | options.borderRgb);
        background.setColor(options.surfaceRgb(progress));
        title.setTextColor(progress > 0.5f ? Color.WHITE : 0xffcec7df);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
