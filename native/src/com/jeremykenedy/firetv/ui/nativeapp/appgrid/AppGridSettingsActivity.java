package com.jeremykenedy.firetv.ui.nativeapp.appgrid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppGridSettingsActivity extends com.jeremykenedy.firetv.ui.nativeapp.MenuActivity {
    private boolean startupBlocked;
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private List<AppInventory.App> apps = new ArrayList<>();
    private LinearLayout body;
    private String focusKey = "columns";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        render();
        loader.execute(new Runnable() {
            @Override public void run() {
                final List<AppInventory.App> loaded = AppInventory.load(getApplicationContext());
                runOnUiThread(new Runnable() {
                    @Override public void run() { if (!isDestroyed()) { apps = loaded; render(); } }
                });
            }
        });
    }

    private void render() {
        AppGridOptions options = AppGridPreferences.load(this);
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "Apps grid", 30));
        page.addView(Pages.text(this, "Favorites appear first. Hold an app on Home to move, hide, or customize it. Hidden apps stay available here.", 16));
        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        add("columns", "Apps per row: " + options.columns, new Runnable() {
            @Override public void run() { chooseColumns(); }
        });
        add("direction", "Scroll: " + (options.horizontal ? "Horizontal" : "Vertical (default)"), new Runnable() {
            @Override public void run() {
                AppGridOptions old = AppGridPreferences.load(AppGridSettingsActivity.this);
                save(old.grid(old.columns, old.rows, !old.horizontal));
            }
        });
        add("rows", "Horizontal rows: " + (options.rows == 0 ? "Auto" : options.rows), new Runnable() {
            @Override public void run() { number(false); }
        });
        add("reset", "Restore default grid dimensions", new Runnable() {
            @Override public void run() {
                AppGridOptions old = AppGridPreferences.load(AppGridSettingsActivity.this);
                save(old.grid(5, 0, false));
            }
        });
        body.addView(Pages.text(this, "Installed apps", 22));
        for (final AppInventory.App app : apps) {
            add(app.key(), app.label + (options.hidden.contains(app.key()) ? " · Hidden" : " · Shown")
                    + (options.favorites.contains(app.key()) ? " · Favorite" : ""), new Runnable() {
                @Override public void run() { appActions(app); }
            });
        }
        if (apps.isEmpty()) body.addView(Pages.text(this, "No TV apps available.", 16));
    }

    private void add(final String key, String label, final Runnable action) {
        Button button = Pages.button(this, label, new View.OnClickListener() {
            @Override public void onClick(View view) { focusKey = key; action.run(); }
        });
        body.addView(button);
        if (key.equals(focusKey)) button.requestFocus();
    }

    private void save(AppGridOptions options) { AppGridPreferences.save(this, options); render(); }

    private void appActions(final AppInventory.App app) {
        AppGridOptions options = AppGridPreferences.load(this);
        AlertDialog actions = new AlertDialog.Builder(this).setTitle(app.label).setItems(new String[] {
            options.hidden.contains(app.key()) ? "Show on Home" : "Hide from Home",
            options.favorites.contains(app.key()) ? "Remove favorite" : "Add favorite",
            "Move earlier", "Move later"
        }, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                AppGridOptions old = AppGridPreferences.load(AppGridSettingsActivity.this).discover(AppInventory.keys(apps));
                if (which == 0) save(old.toggleHidden(app.key()));
                else if (which == 1) save(old.toggleFavorite(app.key()));
                else {
                    try { HomeTileActions.move(AppGridSettingsActivity.this, app.key(), which == 2 ? -1 : 1); render(); }
                    catch (RuntimeException failed) { Toast.makeText(AppGridSettingsActivity.this, failed.getMessage(), Toast.LENGTH_LONG).show(); }
                }
            }
        }).setNegativeButton("Cancel", null).show();
        actions.getListView().requestFocus();
        actions.getListView().setSelection(0);
    }

    private void chooseColumns() {
        new AlertDialog.Builder(this).setTitle("Apps per row").setItems(new String[] {
            "3", "4", "5 (default)", "6", "7", "8", "Custom (2–12)"
        }, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (which == 6) { number(true); return; }
                AppGridOptions old = AppGridPreferences.load(AppGridSettingsActivity.this);
                save(old.grid(which + 3, old.rows, old.horizontal));
            }
        }).setNegativeButton("Cancel", null).show();
    }

    private void number(final boolean columns) {
        final EditText value = new EditText(this); value.setInputType(InputType.TYPE_CLASS_NUMBER);
        AppGridOptions old = AppGridPreferences.load(this);
        value.setText(String.valueOf(columns ? old.columns : old.rows)); value.selectAll();
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(columns ? "Apps per row (2–12)" : "Horizontal rows (0 = Auto, 1–8)")
                .setView(value).setPositiveButton("Save", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        try {
                            int number = Integer.parseInt(value.getText().toString());
                            if (columns && (number < 2 || number > 12)) throw new IllegalArgumentException();
                            AppGridOptions current = AppGridPreferences.load(AppGridSettingsActivity.this);
                            save(current.grid(columns ? number : current.columns, columns ? current.rows : number, current.horizontal));
                            dialog.dismiss();
                        } catch (IllegalArgumentException bad) { value.setError(columns ? "Choose 2 through 12." : "Choose 0 through 8."); }
                    }
                });
            }
        });
        dialog.show();
    }
    @Override protected void onDestroy() {
        if (startupBlocked) { super.onDestroy(); return; } loader.shutdownNow(); super.onDestroy(); }
}
