package com.jeremykenedy.firetv.ui.nativeapp.widgetgroups;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction;
import com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState;

/** The original widget colors remain in the portable launcher preference store. */
public final class WidgetAppearance {
    private static final String FILE = "launcher_prefs";
    private static final int[] COLORS = {0xffcccccc,0xffffd600,0xffe53935,0xffe61082,0xffff6d00,0xffffbd00,0xff8bc34a,0xff26a69a,0xff29b6f6,0xff1565c0,
            0xff4052b5,0xff9c27b0,0xff263238,0xff1b5e20,0xff512da8,0xff00cfe5,0xff69e5a8,0xffff4081,0xffff7043,0xffffff00,
            0xff1de9b6,0xff7c4dff,0xff607d8b,0xff37474f,0xffd32f2f,0xffc51162,0xffff9100,0xffffea00,0xff76ff03,0xff00df78,
            0xff03a9f4,0xff2962ff,0xffaa00ff,0xffea00ff,0xff0d47a1,0xff827717,0xff000000};
    private WidgetAppearance() {}
    private static String key(boolean text, String suffix) { return "color_widget_" + (text ? "text" : "bg") + "_color_" + suffix; }
    private static int value(Context context, String key, int fallback) {
        Object value = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getAll().get(key);
        return value instanceof Integer ? (Integer) value : fallback;
    }
    public static boolean hasText(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getAll().get(key(true, "argb")) instanceof Integer;
    }
    public static int color(Context context, boolean text) {
        int color = value(context, key(text, "argb"), text ? Color.WHITE : Color.BLACK);
        int opacity = Math.max(0, Math.min(100, value(context, key(text, "opacity"), text ? 100 : 0)));
        return (color & 0x00ffffff) | (Math.round(opacity * 255f / 100f) << 24);
    }
    public static int groupColor(Context context, boolean text) {
        int color = value(context, key(text, "argb"), text ? Color.BLACK : Color.WHITE);
        int opacity = Math.max(0, Math.min(100, value(context, key(text, "opacity"), text ? 100 : 50)));
        return (color & 0x00ffffff) | (Math.round(opacity * 255f / 100f) << 24);
    }
    public static void apply(View view) {
        Context context = view.getContext();
        view.setBackgroundColor(color(context, false));
        if (hasText(context)) applyText(view, color(context, true));
    }
    public static void applyText(View view, int color) {
        if (view instanceof TextView) ((TextView) view).setTextColor(color);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) applyText(group.getChildAt(i), color);
        }
    }
    private static boolean save(Context context, String argbKey, String opacityKey, int color, int opacity) {
        synchronized (PresentationState.class) {
            try {
                HolidayPresetTransaction.assertWritable(context);
                SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
                Object previousColor = prefs.getAll().get(argbKey);
                Object previousOpacity = prefs.getAll().get(opacityKey);
                if ((previousColor != null && !(previousColor instanceof Integer)) || (previousOpacity != null && !(previousOpacity instanceof Integer)))
                    throw new IllegalStateException("The saved widget color has an unsupported type.");
                if (prefs.edit().putInt(argbKey, color).putInt(opacityKey, opacity).commit()) return true;
                SharedPreferences.Editor rollback = prefs.edit();
                if (previousColor == null) rollback.remove(argbKey); else rollback.putInt(argbKey, (Integer) previousColor);
                if (previousOpacity == null) rollback.remove(opacityKey); else rollback.putInt(opacityKey, (Integer) previousOpacity);
                rollback.commit();
                throw new IllegalStateException("The widget color could not be saved.");
            } catch (IllegalStateException failure) {
                Toast.makeText(context, failure.getMessage(), Toast.LENGTH_LONG).show();
                return false;
            }
        }
    }
    public static void choose(final Activity activity, final boolean text) {
        choose(activity, text ? "Widget Text" : "Widget Background", key(text, "argb"), key(text, "opacity"),
                text ? Color.WHITE : Color.BLACK, text ? 100 : 0, null);
    }
    public interface ColorStore {
        int color();
        int opacity();
        boolean save(int color, int opacity);
    }
    public static void choose(final Activity activity, String titleText, final String argbKey, final String opacityKey,
            final int defaultColor, final int defaultOpacity, final Runnable changed) {
        choose(activity, titleText, new ColorStore() {
            @Override public int color() { return value(activity, argbKey, defaultColor); }
            @Override public int opacity() { return value(activity, opacityKey, defaultOpacity); }
            @Override public boolean save(int color, int opacity) { return WidgetAppearance.save(activity, argbKey, opacityKey, color, opacity); }
        }, changed);
    }
    public static void choose(final Activity activity, String titleText, final ColorStore store, final Runnable changed) {
        final Dialog dialog = new Dialog(activity);
        LinearLayout panel = new LinearLayout(activity); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Pages.dp(activity, 32), Pages.dp(activity, 24), Pages.dp(activity, 32), Pages.dp(activity, 24));
        panel.setBackgroundColor(0xff1c1c1c);
        TextView title = Pages.text(activity, titleText, 26); panel.addView(title);
        final int[] selected = {store.color()};
        final int[] opacity = {Math.max(0, Math.min(100, store.opacity()))};
        final int[] base = {selected[0]};
        GridLayout grid = new GridLayout(activity); grid.setColumnCount(10); panel.addView(grid);
        final SeekBar brightness = new SeekBar(activity); brightness.setMax(200); brightness.setProgress(100);
        final TextView brightLabel = Pages.text(activity, "Brightness Level: 100%", 17);
        final TextView alphaLabel = Pages.text(activity, "Opacity: " + opacity[0] + "%", 17);
        for (final int color : COLORS) {
            View swatch = new View(activity); swatch.setFocusable(true); swatch.setContentDescription(String.format("Color #%06X", color & 0xffffff));
            final GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(Pages.dp(activity, 6)); shape.setStroke(Pages.dp(activity, 1), 0xff888888); swatch.setBackground(shape);
            GridLayout.LayoutParams bounds = new GridLayout.LayoutParams(); bounds.width = Pages.dp(activity, 32); bounds.height = Pages.dp(activity, 32); bounds.setMargins(2, 2, 2, 2); grid.addView(swatch, bounds);
            swatch.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View v, boolean focused) {
                    int brightness = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000;
                    shape.setStroke(Pages.dp(activity, focused ? 3 : 1), focused ? (brightness > 190 ? Color.BLACK : Color.WHITE) : 0xff888888);
                    v.setScaleX(focused ? 1.12f : 1f); v.setScaleY(focused ? 1.12f : 1f);
                }
            });
            swatch.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                base[0] = color; selected[0] = color; brightness.setProgress(100); if (store.save(selected[0], opacity[0]) && changed != null) changed.run();
            } });
        }
        panel.addView(brightLabel); panel.addView(brightness);
        final SeekBar alpha = new SeekBar(activity); alpha.setMax(100); alpha.setProgress(opacity[0]); panel.addView(alphaLabel); panel.addView(alpha);
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                brightLabel.setText("Brightness Level: " + progress + "%");
                if (fromUser) { float[] hsv = new float[3]; Color.colorToHSV(base[0], hsv); hsv[2] = Math.min(1f, hsv[2] * progress / 100f); selected[0] = Color.HSVToColor(hsv); if (store.save(selected[0], opacity[0]) && changed != null) changed.run(); }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {} @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        alpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { alphaLabel.setText("Opacity: " + progress + "%"); if (fromUser) { opacity[0] = progress; if (store.save(selected[0], opacity[0]) && changed != null) changed.run(); } }
            @Override public void onStartTrackingTouch(SeekBar bar) {} @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        dialog.setContentView(panel); dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        dialog.show(); dialog.getWindow().setLayout(Math.min(Pages.dp(activity, 448), activity.getResources().getDisplayMetrics().widthPixels), ViewGroup.LayoutParams.MATCH_PARENT);
    }
}
