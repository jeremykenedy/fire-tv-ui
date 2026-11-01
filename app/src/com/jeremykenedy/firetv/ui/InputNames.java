package com.jeremykenedy.firetv.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.text.InputFilter;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

public final class InputNames {
    private InputNames() {}

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE);
    }

    public static String displayName(Context context, String key, String factoryLabel) {
        try {
            return InputNameValues.display(preferences(context).getString(InputNameValues.SETTING, "{}"),
                    key, factoryLabel);
        } catch (RuntimeException invalid) {
            return factoryLabel;
        }
    }

    public static void edit(final Context context, final String key, String shownName) {
        if (!InputNameValues.input(key)) return;
        final EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setHint("Input name");
        input.setText(shownName);
        input.selectAll();
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(InputNameValues.MAX_LENGTH)});
        final AlertDialog dialog = new AlertDialog.Builder(context).setTitle("Rename input")
                .setView(input).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface shown) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        try {
                            save(context, key, input.getText().toString());
                            dialog.dismiss();
                            IconOverrides.refresh(context);
                        } catch (Exception error) {
                            input.setError(error.getMessage());
                            input.requestFocus();
                        }
                    }
                });
            }
        });
        dialog.show();
        input.requestFocus();
    }

    public static void reset(Context context, String key) {
        try {
            save(context, key, null);
            Toast.makeText(context, "Factory name restored", Toast.LENGTH_SHORT).show();
            IconOverrides.refresh(context);
        } catch (Exception error) {
            Toast.makeText(context, error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static void save(Context context, String key, String name) throws Exception {
        SharedPreferences preferences = preferences(context);
        String previous = preferences.getString(InputNameValues.SETTING, null);
        String changed = InputNameValues.change(previous == null ? "{}" : previous, key, name);
        SharedPreferences.Editor edit = preferences.edit();
        if ("{}".equals(changed)) edit.remove(InputNameValues.SETTING);
        else edit.putString(InputNameValues.SETTING, changed);
        if (!edit.commit()) {
            SharedPreferences.Editor restore = preferences.edit();
            if (previous == null) restore.remove(InputNameValues.SETTING);
            else restore.putString(InputNameValues.SETTING, previous);
            restore.commit();
            throw new java.io.IOException("The input name could not be saved. Please try again.");
        }
    }
}
