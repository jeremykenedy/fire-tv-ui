package com.jeremykenedy.firetv.ui.nativeapp.accessibility;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import java.util.WeakHashMap;

public final class AccessibilityStyle {
    private static final WeakHashMap<View, Original> originals = new WeakHashMap<>();
    private AccessibilityStyle() {}

    public static void apply(Activity activity, View root) {
        apply(activity, root, AccessibilityPreferences.textPercent(activity), AccessibilityPreferences.highContrast(activity));
    }

    private static void apply(Activity activity, View view, int percent, boolean contrast) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            Original original = originals.get(view);
            if (original == null) { original = new Original(text); originals.put(view, original); }
            text.setTextSize(TypedValue.COMPLEX_UNIT_PX, original.size * percent / 100f);
            if (view instanceof Button) {
                text.setTextColor(contrast ? buttonText() : original.colors);
                view.setBackground(contrast ? buttonBackground(activity) : original.background);
            } else text.setTextColor(contrast ? ColorStateList.valueOf(Color.WHITE) : original.colors);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) apply(activity, group.getChildAt(i), percent, contrast);
        }
    }

    private static ColorStateList buttonText() {
        return new ColorStateList(new int[][] {new int[] {-android.R.attr.state_enabled},
                new int[] {android.R.attr.state_focused}, new int[] {android.R.attr.state_pressed}, new int[] {}},
                new int[] {0xffaaaaaa, Color.BLACK, Color.BLACK, Color.WHITE});
    }

    private static Drawable buttonBackground(Activity activity) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused}, surface(activity, Color.WHITE, Color.WHITE));
        states.addState(new int[] {android.R.attr.state_pressed}, surface(activity, Color.WHITE, Color.WHITE));
        states.addState(new int[] {}, surface(activity, Color.BLACK, 0xffaaaaaa));
        return states;
    }

    private static Drawable surface(Activity activity, int color, int border) {
        float density = activity.getResources().getDisplayMetrics().density;
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(6 * density);
        shape.setStroke(Math.max(1, Math.round(2 * density)), border);
        return shape;
    }

    private static final class Original {
        final float size;
        final ColorStateList colors;
        final Drawable background;
        Original(TextView text) { size = text.getTextSize(); colors = text.getTextColors(); background = text.getBackground(); }
    }
}
