package com.jeremykenedy.firetv.ui.nativeapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppGridOptions;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppGridPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppInventory;
import com.jeremykenedy.firetv.ui.nativeapp.focus.FocusTileView;
import com.jeremykenedy.firetv.ui.nativeapp.icons.IconPickerActivity;
import com.jeremykenedy.firetv.ui.nativeapp.icons.IconSelectionStore;
import com.jeremykenedy.firetv.ui.nativeapp.layout.HomeSlots;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.widget.ClockWidgetView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Independent review shell. It neither selects Home nor imports production settings. */
public final class MainActivity extends Activity {
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final List<FocusTileView> tiles = new ArrayList<>();
    private LinearLayout appArea;
    private Button settings;
    private LinearLayout homeSlots;
    private List<AppInventory.App> apps = new ArrayList<>();
    private String focusedApp;
    private int generation;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null) focusedApp = saved.getString("focused_app");
        LinearLayout body = Pages.page(this);
        body.addView(Pages.text(this, "Fire TV UI", 30));
        body.addView(Pages.text(this, "Internal source preview", 16));
        LinearLayout actions = new LinearLayout(this);
        settings = Pages.button(this, "Settings", new View.OnClickListener() {
            @Override public void onClick(View view) { startActivity(new Intent(MainActivity.this, SettingsActivity.class)); }
        });
        settings.setId(0x1001);
        actions.addView(settings);
        Button help = Pages.button(this, "Help", new View.OnClickListener() {
            @Override public void onClick(View view) { startActivity(new Intent(MainActivity.this, HelpActivity.class)); }
        });
        help.setId(0x1002); actions.addView(help); body.addView(actions);
        homeSlots = new LinearLayout(this); body.addView(homeSlots);
        appArea = new LinearLayout(this); appArea.setOrientation(LinearLayout.VERTICAL);
        body.addView(appArea, new LinearLayout.LayoutParams(-1, 0, 1));
        appArea.addView(Pages.text(this, "Loading apps…", 18));
        settings.requestFocus();
    }

    private void loadApps() {
        final int request = ++generation;
        loader.execute(new Runnable() {
            @Override public void run() {
                try {
                    final List<AppInventory.App> loaded = AppInventory.load(getApplicationContext());
                    final Map<String, Drawable> artwork = new HashMap<>();
                    for (AppInventory.App app : loaded) {
                        artwork.put(app.key(), IconSelectionStore.resolve(MainActivity.this, app.key(), app.artwork, null));
                    }
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (isDestroyed() || request != generation) return;
                            apps = loaded;
                            AppGridPreferences.save(MainActivity.this, AppGridPreferences.load(MainActivity.this).discover(AppInventory.keys(apps)));
                            showApps(artwork);
                        }
                    });
                } catch (RuntimeException unavailable) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (isDestroyed() || request != generation) return;
                            appArea.removeAllViews();
                            appArea.addView(Pages.text(MainActivity.this, "Installed apps could not be read. Reopen Home to try again.", 18));
                        }
                    });
                }
            }
        });
    }

    private void showApps(Map<String, Drawable> artwork) {
        View oldFocus = getCurrentFocus();
        boolean restoreTile = oldFocus instanceof FocusTileView || focusedApp != null;
        if (oldFocus instanceof FocusTileView) focusedApp = String.valueOf(oldFocus.getTag());
        appArea.removeAllViews(); tiles.clear();
        AppGridOptions options = AppGridPreferences.load(this);
        GridLayout grid = new GridLayout(this);
        grid.setClipChildren(false); grid.setClipToPadding(false);
        int width = getResources().getDisplayMetrics().widthPixels - Pages.dp(this, 64);
        int tileWidth = Math.max(Pages.dp(this, 48), width / options.columns);
        int tileHeight = Math.max(Pages.dp(this, 96), Math.round(tileWidth * 9f / 16f) + Pages.dp(this, 46));
        if (options.horizontal) {
            int availableHeight = Math.max(tileHeight, appArea.getHeight());
            int rows = options.rows == 0 ? Math.max(1, availableHeight / tileHeight) : options.rows;
            grid.setOrientation(GridLayout.VERTICAL); grid.setRowCount(rows);
            HorizontalScrollView scroll = new HorizontalScrollView(this);
            scroll.setClipChildren(false); scroll.setClipToPadding(false);
            scroll.addView(grid);
            if (rows * tileHeight > availableHeight) {
                ScrollView vertical = new ScrollView(this); vertical.addView(scroll); appArea.addView(vertical);
            } else appArea.addView(scroll);
        } else {
            grid.setColumnCount(options.columns);
            ScrollView scroll = new ScrollView(this); scroll.setClipChildren(false); scroll.setClipToPadding(false);
            scroll.addView(grid); appArea.addView(scroll);
        }
        Map<String, AppInventory.App> byKey = new HashMap<>();
        for (AppInventory.App app : apps) byKey.put(app.key(), app);
        FocusTileView restore = null;
        for (String key : options.visible(AppInventory.keys(apps))) {
            final AppInventory.App app = byKey.get(key);
            final FocusTileView tile = new FocusTileView(this);
            tile.setTitle(app.label); tile.setIcon(artwork.get(key)); tile.setTag(key);
            tile.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    focusedApp = app.key();
                    try {
                        startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                                .setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (RuntimeException unavailable) {
                        Toast.makeText(MainActivity.this, "This app is currently unavailable.", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            tile.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View view) { focusedApp = app.key(); appActions(app); return true; }
            });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = tileWidth; params.height = tileHeight;
            grid.addView(tile, params); tiles.add(tile);
            if (key.equals(focusedApp)) restore = tile;
        }
        if (tiles.isEmpty()) grid.addView(Pages.text(this, "No visible apps. Show apps in Settings → Home layout → Apps grid.", 18));
        if (restoreTile) {
            if (restore != null) restore.requestFocus();
            else if (!tiles.isEmpty()) { tiles.get(0).requestFocus(); focusedApp = String.valueOf(tiles.get(0).getTag()); }
            else { settings.requestFocus(); focusedApp = null; }
        }
    }

    private void appActions(final AppInventory.App app) {
        final AppGridOptions options = AppGridPreferences.load(this);
        AlertDialog actions = new AlertDialog.Builder(this).setTitle(app.label).setItems(new String[] {
            "Change icon", options.favorites.contains(app.key()) ? "Remove favorite" : "Add favorite",
            "Move earlier", "Move later", "Hide from Home"
        }, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (which == 0) { IconPickerActivity.open(MainActivity.this, app.key(), app.label); return; }
                AppGridOptions current = AppGridPreferences.load(MainActivity.this);
                if (which == 1) current = current.toggleFavorite(app.key());
                else if (which == 4) current = current.toggleHidden(app.key());
                else current = current.move(app.key(), which == 2 ? -1 : 1, AppInventory.keys(apps));
                AppGridPreferences.save(MainActivity.this, current); loadApps();
            }
        }).setNegativeButton("Cancel", null).show();
        actions.getListView().requestFocus();
        actions.getListView().setSelection(0);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        View focus = getCurrentFocus();
        if (focus instanceof FocusTileView) state.putString("focused_app", String.valueOf(focus.getTag()));
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() { loader.shutdownNow(); super.onDestroy(); }
    @Override protected void onResume() {
        super.onResume();
        View focus = getCurrentFocus();
        if (!(focus instanceof FocusTileView) && focus != null && (focus.getId() == 0x1001 || focus.getId() == 0x1002)) focusedApp = null;
        homeSlots.removeAllViews();
        for (LayoutItem item : LayoutPreferences.load(this, HomeSlots.defaults()).discover(HomeSlots.available()).visible(HomeSlots.available())) {
            if (item.equals(HomeSlots.CLOCK)) homeSlots.addView(new ClockWidgetView(this), new LinearLayout.LayoutParams(Pages.dp(this, 320), Pages.dp(this, 160)));
        }
        loadApps();
    }
}
