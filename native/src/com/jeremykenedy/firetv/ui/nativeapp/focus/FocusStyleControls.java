package com.jeremykenedy.firetv.ui.nativeapp.focus;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetAppearance;
import java.util.Locale;

/** Shared app focus controls used by the original-style layout and icon panels. */
public final class FocusStyleControls {
    private FocusStyleControls() {}

    public static void addLegacyControls(final Activity activity, LinearLayout parent) {
        final Button style = Pages.menuButton(activity, "", null);
        final Button fill = Pages.menuButton(activity, "", null);
        final Button border = Pages.menuButton(activity, "", null);
        final Runnable update = new Runnable() {
            @Override public void run() {
                String selected = FocusPreferences.appStyle(activity);
                String label = "default".equals(selected) ? "Default"
                        : "border".equals(selected) ? "Border only"
                        : "color".equals(selected) ? "Color and border" : "Custom";
                style.setText("Focus style: " + label);
                fill.setText(String.format(Locale.US, "Fill color: #%06X", FocusPreferences.appColor(activity, true)));
                border.setText(String.format(Locale.US, "Border color: #%06X", FocusPreferences.appColor(activity, false)));
            }
        };
        style.setTag("focus:style"); fill.setTag("focus:fill-color"); border.setTag("focus:border-color");
        style.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) {
                new AlertDialog.Builder(activity).setTitle("Focus style")
                        .setItems(new String[]{"Default", "Border only", "Color and border"}, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int selected) {
                                try { FocusPreferences.setAppStyle(activity, new String[]{"default", "border", "color"}[selected]); update.run(); }
                                catch (RuntimeException failure) { Toast.makeText(activity, "Focus style could not be saved.", Toast.LENGTH_LONG).show(); }
                            }
                        }).setNegativeButton("Cancel", null).show();
            }
        });
        fill.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) { color(activity, "Fill color", true, update); }
        });
        border.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) { color(activity, "Border color", false, update); }
        });
        parent.addView(style); parent.addView(fill); parent.addView(border); update.run();
    }

    public static Button addOriginalColorControl(final Activity activity, LinearLayout parent,
            final String label, final boolean fill) {
        Button button = Pages.menuButton(activity, label, new View.OnClickListener() {
            @Override public void onClick(View ignored) { color(activity, label, fill, new Runnable() { @Override public void run() {} }); }
        });
        button.setTag(fill ? "icons:focus-background" : "icons:focus-border");
        parent.addView(button);
        return button;
    }

    private static void color(final Activity activity, String title, final boolean fill, final Runnable update) {
        WidgetAppearance.choose(activity, title, new WidgetAppearance.ColorStore() {
            @Override public int color() { return 0xff000000 | FocusPreferences.appColor(activity, fill); }
            @Override public int opacity() {
                FocusOptions options = FocusPreferences.load(activity);
                return fill ? options.fillOpacity : options.borderOpacity;
            }
            @Override public boolean save(int color, int opacity) {
                try { FocusPreferences.saveAppColorAndOpacity(activity, fill, color, opacity); return true; }
                catch (RuntimeException failed) {
                    Toast.makeText(activity, "Focus color could not be saved.", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        }, update);
    }
}
