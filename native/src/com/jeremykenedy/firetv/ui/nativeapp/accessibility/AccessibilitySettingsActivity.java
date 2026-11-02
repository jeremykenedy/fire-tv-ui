package com.jeremykenedy.firetv.ui.nativeapp.accessibility;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;

public final class AccessibilitySettingsActivity extends com.jeremykenedy.firetv.ui.nativeapp.MenuActivity {
    private boolean startupBlocked;
    private Button size;
    private Button contrast;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "Text and contrast", 30));
        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(controls);
        controls.addView(Pages.text(this, "Make menu text easier to read. Default keeps your existing appearance. Text size also follows the TV's system font scale.", 18));
        size = Pages.button(this, "", new View.OnClickListener() {
            @Override public void onClick(View view) { chooseSize(); }
        });
        size.setId(0x2071);
        controls.addView(size);
        contrast = Pages.button(this, "", new View.OnClickListener() {
            @Override public void onClick(View view) {
                save(AccessibilityPreferences.textPercent(AccessibilitySettingsActivity.this),
                        !AccessibilityPreferences.highContrast(AccessibilitySettingsActivity.this));
            }
        });
        contrast.setId(0x2072);
        controls.addView(contrast);
        controls.addView(Pages.text(this, "High contrast uses white text and outlined buttons. The focused button has a white surface and dark text. Artwork and your chosen wallpaper stay available.", 18));
        refresh();
        if (state == null) size.requestFocus();
    }

    private void chooseSize() {
        final int[] values = {100, 110, 120};
        int current = AccessibilityPreferences.textPercent(this);
        int selected = current == 110 ? 1 : current == 120 ? 2 : 0;
        new AlertDialog.Builder(this).setTitle("Text size")
                .setSingleChoiceItems(new String[] {"Default (100%)", "Larger (110%)", "Largest (120%)"}, selected,
                        new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                save(values[which], AccessibilityPreferences.highContrast(AccessibilitySettingsActivity.this));
                            }
                        })
                .setNegativeButton("Cancel", null).setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override public void onDismiss(DialogInterface dialog) { size.requestFocus(); }
                }).show();
    }

    private void save(int percent, boolean highContrast) {
        try {
            AccessibilityPreferences.save(this, percent, highContrast);
            refresh();
        } catch (IllegalStateException failure) {
            new AlertDialog.Builder(this).setTitle("Settings not saved").setMessage(failure.getMessage())
                    .setPositiveButton("OK", null).show();
        }
    }

    private void refresh() {
        int percent = AccessibilityPreferences.textPercent(this);
        size.setText("Text size: " + (percent == 100 ? "Default" : percent == 110 ? "Larger" : "Largest") + " (" + percent + "%)");
        contrast.setText("High contrast: " + (AccessibilityPreferences.highContrast(this) ? "On" : "Off"));
        contrast.setContentDescription(contrast.getText());
        AccessibilityStyle.apply(this, getWindow().getDecorView());
    }
}
