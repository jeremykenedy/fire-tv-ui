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

    public static void open(Context context) {
        context.startActivity(new Intent(context, MenuBackgroundActivity.class));
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        title("Animated menu backgrounds");
        LinearLayout body = scrollBody();
        body.addView(text("Quiet native animation behind your menu. Off brings back your existing wallpaper. Animation stops when you leave Home.", 18, MUTED));
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
        android.content.SharedPreferences prefs = DeviceSettings.prefs(this);
        style.setText("Background: " + MenuBackgroundOptions.LABELS[MenuBackgroundOptions.style(prefs.getString(MenuBackgroundOptions.STYLE, "off"))]);
        speed.setText("Speed: " + MenuBackgroundOptions.speed(prefs.getInt(MenuBackgroundOptions.SPEED, 50)) + "%");
        intensity.setText("Intensity: " + MenuBackgroundOptions.level(prefs.getInt(MenuBackgroundOptions.INTENSITY, 50)) + "%");
        brightness.setText("Brightness: " + MenuBackgroundOptions.level(prefs.getInt(MenuBackgroundOptions.BRIGHTNESS, 50)) + "%");
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
                    error(new IllegalStateException("The animation setting could not be saved."));
                    return;
                }
                dialog.dismiss(); refresh();
            }
        }).setNegativeButton("Cancel", null).show();
    }
}
