package com.jeremykenedy.firetv.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class UiSettingsActivity extends CardActivity {
    private TextView columns;

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
}
