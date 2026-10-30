package com.jeremykenedy.firetv.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MenuBackgroundActivity extends CardActivity {
    private TextView style, motion, speed, intensity, brightness, palette;
    private TextView overlay, overlayMotion, overlaySpeed, overlayIntensity, overlayOpacity, overlayPalette;
    private MenuBackgroundView preview, overlayPreview;

    public static void open(Context context) {
        context.startActivity(new Intent(context, MenuBackgroundActivity.class));
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        title("Backgrounds and overlays");
        LinearLayout body = scrollBody();
        body.addView(text("Keep your wallpaper and add a transparent overlay, or choose a full background. Each layer has its own motion and colors. The preview uses a neutral base.", 18, MUTED));
        FrameLayout sample = new FrameLayout(this);
        sample.setBackground(background(Color.rgb(34, 40, 51), 8));
        preview = new MenuBackgroundView(this, false);
        overlayPreview = new MenuBackgroundView(this, false, true);
        sample.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        sample.addView(overlayPreview, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams previewSize = new LinearLayout.LayoutParams(dp(320), dp(180));
        previewSize.topMargin = dp(10); previewSize.bottomMargin = dp(10);
        body.addView(sample, previewSize);
        addButton(body, button("Holiday themes", new View.OnClickListener() {
            @Override public void onClick(View view) { chooseHoliday(); }
        }));
        style = choice(body, "Background", MenuBackgroundOptions.STYLE, MenuBackgroundOptions.LABELS, MenuBackgroundOptions.STYLES);
        motion = motion(body, MenuBackgroundOptions.FROZEN);
        speed = level(body, MenuBackgroundOptions.SPEED, "Background speed", MenuBackgroundOptions.SPEEDS);
        intensity = level(body, MenuBackgroundOptions.INTENSITY, "Background density", MenuBackgroundOptions.LEVELS);
        brightness = level(body, MenuBackgroundOptions.BRIGHTNESS, "Background brightness", MenuBackgroundOptions.LEVELS);
        palette = palette(body, false);
        body.addView(text("Transparent overlay", 22, INK));
        body.addView(text("Off removes only the overlay. Your background, wallpaper and app layout remain as they are.", 16, MUTED));
        String[] overlayLabels = MenuBackgroundOptions.LABELS.clone();
        overlayLabels[0] = "Off: no overlay";
        overlay = choice(body, "Overlay", MenuBackgroundOptions.OVERLAY, overlayLabels, MenuBackgroundOptions.STYLES);
        overlayMotion = motion(body, MenuBackgroundOptions.OVERLAY_FROZEN);
        overlaySpeed = level(body, MenuBackgroundOptions.OVERLAY_SPEED, "Overlay speed", MenuBackgroundOptions.SPEEDS);
        overlayIntensity = level(body, MenuBackgroundOptions.OVERLAY_INTENSITY, "Overlay density", MenuBackgroundOptions.LEVELS);
        overlayOpacity = level(body, MenuBackgroundOptions.OVERLAY_OPACITY, "Overlay opacity", MenuBackgroundOptions.LEVELS);
        overlayPalette = palette(body, true);
        refresh();
        style.requestFocus();
    }

    private TextView choice(LinearLayout body, final String title, final String key,
            final String[] labels, final String[] values) {
        TextView control = button(title, new View.OnClickListener() {
            @Override public void onClick(final View view) {
                int selected = MenuBackgroundOptions.style(DeviceSettings.prefs(MenuBackgroundActivity.this).getString(key, "off"));
                new AlertDialog.Builder(MenuBackgroundActivity.this).setTitle(title)
                        .setSingleChoiceItems(labels, selected, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (commit(DeviceSettings.prefs(MenuBackgroundActivity.this).edit().putString(key, values[which]))) {
                            dialog.dismiss(); view.requestFocus();
                        }
                    }
                }).setNegativeButton("Cancel", null).show();
            }
        });
        addButton(body, control);
        return control;
    }

    private TextView motion(LinearLayout body, final String key) {
        TextView control = button("Motion", new View.OnClickListener() {
            @Override public void onClick(final View view) {
                int selected = DeviceSettings.prefs(MenuBackgroundActivity.this).getBoolean(key, false) ? 1 : 0;
                new AlertDialog.Builder(MenuBackgroundActivity.this).setTitle("Motion")
                        .setSingleChoiceItems(new String[]{"Animated", "Still"}, selected, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (commit(DeviceSettings.prefs(MenuBackgroundActivity.this).edit().putBoolean(key, which == 1))) {
                            dialog.dismiss(); view.requestFocus();
                        }
                    }
                }).setNegativeButton("Cancel", null).show();
            }
        });
        addButton(body, control);
        return control;
    }

    private TextView level(LinearLayout body, final String key, final String title, final int[] choices) {
        TextView control = button(title, new View.OnClickListener() {
            @Override public void onClick(final View view) {
                String[] labels = new String[choices.length];
                int current = DeviceSettings.prefs(MenuBackgroundActivity.this).getInt(key, 50), selected = 0;
                for (int i = 0; i < choices.length; i++) { labels[i] = choices[i] + "%"; if (choices[i] == current) selected = i; }
                new AlertDialog.Builder(MenuBackgroundActivity.this).setTitle(title).setSingleChoiceItems(labels, selected,
                        new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (commit(DeviceSettings.prefs(MenuBackgroundActivity.this).edit().putInt(key, choices[which]))) {
                            dialog.dismiss(); view.requestFocus();
                        }
                    }
                }).setNegativeButton("Cancel", null).show();
            }
        });
        addButton(body, control);
        return control;
    }

    private TextView palette(LinearLayout body, final boolean overlayLayer) {
        TextView control = button("Colors", new View.OnClickListener() {
            @Override public void onClick(final View view) {
                String[] names = new String[HolidayThemes.ALL.length + 1];
                names[0] = "Original colors";
                for (int i = 0; i < HolidayThemes.ALL.length; i++) names[i + 1] = HolidayThemes.ALL[i].name;
                final String key = overlayLayer ? MenuBackgroundOptions.OVERLAY_PALETTE : MenuBackgroundOptions.PALETTE;
                String current = DeviceSettings.prefs(MenuBackgroundActivity.this).getString(key, "");
                int selected = 0;
                for (int i = 0; i < HolidayThemes.ALL.length; i++) if (HolidayThemes.ALL[i].id.equals(current)) selected = i + 1;
                new AlertDialog.Builder(MenuBackgroundActivity.this).setTitle("Colors")
                        .setSingleChoiceItems(names, selected, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        SharedPreferences.Editor edit = DeviceSettings.prefs(MenuBackgroundActivity.this).edit();
                        if (which == 0) edit.remove(key); else edit.putString(key, HolidayThemes.ALL[which - 1].id);
                        if (commit(edit)) { dialog.dismiss(); view.requestFocus(); }
                    }
                }).setNegativeButton("Cancel", null).show();
            }
        });
        addButton(body, control);
        return control;
    }

    private void chooseHoliday() {
        String[] names = HolidayThemes.names();
        String[] choices = java.util.Arrays.copyOf(names, names.length + 1);
        choices[names.length] = "My own celebration";
        new AlertDialog.Builder(this).setTitle("Holiday themes")
                .setItems(choices, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (which < HolidayThemes.ALL.length) configureHoliday(HolidayThemes.ALL[which]);
                else new AlertDialog.Builder(MenuBackgroundActivity.this)
                        .setTitle("My own celebration")
                        .setMessage("Choose any background or overlay, colors and motion here. Then save the setup under your celebration's name in Saved setups.")
                        .setPositiveButton("Choose appearance", null).show();
            }
        }).setNegativeButton("Cancel", null).show();
    }

    private void configureHoliday(final HolidayThemes.Theme theme) {
        final boolean[] choices = {true, true, false};
        new AlertDialog.Builder(this).setTitle(theme.name)
                .setMultiChoiceItems(new String[]{"Transparent overlay over my current background", "Animated (off for still)", "Use coordinated icon colors"}, choices,
                        new DialogInterface.OnMultiChoiceClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which, boolean checked) { choices[which] = checked; }
                }).setNegativeButton("Cancel", null).setPositiveButton("Apply", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        try {
                            HolidayThemeSettings.apply(MenuBackgroundActivity.this, theme.id, choices[0], choices[1], choices[2]);
                            refresh();
                            if (choices[2]) {
                                Intent home = getPackageManager().getLaunchIntentForPackage(getPackageName());
                                if (home != null) {
                                    home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(home); finish();
                                }
                            }
                        } catch (RuntimeException failed) { error(failed); }
                    }
                }).show();
    }

    private boolean commit(SharedPreferences.Editor edit) {
        if (!edit.commit()) { error(new IllegalStateException("The appearance could not be saved.")); return false; }
        refresh(); return true;
    }

    private void refresh() {
        refreshMenuBackground(); preview.refresh(); overlayPreview.refresh();
        SharedPreferences prefs = DeviceSettings.prefs(this);
        int baseStyle = MenuBackgroundOptions.style(prefs.getString(MenuBackgroundOptions.STYLE, "off"));
        int overlayStyle = MenuBackgroundOptions.style(prefs.getString(MenuBackgroundOptions.OVERLAY, "off"));
        style.setText("Background: " + MenuBackgroundOptions.LABELS[baseStyle]);
        overlay.setText("Overlay: " + (overlayStyle == 0 ? "Off" : MenuBackgroundOptions.LABELS[overlayStyle]));
        refreshLayer(prefs, baseStyle, false, motion, speed, intensity, brightness, palette);
        refreshLayer(prefs, overlayStyle, true, overlayMotion, overlaySpeed, overlayIntensity, overlayOpacity, overlayPalette);
    }

    private void refreshLayer(SharedPreferences prefs, int selected, boolean overlayLayer, TextView motion,
            TextView speed, TextView density, TextView level, TextView colors) {
        boolean frozen = prefs.getBoolean(overlayLayer ? MenuBackgroundOptions.OVERLAY_FROZEN : MenuBackgroundOptions.FROZEN, false);
        boolean movable = MenuBackgroundOptions.animated(selected);
        motion.setVisibility(movable ? View.VISIBLE : View.GONE);
        motion.setText("Motion: " + (frozen || !movable ? "Still" : "Animated"));
        speed.setVisibility(movable && !frozen ? View.VISIBLE : View.GONE);
        density.setVisibility(selected == 0 ? View.GONE : View.VISIBLE);
        level.setVisibility(selected == 0 ? View.GONE : View.VISIBLE);
        colors.setVisibility(selected == 0 ? View.GONE : View.VISIBLE);
        String prefix = overlayLayer ? "Overlay " : "Background ";
        speed.setText(prefix + "speed: " + prefs.getInt(overlayLayer ? MenuBackgroundOptions.OVERLAY_SPEED : MenuBackgroundOptions.SPEED, 50) + "%");
        density.setText(prefix + "density: " + prefs.getInt(overlayLayer ? MenuBackgroundOptions.OVERLAY_INTENSITY : MenuBackgroundOptions.INTENSITY, 50) + "%");
        level.setText(prefix + (overlayLayer ? "opacity: " : "brightness: ")
                + prefs.getInt(overlayLayer ? MenuBackgroundOptions.OVERLAY_OPACITY : MenuBackgroundOptions.BRIGHTNESS, 50) + "%");
        HolidayThemes.Theme theme = HolidayThemes.find(prefs.getString(overlayLayer ? MenuBackgroundOptions.OVERLAY_PALETTE : MenuBackgroundOptions.PALETTE, ""));
        colors.setText(prefix + "colors: " + (theme == null ? "Original" : theme.name));
    }

    @Override protected void onResume() {
        super.onResume(); preview.menuResumed(true); overlayPreview.menuResumed(true);
    }
    @Override protected void onPause() {
        preview.menuResumed(false); overlayPreview.menuResumed(false); super.onPause();
    }
}
