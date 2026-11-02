package com.jeremykenedy.firetv.ui.nativeapp.inputs;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.text.InputFilter;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.focus.FocusTileView;
import com.jeremykenedy.firetv.ui.nativeapp.icons.IconPickerActivity;
import com.jeremykenedy.firetv.ui.nativeapp.icons.IconSelectionStore;

public final class InputViews {
    private InputViews() {}
    public static Drawable artwork(Context context, InputEntry input) {
        return IconSelectionStore.resolve(context, input.artworkKey(), factoryIcon(context, input.id), null);
    }
    public static FocusTileView tile(final Activity activity, final InputEntry input, final Runnable refresh) {
        return tile(activity, input, artwork(activity, input), refresh);
    }
    public static FocusTileView tile(final Activity activity, final InputEntry input, Drawable artwork, final Runnable refresh) {
        FocusTileView tile = new FocusTileView(activity);
        tile.setTitle(input.displayName);
        tile.setTag(input.artworkKey());
        tile.setContentDescription(input.displayName + ". " + input.message());
        tile.setIcon(artwork);
        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                InputActions.Result result = InputActions.open(activity, input);
                if (result != InputActions.Result.STARTED) Toast.makeText(activity,
                        result == InputActions.Result.UNAVAILABLE ? "This input cannot be opened on this device right now." : "The TV input could not be opened.", Toast.LENGTH_LONG).show();
            }
        });
        tile.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View view) { actions(activity, input, refresh); return true; }
        });
        return tile;
    }
    private static Drawable factoryIcon(Context context, String id) {
        try {
            TvInputManager manager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
            TvInputInfo info = manager == null ? null : manager.getTvInputInfo(id);
            if (info != null) {
                Drawable icon = info.loadIcon(context);
                if (icon != null) return icon;
            }
        } catch (RuntimeException unavailable) { }
        return context.getDrawable(android.R.drawable.ic_menu_slideshow);
    }
    public static void actions(final Activity activity, final InputEntry input, final Runnable refresh) {
        new AlertDialog.Builder(activity).setTitle(input.displayName)
                .setItems(new String[]{"Open input", "Rename input", "Use factory name", "Change icon", "Use factory icon"}, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        try {
                            if (which == 0) {
                                if (InputActions.open(activity, input) != InputActions.Result.STARTED)
                                    Toast.makeText(activity, "This input cannot be opened on this device right now.", Toast.LENGTH_LONG).show();
                            } else if (which == 1) { rename(activity, input, refresh); return; }
                            else if (which == 2) new InputPreferences(activity).rename(input.id, null);
                            else if (which == 3) { IconPickerActivity.open(activity, input.artworkKey(), input.displayName); return; }
                            else IconSelectionStore.save(activity, input.artworkKey(), IconSelectionStore.FACTORY);
                            if (refresh != null) refresh.run();
                        } catch (Exception failure) { Toast.makeText(activity, failure.getMessage(), Toast.LENGTH_LONG).show(); }
                    }
                }).setNegativeButton("Cancel", null).show();
    }
    public static AlertDialog rename(final Activity activity, final InputEntry input, final Runnable refresh) {
        final EditText edit = new EditText(activity);
        edit.setSingleLine(true);
        edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(60)});
        edit.setText(input.displayName);
        edit.selectAll();
        final AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Rename input").setView(edit)
                .setPositiveButton("Save", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface shown) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        try {
                            new InputPreferences(activity).rename(input.id, edit.getText().toString());
                            dialog.dismiss();
                            if (refresh != null) refresh.run();
                        } catch (Exception failure) { edit.setError(failure.getMessage()); edit.requestFocus(); }
                    }
                });
                edit.requestFocus();
            }
        });
        dialog.show();
        return dialog;
    }
}
