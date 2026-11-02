package com.jeremykenedy.firetv.ui.nativeapp.focus;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
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

public final class FocusSettingsActivity extends Activity {
    private FocusOptions options;
    private FocusTileView preview;
    private TextView summary;
    private RadioGroup growthChoices;
    private SeekBar growthSlider;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        options = FocusPreferences.load(this);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        LinearLayout root = column();
        root.setPadding(dp(32), dp(24), dp(32), dp(24));
        root.setBackgroundColor(0xff100d28);
        root.addView(text("Focus and motion", 30, Color.WHITE));
        TextView intro = text("Choose how apps respond as you move with the remote.", 17, 0xffc8bfdc);
        intro.setPadding(0, dp(6), 0, dp(18));
        root.addView(intro);
        LinearLayout row = new LinearLayout(this);
        root.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
        ScrollView scroll = new ScrollView(this);
        row.addView(scroll, new LinearLayout.LayoutParams(0, -1, 3));
        LinearLayout controls = column();
        controls.setPadding(0, 0, dp(28), dp(16));
        scroll.addView(controls);
        LinearLayout sample = column();
        sample.setGravity(Gravity.CENTER);
        row.addView(sample, new LinearLayout.LayoutParams(0, -1, 2));
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
        preview.setPreviewFocused(true);
        sample.addView(preview, new LinearLayout.LayoutParams(-1, dp(180)));
        summary = text("", 16, 0xffc8bfdc);
        summary.setGravity(Gravity.CENTER);
        sample.addView(summary);
        controls.addView(text("Transition speed", 20, Color.WHITE));
        choices(controls, new String[]{"Instant", "Fast", "Normal", "Slow"},
                new int[]{0, 100, 180, 280}, options.durationMillis, new Choice() {
                    @Override public void selected(int value) {
                        save(new FocusOptions(value, options.growthPercent, options.borderEnabled,
                                options.borderRgb, options.borderOpacity, options.fillEnabled, options.fillRgb, options.fillOpacity));
                    }
                });
        section(controls, "Focus growth");
        growthChoices = choices(controls, new String[]{"None", "3%", "6%", "10%"},
                new int[]{0, 3, 6, 10}, options.growthPercent, new Choice() {
                    @Override public void selected(int value) {
                        if (value == options.growthPercent) return;
                        save(new FocusOptions(options.durationMillis, value, options.borderEnabled,
                                options.borderRgb, options.borderOpacity, options.fillEnabled, options.fillRgb, options.fillOpacity));
                    }
                });
        growthSlider = slider(controls, "Custom growth", options.growthPercent, FocusOptions.MAX_GROWTH, "%", new Choice() {
            @Override public void selected(int value) {
                save(new FocusOptions(options.durationMillis, value, options.borderEnabled,
                        options.borderRgb, options.borderOpacity, options.fillEnabled, options.fillRgb, options.fillOpacity));
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
        setContentView(root);
        updateSummary();
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
        group.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            RadioButton button = new RadioButton(this);
            button.setId(View.generateViewId());
            button.setText(labels[i]);
            button.setTextSize(16);
            button.setTextColor(0xffe5dff0);
            button.setTag(values[i]);
            button.setMinHeight(dp(48));
            group.addView(button, new RadioGroup.LayoutParams(0, -2, 1));
            if (values[i] == selected) group.check(button.getId());
        }
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup owner, int id) {
                View selected = owner.findViewById(id);
                if (selected != null) action.selected((Integer) selected.getTag());
            }
        });
        parent.addView(group);
        return group;
    }

    private void save(FocusOptions value) {
        try {
            FocusPreferences.save(this, value);
            options = value;
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
