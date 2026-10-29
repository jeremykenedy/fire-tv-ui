package com.jeremykenedy.firetv.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public final class UiSettingsActivity extends CardActivity {
    private TextView columns, focusStyle, focusBackground, focusBorder;
    private static final int[] FOCUS_COLORS = {
            0xffffffff, 0xff9ebcfa, 0xffbcacff, 0xffffcd78, 0xffffa9a2, 0xff263a5c
    };
    private static final String[] FOCUS_COLOR_LABELS = {
            "White", "Light blue", "Lavender", "Amber", "Coral", "Deep blue"
    };

    public static void open(Context context) {
        context.startActivity(new Intent(context, UiSettingsActivity.class));
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        title("Layout and focus");
        LinearLayout body = scrollBody();
        body.addView(text("Arrange the apps on Home. Changes keep your app order and existing choices.", 18, MUTED));
        columns = button("", new View.OnClickListener() {
            @Override public void onClick(View view) { chooseColumns(); }
        });
        addButton(body, columns);
        refreshColumns();
        body.addView(text("Default keeps your existing highlight. Border only leaves the icon background unchanged. Color and border adds a fill behind the artwork.", 16, MUTED));
        focusStyle = button("", new View.OnClickListener() {
            @Override public void onClick(View view) { chooseFocusStyle(); }
        });
        focusBackground = button("", new View.OnClickListener() {
            @Override public void onClick(View view) {
                chooseFocusColor(IconFocus.BACKGROUND, "Focus fill color", focusBackground);
            }
        });
        focusBorder = button("", new View.OnClickListener() {
            @Override public void onClick(View view) {
                chooseFocusColor(IconFocus.BORDER, "Focus border color", focusBorder);
            }
        });
        addButton(body, focusStyle);
        addButton(body, focusBackground);
        addButton(body, focusBorder);
        refreshFocus();
        columns.requestFocus();
    }

    private void refreshColumns() {
        SharedPreferences prefs = DeviceSettings.prefs(this);
        columns.setText("Apps per row: " + LauncherLayout.columns(prefs)
                + (prefs.contains(LauncherLayout.COLUMNS) ? "" : " (Auto / default)"));
    }

    private void chooseColumns() {
        final String[] labels = new String[LauncherLayout.PRESETS.length + 2];
        labels[0] = "Auto / default (5)";
        for (int i = 0; i < LauncherLayout.PRESETS.length; i++) {
            labels[i + 1] = Integer.toString(LauncherLayout.PRESETS[i]);
        }
        labels[labels.length - 1] = "Custom...";
        new AlertDialog.Builder(this).setTitle("Apps per row").setItems(labels, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int index) {
            if (index == labels.length - 1) {
                customColumns();
            } else {
                saveColumns(index == 0 ? null : LauncherLayout.PRESETS[index - 1]);
            }
            }
        }).setNegativeButton("Cancel", null).show();
    }

    private void customColumns() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setText(LauncherLayout.columns(DeviceSettings.prefs(this)).toString());
        input.selectAll();
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Custom apps per row")
                .setMessage("Enter a whole number from 2 to 12.").setView(input)
                .setPositiveButton("Save", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface shown) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
            try {
                int value = LauncherLayout.parseColumns(input.getText().toString());
                if (saveColumns(value)) dialog.dismiss();
            } catch (IllegalArgumentException invalid) {
                input.setError(invalid.getMessage());
                input.requestFocus();
            }
                    }
                });
            }
        });
        dialog.show();
    }

    private boolean saveColumns(Integer value) {
        SharedPreferences.Editor edit = DeviceSettings.prefs(this).edit();
        if (value == null) edit.remove(LauncherLayout.COLUMNS);
        else edit.putInt(LauncherLayout.COLUMNS, value);
        if (!edit.commit()) {
            error(new IllegalStateException("The layout could not be saved. Please try again."));
            return false;
        }
        refreshColumns();
        columns.requestFocus();
        return true;
    }

    private void refreshFocus() {
        String selected = IconFocus.style(this);
        String label = IconFocus.LABELS[0];
        for (int i = 0; i < IconFocus.STYLES.length; i++) {
            if (IconFocus.STYLES[i].equals(selected)) label = IconFocus.LABELS[i];
        }
        focusStyle.setText("Focus style: " + label);
        focusBackground.setText("Fill color (Color and border): " + hex(IconFocus.color(
                this, IconFocus.BACKGROUND, IconFocus.DEFAULT_BACKGROUND)));
        focusBorder.setText("Border color (custom styles): " + hex(IconFocus.color(
                this, IconFocus.BORDER, IconFocus.DEFAULT_BORDER)));
    }

    private void chooseFocusStyle() {
        int selected = 0;
        String current = IconFocus.style(this);
        for (int i = 0; i < IconFocus.STYLES.length; i++) {
            if (IconFocus.STYLES[i].equals(current)) selected = i;
        }
        new AlertDialog.Builder(this).setTitle("Icon focus style")
                .setSingleChoiceItems(IconFocus.LABELS, selected, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        try {
                            IconFocus.setStyle(UiSettingsActivity.this, IconFocus.STYLES[which]);
                            dialog.dismiss();
                            refreshFocus();
                            focusStyle.requestFocus();
                        } catch (IllegalStateException failed) {
                            error(failed);
                        }
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void chooseFocusColor(final String key, final String title, final TextView control) {
        String[] labels = new String[FOCUS_COLORS.length + 2];
        labels[0] = "Default: " + hex(defaultColor(key));
        for (int i = 0; i < FOCUS_COLORS.length; i++) {
            labels[i + 1] = FOCUS_COLOR_LABELS[i] + ": " + hex(FOCUS_COLORS[i]);
        }
        labels[labels.length - 1] = "Custom hex color...";
        new AlertDialog.Builder(this).setTitle(title).setItems(labels, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (which == FOCUS_COLORS.length + 1) {
                    customFocusColor(key, title, control);
                } else {
                    saveFocusColor(key, which == 0 ? defaultColor(key) : FOCUS_COLORS[which - 1], control);
                }
            }
        }).setNegativeButton("Cancel", null).show();
    }

    private void customFocusColor(final String key, String title, final TextView control) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setSingleLine(true);
        input.setText(hex(IconFocus.color(this, key, defaultColor(key))));
        input.selectAll();
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title)
                .setMessage("Enter six hexadecimal digits, such as #9EBCFA.").setView(input)
                .setPositiveButton("Save", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface shown) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        String value = input.getText().toString().trim();
                        if (!value.matches("#?[0-9a-fA-F]{6}")) {
                            input.setError("Enter six hexadecimal digits, such as #9EBCFA.");
                            input.requestFocus();
                            return;
                        }
                        if (saveFocusColor(key, Color.parseColor(value.startsWith("#") ? value : "#" + value), control)) {
                            dialog.dismiss();
                        }
                    }
                });
            }
        });
        dialog.show();
    }

    private boolean saveFocusColor(String key, int color, TextView control) {
        try {
            IconFocus.setColor(this, key, color);
            refreshFocus();
            control.requestFocus();
            return true;
        } catch (IllegalStateException failed) {
            error(failed);
            return false;
        }
    }

    private static int defaultColor(String key) {
        return IconFocus.BACKGROUND.equals(key) ? IconFocus.DEFAULT_BACKGROUND : IconFocus.DEFAULT_BORDER;
    }

    private static String hex(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0xffffff);
    }

}
