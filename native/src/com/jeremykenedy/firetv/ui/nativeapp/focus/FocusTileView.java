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

/** Stable focus targets with independent app artwork motion inside the viewport. */
public final class FocusTileView extends FrameLayout {
    private final LinearLayout surface;
    private final ImageView icon;
    private final TextView title;
    private final GradientDrawable background = new GradientDrawable();
    private FocusOptions options;
    private ValueAnimator animation;
    private float progress;
    private boolean preview;
    private boolean transparentSurface;
    private boolean compact;
    private boolean miniApp;
    private int legacyArtworkWidth;
    private boolean legacyPresentation;
    private boolean darkLegacySurface;
    private boolean appFocus, appBorder, fixedAppArtwork;
    private int appGrowth, appShadowSize;
    private int appBackgroundColor;
    private float appBackgroundAlpha;
    private Integer titleColor;
    private boolean titleVisible = true;
    private final android.view.ViewTreeObserver.OnPreDrawListener focusBounds =
            new android.view.ViewTreeObserver.OnPreDrawListener() {
                @Override public boolean onPreDraw() {
                    if (appFocus && !preview && progress > 0) positionAppSurface();
                    return true;
                }
            };

    public FocusTileView(Context context) {
        super(context);
        options = com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.ready(context)
                ? FocusPreferences.load(context) : FocusOptions.defaults();
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

    public void setTitleVisible(boolean visible) {
        titleVisible = visible;
        title.setVisibility(visible && !legacyPresentation && !miniApp ? View.VISIBLE : View.GONE);
    }

    public void setTitleColor(Integer color) {
        titleColor = color;
        updateVisual();
    }

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
        if (!legacyPresentation && !miniApp && !fixedAppArtwork) configureNativeSurface();
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

    /** Opt in only for an app tile with a measured artwork width. */
    public void useLegacyDefaultAppPresentation(int artworkWidth) {
        if (artworkWidth <= 0) throw new IllegalArgumentException("Artwork width must be positive.");
        appFocus = true;
        legacyArtworkWidth = artworkWidth;
        updatePresentation();
        layoutAppSurface();
        updateVisual();
    }

    public void useAppArtworkPresentation(int artworkWidth) {
        fixedAppArtwork = true;
        useLegacyDefaultAppPresentation(artworkWidth);
    }

    public void useAppFocusPresentation() {
        appFocus = true;
        updateAppVisualOptions();
        requestLayout();
        updateVisual();
    }

    /** Home supplies square app artwork for its compact favorites dock. */
    public void setMiniAppPresentation(boolean enabled) {
        if (miniApp == enabled) return;
        miniApp = enabled;
        if (legacyArtworkWidth > 0) {
            layoutAppSurface();
            updateVisual();
        }
    }

    private void updatePresentation() {
        boolean legacy = legacyArtworkWidth > 0 && FocusPreferences.legacyDefaultAppMode(getContext());
        if (legacy == legacyPresentation) return;
        legacyPresentation = legacy;
        if (legacy) updateLegacyContrast(icon.getDrawable());
        layoutAppSurface();
    }

    private void layoutAppSurface() {
        boolean artworkOnly = legacyPresentation || fixedAppArtwork || (miniApp && legacyArtworkWidth > 0);
        surface.setClipToOutline(artworkOnly);
        background.setCornerRadius(dp(legacyPresentation ? 16 : 14));
        updateAppVisualOptions();
        if (artworkOnly) {
            surface.setOrientation(LinearLayout.VERTICAL);
            surface.setPadding(0, 0, 0, 0);
            int height = miniApp ? legacyArtworkWidth : FocusOptions.legacyAppArtworkHeight(legacyArtworkWidth);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(legacyArtworkWidth,
                    height, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            params.topMargin = FocusOptions.legacyAppTopInset(height);
            surface.setLayoutParams(params);
            icon.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
            title.setVisibility(View.GONE);
        } else {
            surface.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
            title.setVisibility(titleVisible ? View.VISIBLE : View.GONE);
            configureNativeSurface();
        }
        requestLayout();
    }

    public void useTransparentSurface() {
        transparentSurface = true;
        updateVisual();
    }

    public void setPreviewFocused(boolean value) {
        preview = value;
        animateFocus();
    }

    public void refreshPreferences() {
        if (!com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.ready(getContext())) { cancelAnimation(); return; }
        options = FocusPreferences.load(getContext());
        updatePresentation();
        updateAppVisualOptions();
        cancelAnimation();
        progress = isFocused() && hasWindowFocus() || preview ? 1f : 0f;
        updateVisual();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        int horizontal = legacyPresentation || fixedAppArtwork ? 0 : FocusOptions.growthInset(getMeasuredWidth());
        int vertical = legacyPresentation || fixedAppArtwork ? 0 : FocusOptions.growthInset(getMeasuredHeight());
        if (getPaddingLeft() != horizontal || getPaddingTop() != vertical) {
            setPadding(horizontal, vertical, horizontal, vertical);
            super.onMeasure(widthSpec, heightSpec);
        }
    }

    @Override protected void onFocusChanged(boolean focused, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        if (surface != null) animateFocus();
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (surface != null) animateFocus();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(focusBounds);
        refreshPreferences();
    }

    @Override protected void onDetachedFromWindow() {
        if (getViewTreeObserver().isAlive()) getViewTreeObserver().removeOnPreDrawListener(focusBounds);
        cancelAnimation();
        super.onDetachedFromWindow();
    }

    private void animateFocus() {
        if (!com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.ready(getContext())) { cancelAnimation(); return; }
        options = FocusPreferences.load(getContext());
        updatePresentation();
        updateAppVisualOptions();
        cancelAnimation();
        float target = isFocused() && hasWindowFocus() || preview ? 1f : 0f;
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

    private void updateAppVisualOptions() {
        if (!appFocus) return;
        try { appGrowth = FocusPreferences.appGrowth(getContext()); }
        catch (IllegalStateException unavailable) { appGrowth = 18; }
        try { appShadowSize = FocusPreferences.appShadowSize(getContext()); }
        catch (IllegalStateException unavailable) { appShadowSize = 0; }
        if (legacyArtworkWidth <= 0) return;
        try { background.setCornerRadius(dp(FocusPreferences.iconRadius(getContext()))); }
        catch (IllegalStateException unavailable) { background.setCornerRadius(dp(16)); }
        appBorder = FocusPreferences.appBorder(getContext());
        appBackgroundColor = FocusPreferences.iconBackgroundColor(getContext());
        appBackgroundAlpha = FocusPreferences.iconBackgroundOpacity(getContext()) / 100f
                * Color.alpha(appBackgroundColor) / 255f;
        surface.setClipToOutline(true);
    }

    private void updateVisual() {
        float scale = appFocus ? 1f + appGrowth * progress / 100f : options.scale(progress);
        Rect viewport = new Rect();
        if (appFocus && !preview && surface.getWidth() > 0 && surface.getHeight() > 0) {
            getWindowVisibleDisplayFrame(viewport);
            float shadow = dp(appShadowSize) * progress;
            if (viewport.width() > 0 && viewport.height() > 0)
                scale = Math.min(scale, Math.max(1f,Math.min((viewport.width() - 2 * shadow) / surface.getWidth(),
                        (viewport.height() - 2 * shadow) / surface.getHeight())));
        }
        surface.setScaleX(scale);
        surface.setScaleY(scale);
        if (!appFocus || preview || progress == 0) { surface.setTranslationX(0); surface.setTranslationY(0); }
        if (appFocus) {
            float shadow = dp(appShadowSize) * progress;
            surface.setElevation(shadow);
            if (!preview && progress > 0) positionAppSurface();
        }
        if (transparentSurface) {
            background.setColor(Color.TRANSPARENT);
            background.setStroke(dp(3), (Math.round(255f * progress) << 24) | 0x00ffffff);
            return;
        }
        if (legacyPresentation) {
            int alpha = appBorder ? Math.round(Color.alpha(options.borderArgb()) * progress) : 0;
            background.setStroke(dp(3), (alpha << 24) | options.borderRgb);
            int focused = darkLegacySurface ? 0x251f39 : 0xffffff;
            float surfaceAlpha = progress + appBackgroundAlpha * (1f - progress);
            int color = surfaceAlpha == 0 ? focused : mix(appBackgroundColor, focused, progress / surfaceAlpha);
            background.setColor((Math.round(255f * surfaceAlpha) << 24) | color);
            return;
        }
        int color = options.borderArgb();
        int alpha = options.borderEnabled ? Math.round(Color.alpha(color) * progress) : 0;
        background.setStroke(dp(3), (alpha << 24) | options.borderRgb);
        int surfaceColor = options.surfaceRgb(progress);
        if (legacyArtworkWidth > 0 && appBackgroundAlpha > 0)
            surfaceColor = 0xff000000 | mix(surfaceColor, appBackgroundColor, appBackgroundAlpha * (1f - progress));
        background.setColor(surfaceColor);
        title.setTextColor(titleColor != null ? titleColor : (progress > 0.5f ? Color.WHITE : 0xffcec7df));
    }

    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
        super.onLayout(changed,left,top,right,bottom);
        updateVisual();
    }

    private void positionAppSurface() {
        Rect viewport = new Rect(); getWindowVisibleDisplayFrame(viewport);
        if (viewport.width() <= 0 || viewport.height() <= 0 || surface.getWidth() <= 0) return;
        int[] location = new int[2]; surface.getLocationOnScreen(location);
        float shadow = surface.getElevation();
        float x = edgeOffset(location[0] - surface.getTranslationX(),surface.getWidth() * surface.getScaleX(),viewport.left + shadow,viewport.right - shadow);
        float y = edgeOffset(location[1] - surface.getTranslationY(),surface.getHeight() * surface.getScaleY(),viewport.top + shadow,viewport.bottom - shadow);
        if (Math.abs(x - surface.getTranslationX()) >= 1f) surface.setTranslationX(x);
        if (Math.abs(y - surface.getTranslationY()) >= 1f) surface.setTranslationY(y);
    }

    private static float edgeOffset(float start,float size,float min,float max) {
        return start < min ? min - start : start + size > max ? max - start - size : 0;
    }

    private static int mix(int from, int to, float fraction) {
        float amount = Math.max(0f, Math.min(1f, fraction));
        int rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int start = (from >> shift) & 255, end = (to >> shift) & 255;
            rgb |= Math.round(start + (end - start) * amount) << shift;
        }
        return rgb;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
