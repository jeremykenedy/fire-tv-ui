package com.jeremykenedy.firetv.ui.nativeapp;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.jeremykenedy.firetv.ui.nativeapp.focus.FocusTileView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Independent review shell. It neither selects Home nor imports production settings. */
public final class MainActivity extends Activity {
    private final List<FocusTileView> tiles = new ArrayList<>();
    private final ExecutorService loader = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout body = Pages.page(this);
        body.addView(Pages.text(this, "Fire TV UI", 30));
        body.addView(Pages.text(this, "Source preview · Apps and focus controls", 16));
        LinearLayout actions = new LinearLayout(this);
        Button appearance = Pages.button(this, "Settings", new View.OnClickListener() {
            @Override public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        actions.addView(appearance);
        actions.addView(Pages.button(this, "Help", new View.OnClickListener() {
            @Override public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, HelpActivity.class));
            }
        }));
        body.addView(actions);
        ScrollView scroll = new ScrollView(this);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(5);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(Pages.dp(this, 12), Pages.dp(this, 12), Pages.dp(this, 12), Pages.dp(this, 12));
        scroll.addView(grid);
        body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        grid.addView(Pages.text(this, "Loading apps…", 18));
        loadApps(grid);
        appearance.requestFocus();
    }

    private void loadApps(final GridLayout grid) {
        final PackageManager packages = getApplicationContext().getPackageManager();
        final String ownPackage = getPackageName();
        loader.execute(new Runnable() {
            @Override public void run() {
                final List<App> apps = new ArrayList<>();
                try {
                    Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
                    for (ResolveInfo info : packages.queryIntentActivities(query, 0)) {
                        if (Thread.currentThread().isInterrupted()) return;
                        if (ownPackage.equals(info.activityInfo.packageName)) continue;
                        try {
                            apps.add(new App(info.loadLabel(packages).toString(), info.loadIcon(packages),
                                    new ComponentName(info.activityInfo.packageName, info.activityInfo.name)));
                        } catch (RuntimeException removed) {
                            // An app may disappear while its metadata is being loaded.
                        }
                    }
                    Collections.sort(apps, new Comparator<App>() {
                        @Override public int compare(App left, App right) {
                            int label = left.label.compareToIgnoreCase(right.label);
                            return label == 0 ? left.component.flattenToString()
                                    .compareTo(right.component.flattenToString()) : label;
                        }
                    });
                } catch (RuntimeException unavailable) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (isDestroyed()) return;
                            grid.removeAllViews();
                            grid.addView(Pages.text(MainActivity.this, "Installed apps could not be read.", 18));
                        }
                    });
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (!isDestroyed()) showApps(grid, apps);
                    }
                });
            }
        });
    }

    private void showApps(GridLayout grid, List<App> apps) {
        grid.removeAllViews();
        int width = getResources().getDisplayMetrics().widthPixels;
        int tileWidth = Math.max(Pages.dp(this, 80), (width - Pages.dp(this, 88)) / 5 - Pages.dp(this, 20));
        for (App app : apps) {
            FocusTileView tile = new FocusTileView(this);
            tile.setTitle(app.label);
            tile.setIcon(app.icon);
            final ComponentName component = app.component;
            tile.setTag(component.flattenToString());
            tile.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    try {
                        startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                                .setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (RuntimeException unavailable) {
                        Toast.makeText(MainActivity.this, "This app is currently unavailable.", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = tileWidth;
            params.height = Pages.dp(this, 120);
            int margin = Pages.dp(this, 10);
            params.setMargins(margin, margin, margin, margin);
            grid.addView(tile, params);
            tiles.add(tile);
        }
        if (tiles.isEmpty()) grid.addView(Pages.text(this, "No TV apps are installed on this review device.", 18));
    }

    private static final class App {
        final String label;
        final Drawable icon;
        final ComponentName component;
        App(String label, Drawable icon, ComponentName component) {
            this.label = label;
            this.icon = icon;
            this.component = component;
        }
    }

    @Override protected void onDestroy() {
        loader.shutdownNow();
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        for (FocusTileView tile : tiles) tile.refreshPreferences();
    }
}
