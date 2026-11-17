package com.jeremykenedy.firetv.ui.nativeapp.focus;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

/** Keep scrolling rows inside their viewport while the focused artwork can grow. */
public final class AppFocusViewport implements View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
    private final ViewGroup grid;
    private final View viewport;
    private final int[] viewportOrigin = new int[2], childOrigin = new int[2];
    private final Rect desired = new Rect(), previous = new Rect();
    private ViewTreeObserver observer;

    private AppFocusViewport(ViewGroup grid, View viewport) {
        this.grid = grid;
        this.viewport = viewport;
    }

    public static void attach(ViewGroup grid, View viewport) {
        AppFocusViewport clipping = new AppFocusViewport(grid, viewport);
        grid.addOnAttachStateChangeListener(clipping);
        if (grid.isAttachedToWindow()) clipping.onViewAttachedToWindow(grid);
    }

    @Override public void onViewAttachedToWindow(View view) {
        observer = grid.getViewTreeObserver();
        observer.addOnPreDrawListener(this);
    }

    @Override public void onViewDetachedFromWindow(View view) {
        if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
        observer = null;
    }

    @Override public boolean onPreDraw() {
        viewport.getLocationOnScreen(viewportOrigin);
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            boolean clipped = child.getClipBounds(previous);
            if (child.hasFocus()) {
                if (clipped) child.setClipBounds(null);
                continue;
            }
            child.getLocationOnScreen(childOrigin);
            int left = viewportOrigin[0] - childOrigin[0], top = viewportOrigin[1] - childOrigin[1];
            desired.set(left, top, left + viewport.getWidth(), top + viewport.getHeight());
            if (!clipped || !previous.equals(desired)) child.setClipBounds(desired);
        }
        return true;
    }
}
