package com.jeremykenedy.firetv.ui.nativeapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.PathShape;
import android.graphics.Path;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.cards.CardOptions;
import com.jeremykenedy.firetv.ui.nativeapp.cards.CardPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.cards.CardsSettingsActivity;
import com.jeremykenedy.firetv.ui.nativeapp.cards.ChannelCardView;
import com.jeremykenedy.firetv.ui.nativeapp.cards.HomeContentView;
import com.jeremykenedy.firetv.ui.nativeapp.providers.RecommendationLoader;
import com.jeremykenedy.firetv.ui.nativeapp.appearance.BackgroundLayer;
import com.jeremykenedy.firetv.ui.nativeapp.locks.LauncherLocks;
import com.jeremykenedy.firetv.ui.nativeapp.folders.FolderActivity;
import com.jeremykenedy.firetv.ui.nativeapp.folders.FolderOptions;
import com.jeremykenedy.firetv.ui.nativeapp.folders.FolderPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.inputs.InputEntry;
import com.jeremykenedy.firetv.ui.nativeapp.inputs.InputRepository;
import com.jeremykenedy.firetv.ui.nativeapp.inputs.InputViews;
import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;
import com.jeremykenedy.firetv.ui.nativeapp.providers.RecommendationActions;
import com.jeremykenedy.firetv.ui.nativeapp.device.DeviceRoutes;
import com.jeremykenedy.firetv.ui.nativeapp.device.protection.LauncherAwakeController;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppGridOptions;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppGridPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppInventory;
import com.jeremykenedy.firetv.ui.nativeapp.focus.FocusTileView;
import com.jeremykenedy.firetv.ui.nativeapp.icons.IconPickerActivity;
import com.jeremykenedy.firetv.ui.nativeapp.icons.IconSelectionStore;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.HomeSlots;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutPreferences;
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
    private HomeContentView homeContent;
    private ScrollView slotViewport;
    private BackgroundLayer background;
    private LauncherAwakeController awake;
    private RecommendationLoader recommendations;
    private long feedRequest;
    private TextView cardStatus;
    private boolean resumed;
    private List<AppInventory.App> apps = new ArrayList<>();
    private String focusedApp;
    private boolean restoringGridFocus;
    private int generation;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null) focusedApp = saved.getString("focused_app");
        restoringGridFocus = focusedApp != null;
        LinearLayout body = Pages.page(this);
        body.addView(Pages.text(this, "Fire TV UI Preview", 30));
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
        View.OnFocusChangeListener headerFocus = new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean focused) {
                if (focused && resumed) { focusedApp = null; restoringGridFocus = false; }
            }
        };
        settings.setOnFocusChangeListener(headerFocus); help.setOnFocusChangeListener(headerFocus);
        cardStatus = Pages.text(this, "Checking channel cards…", 14);
        cardStatus.setTag("home:card-status");
        cardStatus.setMaxLines(2);
        cardStatus.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cardStatus.setPadding(Pages.dp(this, 16), 0, 0, 0);
        LinearLayout.LayoutParams statusLayout = new LinearLayout.LayoutParams(0, -2, 1);
        statusLayout.gravity = android.view.Gravity.CENTER_VERTICAL;
        actions.addView(cardStatus, statusLayout);
        homeContent = new HomeContentView(this, new ChannelCardView.Listener() {
            @Override public void onSelect(TvRecommendations.Feed feed, TvRecommendations.Program program) { cardActions(feed, program); }
            @Override public void onLongPress(TvRecommendations.Feed feed, TvRecommendations.Program program) { cardActions(feed, program); }
        }, new HomeContentView.Listener() {
            @Override public void onSlotFocused(String key) { focusedApp = null; }
            @Override public void onUnavailable(LayoutItem item) {
                startActivity(new Intent(MainActivity.this, item.kind == LayoutItem.Kind.CHANNEL_CARD
                        ? CardsSettingsActivity.class : com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutSettingsActivity.class));
            }
        });
        homeContent.restoreState(saved);
        slotViewport = new ScrollView(this);
        slotViewport.setClipChildren(false); slotViewport.setClipToPadding(false);
        slotViewport.addView(homeContent);
        body.addView(slotViewport, new LinearLayout.LayoutParams(-1, Pages.dp(this, 190)));
        if (saved != null) {
            final int scrollY = saved.getInt("slot_scroll_y", 0);
            slotViewport.post(new Runnable() { @Override public void run() { slotViewport.scrollTo(0, scrollY); } });
        }
        recommendations = new RecommendationLoader(this, new RecommendationLoader.Listener() {
            @Override public void onRecommendations(TvRecommendations.Result result) {
                if (!homeContent.applyResult(feedRequest, result)) return;
                int omitted = HomeSlots.omittedCount(LayoutPreferences.load(MainActivity.this, HomeSlots.defaults()), result.feeds);
                String capacity = omitted == 0 ? "" : "Home layout limit reached: " + omitted + " new items were not added. ";
                cardStatus.setText(capacity + result.message()
                        + (result.status == TvRecommendations.Status.PERMISSION_REQUIRED ? " Open Settings, Cards to allow access." : ""));
            }
        });
        appArea = new LinearLayout(this); appArea.setOrientation(LinearLayout.VERTICAL);
        body.addView(appArea, new LinearLayout.LayoutParams(-1, 0, 1));
        appArea.addView(Pages.text(this, "Loading apps…", 18));
        settings.requestFocus();
        background = BackgroundLayer.attach(this, body);
        awake = new LauncherAwakeController(body);
    }

    private void loadApps() {
        final int request = ++generation;
        final List<AppInventory.App> previous = new ArrayList<>(apps);
        loader.execute(new Runnable() {
            @Override public void run() {
                final List<String> messages = new ArrayList<>();
                List<AppInventory.App> inventory;
                boolean readApps = true;
                try { inventory = AppInventory.load(getApplicationContext()); }
                catch (RuntimeException unavailable) {
                    inventory = previous;
                    readApps = false;
                    messages.add("Installed apps could not be refreshed. Reopen Home to try again.");
                }
                final List<AppInventory.App> loaded = inventory;
                final boolean appsRead = readApps;
                final Map<String, Drawable> artwork = new HashMap<>();
                boolean artworkFailed = false;
                for (AppInventory.App app : loaded) {
                    try { artwork.put(app.key(), IconSelectionStore.resolve(getApplicationContext(), app.key(), app.artwork, null)); }
                    catch (RuntimeException unavailable) { artwork.put(app.key(), app.artwork); artworkFailed = true; }
                }
                if (artworkFailed) messages.add("Some app artwork could not be loaded. Factory artwork is shown where available.");
                FolderOptions foundFolders;
                try { foundFolders = FolderPreferences.load(getApplicationContext()); }
                catch (RuntimeException unavailable) {
                    foundFolders = FolderOptions.empty();
                    messages.add("Saved folders could not be read. Their stored data has been kept.");
                }
                final FolderOptions folders = foundFolders;
                for (FolderOptions.Folder folder : folders.folders) artwork.put("folder:" + folder.id, folderArtwork());
                final List<InputEntry> inputs = new ArrayList<>();
                try {
                    InputRepository.Snapshot found = InputRepository.load(getApplicationContext());
                    inputs.addAll(found.inputs);
                    for (InputEntry input : inputs) artwork.put(input.artworkKey(), InputViews.artwork(getApplicationContext(), input));
                } catch (RuntimeException unavailable) { messages.add("TV inputs could not be refreshed. Saved choices are kept."); }
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (isDestroyed() || !resumed || request != generation) return;
                        apps = loaded;
                        if (appsRead) {
                            try { AppGridPreferences.saveDiscoveredOrder(MainActivity.this, AppInventory.keys(apps)); }
                            catch (RuntimeException failed) { messages.add("App order could not be saved. Existing settings are unchanged."); }
                        }
                        showApps(artwork, folders, inputs, messages);
                    }
                });
            }
        });
    }

    private static Drawable folderArtwork() {
        Path path = new Path();
        path.moveTo(8, 12); path.lineTo(48, 12); path.lineTo(60, 26); path.lineTo(120, 26);
        path.quadTo(128, 26, 128, 34); path.lineTo(128, 88); path.quadTo(128, 96, 120, 96);
        path.lineTo(8, 96); path.quadTo(0, 96, 0, 88); path.lineTo(0, 20); path.quadTo(0, 12, 8, 12);
        path.close();
        ShapeDrawable folder = new ShapeDrawable(new PathShape(path, 128, 108));
        folder.getPaint().setColor(0xffb49ae4);
        folder.setIntrinsicWidth(128); folder.setIntrinsicHeight(108);
        return folder;
    }

    private void showApps(Map<String, Drawable> artwork, FolderOptions folders,
            List<InputEntry> inputs, List<String> messages) {
        View oldFocus = getCurrentFocus();
        boolean restoreTile = oldFocus instanceof FocusTileView || focusedApp != null;
        if (oldFocus instanceof FocusTileView) focusedApp = String.valueOf(oldFocus.getTag());
        appArea.removeAllViews(); tiles.clear();
        AppGridOptions options = AppGridPreferences.load(this);
        if (options.unrememberedCount(AppInventory.keys(apps)) > 0)
            messages.add("App order limit reached. New apps remain usable, but their order cannot be saved.");
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
                    LauncherLocks.guard(MainActivity.this, app.key(), new Runnable() {
                        @Override public void run() {
                            try {
                                startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                                        .setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            } catch (RuntimeException unavailable) {
                                Toast.makeText(MainActivity.this, "This app is currently unavailable.", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
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
        for (final FolderOptions.Folder folder : folders.folders) {
            final String key = "folder:" + folder.id;
            FocusTileView tile = new FocusTileView(this);
            tile.setTitle(folder.name); tile.setIcon(artwork.get(key)); tile.setTag(key);
            tile.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    focusedApp = key;
                    // FolderActivity guards its direct-open boundary, so a locked folder prompts once.
                    startActivity(FolderActivity.intent(MainActivity.this, folder.id));
                }
            });
            tile.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View view) { focusedApp = key; folderActions(folder); return true; }
            });
            addGridTile(grid, tile, tileWidth, tileHeight);
            if (key.equals(focusedApp)) restore = tile;
        }
        for (final InputEntry input : inputs) {
            final String key = input.artworkKey();
            FocusTileView tile = InputViews.tile(this, input, artwork.get(key), new Runnable() {
                @Override public void run() { focusedApp = key; loadApps(); }
            });
            tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View view, boolean focused) { if (focused) focusedApp = key; }
            });
            addGridTile(grid, tile, tileWidth, tileHeight);
            if (key.equals(focusedApp)) restore = tile;
        }
        for (String message : messages) grid.addView(Pages.text(this, message, 16));
        if (tiles.isEmpty()) grid.addView(Pages.text(this, "No visible apps. Show apps in Settings → Home layout → Apps grid.", 18));
        if (restoreTile) {
            if (restore != null) restore.requestFocus();
            else if (!tiles.isEmpty()) { tiles.get(0).requestFocus(); focusedApp = String.valueOf(tiles.get(0).getTag()); }
            else { settings.requestFocus(); focusedApp = null; }
        }
        restoringGridFocus = false;
    }

    private void addGridTile(GridLayout grid, FocusTileView tile, int width, int height) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = width; params.height = height;
        grid.addView(tile, params); tiles.add(tile);
    }

    private void folderActions(final FolderOptions.Folder folder) {
        final String key = "folder:" + folder.id;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(folder.name)
                .setItems(new String[]{"Open folder", "Manage folders", "Lock / unlock"}, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface ignored, int which) {
                        if (which == 0) startActivity(FolderActivity.intent(MainActivity.this, folder.id));
                        else if (which == 1) startActivity(new Intent(MainActivity.this, FolderActivity.class));
                        else LauncherLocks.showControls(MainActivity.this, key, folder.name, null);
                    }
                }).setNegativeButton("Cancel", null).show();
        dialog.getListView().requestFocus(); dialog.getListView().setSelection(0);
    }

    private void appActions(final AppInventory.App app) {
        final AppGridOptions options = AppGridPreferences.load(this);
        AlertDialog actions = new AlertDialog.Builder(this).setTitle(app.label).setItems(new String[] {
            "Change icon", options.favorites.contains(app.key()) ? "Remove favorite" : "Add favorite",
            "Move earlier", "Move later", "Hide from Home", "Lock / unlock"
        }, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (which == 0) { IconPickerActivity.open(MainActivity.this, app.key(), app.label); return; }
                if (which == 5) {
                    LauncherLocks.showControls(MainActivity.this, app.key(), app.label, null); return;
                }
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

    private void configureCard(ChannelCardView card, CardOptions options) {
        card.setPrograms(CardPreferences.visiblePrograms(options, card.feed()));
        card.setRotationIntervalMillis(options.timerSeconds(card.feed().item.id) * 1000L);
    }

    private void cardActions(final TvRecommendations.Feed feed, final TvRecommendations.Program program) {
        final ChannelCardView card = findCard(feed.item.key());
        if (card == null) return;
        card.onHostPause();
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        if (program != null) {
            RecommendationActions.Availability links = RecommendationActions.inspect(this, program);
            if (links.details) {
                labels.add(links.detailsLabel()); actions.add(new Runnable() {
                    @Override public void run() { launchResult(RecommendationActions.launchDetails(MainActivity.this, program)); }
                });
            }
            if (links.play) {
                labels.add(links.playLabel()); actions.add(new Runnable() {
                    @Override public void run() { launchResult(RecommendationActions.launchPlay(MainActivity.this, program)); }
                });
            }
            if (links.title && (!links.details || !java.util.Objects.equals(program.intentUri, program.detailsIntentUri))) {
                labels.add(links.titleLabel()); actions.add(new Runnable() {
                    @Override public void run() { launchResult(RecommendationActions.launchTitle(MainActivity.this, program)); }
                });
            }
            if (links.app) {
                labels.add(links.appLabel()); actions.add(new Runnable() {
                    @Override public void run() { launchResult(RecommendationActions.openApp(MainActivity.this, program)); }
                });
            }
            labels.add("Next title"); actions.add(new Runnable() { @Override public void run() { card.showNextProgram(); } });
            labels.add("Hide this item"); actions.add(new Runnable() {
                @Override public void run() {
                    try {
                        CardOptions updated = CardPreferences.load(MainActivity.this).hide(program.source, program.key, program.title);
                        CardPreferences.save(MainActivity.this, updated); configureCard(card, updated);
                    } catch (RuntimeException failed) { Toast.makeText(MainActivity.this, "This item could not be hidden. Please try again.", Toast.LENGTH_SHORT).show(); }
                }
            });
            if (program.seriesKey != null && !program.seriesKey.isEmpty()) {
                labels.add("Hide series"); actions.add(new Runnable() {
                    @Override public void run() {
                        try {
                            String label = program.seriesTitle == null || program.seriesTitle.isEmpty()
                                    ? "Series containing " + program.title : program.seriesTitle;
                            if (label.length() > 512) label = label.substring(0, 512);
                            CardOptions updated = CardPreferences.load(MainActivity.this).hideSeries(program.source, program.seriesKey, label);
                            CardPreferences.save(MainActivity.this, updated); homeContent.refreshPreferences();
                        } catch (RuntimeException failed) { Toast.makeText(MainActivity.this, "This series could not be hidden. Please try again.", Toast.LENGTH_SHORT).show(); }
                    }
                });
            }
        }
        labels.add("Card settings"); actions.add(new Runnable() {
            @Override public void run() { startActivity(new Intent(MainActivity.this, CardsSettingsActivity.class)); }
        });
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(program == null ? feed.title : program.title)
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { actions.get(which).run(); }
                }).setNegativeButton("Cancel", null).create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface ignored) { if (resumed) card.onHostResume(); }
        });
        dialog.show(); dialog.getListView().requestFocus(); dialog.getListView().setSelection(0);
    }

    private ChannelCardView findCard(String key) {
        return homeContent.findCard(key);
    }

    private void launchResult(RecommendationActions.Outcome result) {
        if (result != RecommendationActions.Outcome.OPENED && result != RecommendationActions.Outcome.PENDING)
            Toast.makeText(this, "This action is unavailable. Try opening the source app.", Toast.LENGTH_SHORT).show();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        View focus = getCurrentFocus();
        if (focus instanceof FocusTileView) state.putString("focused_app", String.valueOf(focus.getTag()));
        homeContent.saveState(state);
        state.putInt("slot_scroll_y", slotViewport.getScrollY());
        super.onSaveInstanceState(state);
    }
    @Override protected void onPause() {
        DeviceRoutes.launcherVisibility(this, false);
        resumed = false;
        generation++;
        awake.onPause();
        recommendations.stop(); homeContent.onHostPause(); background.setResumed(false);
        super.onPause();
    }
    @Override protected void onDestroy() { awake.onPause(); recommendations.close(); loader.shutdownNow(); super.onDestroy(); }
    @Override protected void onResume() {
        super.onResume();
        DeviceRoutes.launcherVisibility(this, true);
        resumed = true;
        View focus = getCurrentFocus();
        if (!restoringGridFocus && !(focus instanceof FocusTileView) && focus != null
                && (focus.getId() == 0x1001 || focus.getId() == 0x1002)) focusedApp = null;
        awake.onResume(this);
        background.setResumed(true); homeContent.onHostResume();
        feedRequest = homeContent.beginRefresh(); recommendations.start();
        loadApps();
    }
}
