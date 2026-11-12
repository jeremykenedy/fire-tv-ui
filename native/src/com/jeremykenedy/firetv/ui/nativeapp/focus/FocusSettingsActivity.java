package com.jeremykenedy.firetv.ui.nativeapp.focus;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.Path;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.PathShape;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.HomeVisualOptions;
import com.jeremykenedy.firetv.ui.nativeapp.icons.IconPackStore;
import com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetAppearance;

public final class FocusSettingsActivity extends com.jeremykenedy.firetv.ui.nativeapp.MenuActivity {
    private boolean startupBlocked;
    private boolean advanced;
    private FocusOptions options;
    private FocusTileView preview;
    private TextView summary;
    private TextView importedNotice;
    private RadioGroup growthChoices;
    private SeekBar growthSlider;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        showIconsPanel();
    }

    private void showAdvanced() {
        advanced = true;
        options = FocusPreferences.load(this);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        LinearLayout root = Pages.sidePanel(this);
        root.addView(text("Focus and motion", 30, Color.WHITE));
        TextView intro = text("Choose how apps respond as you move with the remote.", 17, 0xffc8bfdc);
        intro.setPadding(0, dp(6), 0, dp(18));
        root.addView(intro);
        importedNotice = text("Apps use the standard focus style. Changing a control applies your custom style.", 16, 0xffe5dff0);
        importedNotice.setPadding(0, 0, 0, dp(12));
        importedNotice.setVisibility(FocusPreferences.legacyDefaultAppMode(this) ? View.VISIBLE : View.GONE);
        root.addView(importedNotice);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        root.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
        ScrollView scroll = new ScrollView(this);
        row.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout controls = column();
        controls.setPadding(0, 0, dp(28), dp(16));
        scroll.addView(controls);
        LinearLayout sample = column();
        sample.setGravity(Gravity.CENTER);
        controls.addView(sample, new LinearLayout.LayoutParams(-1, -2));
        sample.addView(text("Focused appearance", 20, Color.WHITE));
        preview = new FocusTileView(this);
        preview.setTitle("Preview tile");
        Path play = new Path();
        play.moveTo(28, 12);
        play.lineTo(104, 64);
        play.lineTo(28, 116);
        play.close();
        ShapeDrawable artwork = new ShapeDrawable(new PathShape(play, 128, 128));
        artwork.getPaint().setColor(Color.WHITE);
        artwork.getPaint().setAntiAlias(true);
        artwork.setIntrinsicWidth(128);
        artwork.setIntrinsicHeight(128);
        preview.setIcon(artwork);
        preview.useLegacyDefaultAppPresentation(dp(230));
        preview.setPreviewFocused(true);
        sample.addView(preview, new LinearLayout.LayoutParams(-1, dp(120)));
        summary = text("", 16, 0xffc8bfdc);
        summary.setGravity(Gravity.CENTER);
        sample.addView(summary);
        controls.addView(text("Transition speed", 20, Color.WHITE));
        choices(controls, new String[]{"Instant", "Fast", "Normal", "Slow"},
                new int[]{0, 100, 180, 280}, options.durationMillis, new Choice() {
                    @Override public void selected(int value) {
                        saveMotion("duration_millis", value);
                    }
                });
        section(controls, "Focus growth");
        growthChoices = choices(controls, new String[]{"None", "3%", "6%", "10%"},
                new int[]{0, 3, 6, 10}, options.growthPercent, new Choice() {
                    @Override public void selected(int value) {
                        saveMotion("growth_percent", value);
                    }
                });
        growthSlider = slider(controls, "Custom growth", options.growthPercent, FocusOptions.MAX_GROWTH, "%", new Choice() {
            @Override public void selected(int value) {
                saveMotion("growth_percent", value);
            }
        });
        section(controls, "Focus border");
        Switch enabled = new Switch(this);
        enabled.setText("Show a border");
        enabled.setTextColor(Color.WHITE);
        enabled.setTextSize(17);
        enabled.setMinHeight(dp(48));
        enabled.setChecked(options.borderEnabled);
        enabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                save(new FocusOptions(options.durationMillis, options.growthPercent, checked,
                        options.borderRgb, options.borderOpacity, options.fillEnabled, options.fillRgb, options.fillOpacity));
            }
        });
        controls.addView(enabled);
        color(controls, "Border red", 16, false);
        color(controls, "Border green", 8, false);
        color(controls, "Border blue", 0, false);
        slider(controls, "Border opacity", options.borderOpacity, 100, "%", new Choice() {
            @Override public void selected(int value) {
                save(new FocusOptions(options.durationMillis, options.growthPercent,
                        options.borderEnabled, options.borderRgb, value, options.fillEnabled, options.fillRgb, options.fillOpacity));
            }
        });
        section(controls, "Focus fill");
        Switch fill = new Switch(this);
        fill.setText("Show a focus fill");
        fill.setTextColor(Color.WHITE);
        fill.setTextSize(17);
        fill.setMinHeight(dp(48));
        fill.setChecked(options.fillEnabled);
        fill.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                save(new FocusOptions(options.durationMillis, options.growthPercent, options.borderEnabled,
                        options.borderRgb, options.borderOpacity, checked, options.fillRgb, options.fillOpacity));
            }
        });
        controls.addView(fill);
        color(controls, "Fill red", 16, true);
        color(controls, "Fill green", 8, true);
        color(controls, "Fill blue", 0, true);
        slider(controls, "Fill opacity", options.fillOpacity, 100, "%", new Choice() {
            @Override public void selected(int value) {
                save(new FocusOptions(options.durationMillis, options.growthPercent, options.borderEnabled,
                        options.borderRgb, options.borderOpacity, options.fillEnabled, options.fillRgb, value));
            }
        });
        TextView note = text("Turn off focus fill for a border-only highlight. Fill stays behind artwork; each layer has its own color and opacity.", 16, 0xffc8bfdc);
        note.setPadding(0, dp(12), 0, 0);
        controls.addView(note);
        updateSummary();
    }

    private interface ToggleSetting {
        boolean get();
        void set(boolean value);
    }

    private void showIconsPanel() {
        advanced = false;
        LinearLayout page = Pages.sidePanel(this);
        TextView heading = new TextView(this);
        heading.setText("Icons"); heading.setTextSize(26); heading.setTextColor(0xff8d8d8d);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setPadding(0, dp(4), 0, dp(26)); page.addView(heading);
        ScrollView scroll = new ScrollView(this);
        scroll.setClipChildren(false); scroll.setClipToPadding(false); scroll.setVerticalScrollBarEnabled(false);
        scroll.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View view, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                view.setClipBounds(new android.graphics.Rect(-page.getPaddingLeft(), 0,
                        view.getWidth() + page.getPaddingRight(), view.getHeight()));
            }
        });
        final LinearLayout controls = column(); controls.setClipChildren(false); controls.setClipToPadding(false);
        controls.setPadding(0, dp(4), 0, dp(8)); scroll.addView(controls);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        Button first = toggleRow(controls, "Mini favorites mode", new ToggleSetting() {
            public boolean get() { return HomeVisualOptions.isMini(FocusSettingsActivity.this); }
            public void set(boolean value) { HomeVisualOptions.setMini(FocusSettingsActivity.this, value); }
        });
        toggleRow(controls, "Enable icon focus border", new ToggleSetting() {
            public boolean get() { return FocusPreferences.appBorder(FocusSettingsActivity.this); }
            public void set(boolean value) { FocusPreferences.setAppBorder(FocusSettingsActivity.this, value); }
        });
        appSize(controls,false);
        appSize(controls,true);
        controls.addView(Pages.text(this,"Large tiles fit the available space.",16));
        toggleRow(controls, "Wallpaper blur on scroll", new ToggleSetting() {
            public boolean get() { return HomeVisualOptions.isBlurOnScroll(FocusSettingsActivity.this); }
            public void set(boolean value) { HomeVisualOptions.setBlurOnScroll(FocusSettingsActivity.this, value); }
        });
        final Button radius = Pages.menuButton(this, "", null);
        radius.setTag("icons:radius");
        final Runnable updateRadius = new Runnable() {
            @Override public void run() {
                try { radius.setText("Icon radius scale: " + FocusPreferences.iconRadius(FocusSettingsActivity.this)); }
                catch (IllegalStateException unavailable) { radius.setText("Icon radius scale: Unavailable"); }
            }
        };
        radius.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) {
                try {
                    int current = FocusPreferences.iconRadius(FocusSettingsActivity.this);
                    FocusPreferences.saveIconOption(FocusSettingsActivity.this, "icon_radius", current >= 20 ? 0 : Math.min(20, current + 2));
                    updateRadius.run();
                } catch (RuntimeException failed) { Toast.makeText(FocusSettingsActivity.this, "Icon radius could not be saved.", Toast.LENGTH_LONG).show(); }
            }
        });
        controls.addView(radius); updateRadius.run();
        FocusStyleControls.addOriginalColorControl(this, controls, "Icon Focus Background Color", true);
        FocusStyleControls.addOriginalColorControl(this, controls, "Icon Focus Border Color", false);
        Button background = Pages.menuButton(this, "Icon Background Color", new View.OnClickListener() {
            @Override public void onClick(View ignored) {
                WidgetAppearance.choose(FocusSettingsActivity.this, "Icon Background", "color_back_color_argb",
                        "color_back_color_opacity", Color.WHITE, 0, null);
            }
        });
        background.setTag("icons:background"); controls.addView(background);
        Button packs = Pages.menuButton(this, "Icon Pack", new View.OnClickListener() {
            @Override public void onClick(View ignored) { IconPackStore.showPicker(FocusSettingsActivity.this, null); }
        });
        controls.addView(packs);
        Button extra = Pages.menuButton(this, "Advanced focus", new View.OnClickListener() {
            @Override public void onClick(View ignored) { showAdvanced(); }
        });
        controls.addView(extra); first.requestFocus();
    }

    private Button toggleRow(LinearLayout controls, final String label, final ToggleSetting setting) {
        final Button button = Pages.menuButton(this, label, null);
        button.setTag("icons:" + label);
        final Runnable update = new Runnable() {
            @Override public void run() {
                try {
                    boolean checked = setting.get();
                    button.setCompoundDrawablesWithIntrinsicBounds(null, null, checked ? new CheckMark() : null, null);
                    button.setContentDescription(label + (checked ? ": On" : ": Off"));
                } catch (RuntimeException unavailable) { button.setContentDescription(label + ": Unavailable"); }
            }
        };
        button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) {
                try { setting.set(!setting.get()); update.run(); }
                catch (RuntimeException failed) { Toast.makeText(FocusSettingsActivity.this, label + " could not be saved.", Toast.LENGTH_LONG).show(); }
            }
        });
        controls.addView(button); update.run(); return button;
    }

    private void appSize(LinearLayout controls, final boolean shadow) {
        final String label = shadow ? "App focus shadow" : "App focus growth";
        final Button row = Pages.menuButton(this,"",null);
        row.setTag(shadow ? "icons:app-shadow" : "icons:app-growth");
        final Runnable refresh = new Runnable() {
            @Override public void run() {
                try {
                    int value = shadow ? FocusPreferences.appShadowSize(FocusSettingsActivity.this) : FocusPreferences.appGrowth(FocusSettingsActivity.this);
                    row.setText(label + ": " + (value == 0 ? "Off" : value + (shadow ? " dp" : "%")));
                } catch (IllegalStateException unavailable) { row.setText(label + ": Unavailable"); }
            }
        };
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) {
                final int[] values = shadow ? new int[]{0,18,28,38} : new int[]{18,0,6,12,24,30};
                String[] labels = shadow ? new String[]{"Off","Default (18 dp)","Large (28 dp)","Extra large (38 dp)"}
                        : new String[]{"Default (18%)","Off","6%","12%","24%","30%"};
                try {
                    int current = shadow ? FocusPreferences.appShadowSize(FocusSettingsActivity.this) : FocusPreferences.appGrowth(FocusSettingsActivity.this);
                    int selected = -1; for (int i=0;i<values.length;i++) if (values[i] == current) selected=i;
                    new AlertDialog.Builder(FocusSettingsActivity.this).setTitle(label).setSingleChoiceItems(labels,selected,
                            new android.content.DialogInterface.OnClickListener() {
                                @Override public void onClick(android.content.DialogInterface dialog,int which) {
                                    try {
                                        if (shadow) FocusPreferences.saveAppShadow(FocusSettingsActivity.this,values[which]);
                                        else FocusPreferences.saveAppSize(FocusSettingsActivity.this,FocusPreferences.APP_GROWTH,values[which]);
                                        dialog.dismiss();refresh.run();row.requestFocus();
                                    } catch (RuntimeException failed) { Toast.makeText(FocusSettingsActivity.this,failed.getMessage(),Toast.LENGTH_LONG).show(); }
                                }
                            }).setNegativeButton("Cancel",null).show();
                } catch (RuntimeException failed) { Toast.makeText(FocusSettingsActivity.this,failed.getMessage(),Toast.LENGTH_LONG).show(); }
            }
        });
        controls.addView(row);refresh.run();
    }

    private final class CheckMark extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            boolean focused = false;
            for (int state : getState()) if (state == android.R.attr.state_focused) focused = true;
            paint.setColor(focused ? 0xff000000 : 0xffd3d3d3); paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2)); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
            Path path = new Path(); path.moveTo(bounds.left + dp(3), bounds.top + dp(10));
            path.lineTo(bounds.left + dp(8), bounds.top + dp(15)); path.lineTo(bounds.left + dp(17), bounds.top + dp(4));
            canvas.drawPath(path, paint);
        }
        @Override public int getIntrinsicWidth() { return dp(22); }
        @Override public boolean isStateful() { return true; }
        @Override protected boolean onStateChange(int[] states) { invalidateSelf(); return true; }
        @Override public int getIntrinsicHeight() { return dp(20); }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    @Override public void onBackPressed() {
        if (advanced) showIconsPanel(); else super.onBackPressed();
    }

    private void color(LinearLayout parent, String label, final int shift, final boolean fill) {
        slider(parent, label, ((fill ? options.fillRgb : options.borderRgb) >> shift) & 255, 255, "", new Choice() {
            @Override public void selected(int value) {
                int rgb = ((fill ? options.fillRgb : options.borderRgb) & ~(255 << shift)) | (value << shift);
                save(new FocusOptions(options.durationMillis, options.growthPercent,
                        options.borderEnabled, fill ? options.borderRgb : rgb, options.borderOpacity,
                        options.fillEnabled, fill ? rgb : options.fillRgb, options.fillOpacity));
            }
        });
    }

    private SeekBar slider(LinearLayout parent, String label, int value, int max, String suffix, final Choice action) {
        final TextView caption = text(label + ": " + value + suffix, 17, 0xffe5dff0);
        parent.addView(caption);
        SeekBar bar = new SeekBar(this);
        bar.setContentDescription(label);
        bar.setMax(max);
        bar.setKeyProgressIncrement(1);
        bar.setProgress(value);
        bar.setMinimumHeight(dp(44));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar view) {}
            @Override public void onStopTrackingTouch(SeekBar view) {}
            @Override public void onProgressChanged(SeekBar view, int selected, boolean fromUser) {
                caption.setText(label + ": " + selected + suffix);
                if (fromUser) action.selected(selected);
            }
        });
        parent.addView(bar, new LinearLayout.LayoutParams(-1, dp(44)));
        return bar;
    }

    private RadioGroup choices(LinearLayout parent, String[] labels, int[] values, int selected, final Choice action) {
        final RadioGroup group = new RadioGroup(this);
        group.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < labels.length; i++) {
            RadioButton button = new RadioButton(this);
            button.setId(View.generateViewId());
            button.setText(labels[i]);
            button.setTextSize(16);
            button.setTextColor(0xffe5dff0);
            button.setTag(values[i]);
            final int choice = values[i];
            button.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { action.selected(choice); }
            });
            button.setMinHeight(dp(48));
            group.addView(button, new RadioGroup.LayoutParams(-1, -2));
            if (values[i] == selected) group.check(button.getId());
        }
        parent.addView(group);
        return group;
    }

    private void saveMotion(String key, int value) {
        try {
            FocusPreferences.saveMotion(this, key, value);
            options = FocusPreferences.load(this);
            importedNotice.setVisibility(FocusPreferences.legacyDefaultAppMode(this) ? View.VISIBLE : View.GONE);
            preview.refreshPreferences();
            updateSummary();
        } catch (IllegalStateException failure) {
            new AlertDialog.Builder(this).setTitle("Settings not saved")
                    .setMessage(failure.getMessage()).setPositiveButton("OK", null).show();
        }
    }

    private void save(FocusOptions value) {
        try {
            FocusPreferences.save(this, value);
            options = value;
            importedNotice.setVisibility(View.GONE);
            preview.refreshPreferences();
            updateSummary();
        } catch (IllegalStateException failure) {
            new AlertDialog.Builder(this).setTitle("Settings not saved")
                    .setMessage(failure.getMessage()).setPositiveButton("OK", null).show();
        }
    }

    private void updateSummary() {
        growthSlider.setProgress(options.growthPercent);
        int selected = -1;
        for (int i = 0; i < growthChoices.getChildCount(); i++) {
            View button = growthChoices.getChildAt(i);
            if ((Integer) button.getTag() == options.growthPercent) selected = button.getId();
        }
        if (growthChoices.getCheckedRadioButtonId() != selected) growthChoices.check(selected);
        summary.setText((options.durationMillis == 0 ? "Instant transition" : options.durationMillis + " ms transition")
                + "\n" + options.growthPercent + "% growth"
                + "\n" + (options.borderEnabled ? "Border " + options.borderOpacity + "% opacity" : "Border off")
                + "\n" + (options.fillEnabled ? "Fill " + options.fillOpacity + "% opacity" : "Fill off"));
    }

    private void section(LinearLayout parent, String label) {
        TextView view = text(label, 20, Color.WHITE);
        view.setPadding(0, dp(18), 0, dp(6));
        parent.addView(view);
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private interface Choice { void selected(int value); }
}
