package com.jeremykenedy.firetv.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MenuBackgroundActivity extends CardActivity {
    private TextView style, speed, intensity, brightness;
    private MenuBackgroundView preview;

    public static void open(Context context) {
        context.startActivity(new Intent(context, MenuBackgroundActivity.class));
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        title("Menu backgrounds");
        LinearLayout body = scrollBody();
        body.addView(text("Choose a moving or still background for Home and your Fire TV UI panels. Preview it here. Off restores your wallpaper.", 18, MUTED));
        preview = new MenuBackgroundView(this, false);
        LinearLayout.LayoutParams previewSize = new LinearLayout.LayoutParams(dp(320), dp(180));
        previewSize.topMargin = dp(12);
        previewSize.bottomMargin = dp(12);
        body.addView(preview, previewSize);
        style = button("", new View.OnClickListener() {
            @Override public void onClick(View view) { chooseStyle(); }
        });
        speed = button("", new View.OnClickListener() {
            @Override public void onClick(View view) { chooseLevel(MenuBackgroundOptions.SPEED, "Speed", MenuBackgroundOptions.SPEEDS); }
        });
        intensity = button("", new View.OnClickListener() {
            @Override public void onClick(View view) { chooseLevel(MenuBackgroundOptions.INTENSITY, "Intensity", MenuBackgroundOptions.LEVELS); }
        });
        brightness = button("", new View.OnClickListener() {
            @Override public void onClick(View view) { chooseLevel(MenuBackgroundOptions.BRIGHTNESS, "Brightness", MenuBackgroundOptions.LEVELS); }
        });
        addButton(body, style); addButton(body, speed); addButton(body, intensity); addButton(body, brightness);
        refresh();
        style.requestFocus();
    }

    private void refresh() {
        refreshMenuBackground();
        preview.setVisibility(MenuBackgroundView.enabled(this) ? View.VISIBLE : View.GONE);
        preview.refresh();
        android.content.SharedPreferences prefs = DeviceSettings.prefs(this);
        int selected = MenuBackgroundOptions.style(prefs.getString(MenuBackgroundOptions.STYLE, "off"));
        style.setText("Background: " + MenuBackgroundOptions.LABELS[selected]);
        speed.setVisibility(MenuBackgroundOptions.animated(selected) ? View.VISIBLE : View.GONE);
        intensity.setVisibility(selected == 0 ? View.GONE : View.VISIBLE);
        brightness.setVisibility(selected == 0 ? View.GONE : View.VISIBLE);
        speed.setText("Speed: " + MenuBackgroundOptions.speed(prefs.getInt(MenuBackgroundOptions.SPEED, 50)) + "%");
        intensity.setText("Intensity: " + MenuBackgroundOptions.level(prefs.getInt(MenuBackgroundOptions.INTENSITY, 50)) + "%");
        brightness.setText("Brightness: " + MenuBackgroundOptions.level(prefs.getInt(MenuBackgroundOptions.BRIGHTNESS, 50)) + "%");
    }

    @Override protected void onResume() {
        super.onResume();
        preview.menuResumed(true);
    }

    @Override protected void onPause() {
        preview.menuResumed(false);
        super.onPause();
    }

    private void chooseStyle() {
        int selected = MenuBackgroundOptions.style(DeviceSettings.prefs(this).getString(MenuBackgroundOptions.STYLE, "off"));
        new AlertDialog.Builder(this).setTitle("Menu background")
                .setSingleChoiceItems(MenuBackgroundOptions.LABELS, selected, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface dialog, int which) {
                        if (!DeviceSettings.prefs(MenuBackgroundActivity.this).edit().putString(MenuBackgroundOptions.STYLE, MenuBackgroundOptions.STYLES[which]).commit()) {
                            error(new IllegalStateException("The background choice could not be saved."));
                            return;
                        }
                        dialog.dismiss(); refresh(); style.requestFocus();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void chooseLevel(final String setting, String title, final int[] choices) {
        String[] labels = new String[choices.length];
        int current = DeviceSettings.prefs(this).getInt(setting, 50), selected = 0;
        for (int i = 0; i < choices.length; i++) { labels[i] = choices[i] + "%"; if (choices[i] == current) selected = i; }
        new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels, selected, new android.content.DialogInterface.OnClickListener() {
            @Override public void onClick(android.content.DialogInterface dialog, int which) {
                if (!DeviceSettings.prefs(MenuBackgroundActivity.this).edit().putInt(setting, choices[which]).commit()) {
                    error(new IllegalStateException("The background setting could not be saved."));
                    return;
                }
                dialog.dismiss(); refresh();
            }
        }).setNegativeButton("Cancel", null).show();
    }
}
