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
import android.view.KeyEvent;
import com.jeremykenedy.firetv.ui.nativeapp.statusbar.StatusBarOptions;
import com.jeremykenedy.firetv.ui.nativeapp.statusbar.StatusBarQuickPanel;
import com.jeremykenedy.firetv.ui.nativeapp.statusbar.StatusBarView;
import com.jeremykenedy.firetv.ui.nativeapp.accessibility.AccessibilityStyle;
import com.jeremykenedy.firetv.ui.nativeapp.device.remotebuttons.RemoteButtonDispatcher;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.HomeTileOrder;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.HomeTilePreferences;
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
import com.jeremykenedy.firetv.ui.nativeapp.allapps.AllAppsActivity;
import com.jeremykenedy.firetv.ui.nativeapp.allapps.AllAppsPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.focus.FocusTileView;
import com.jeremykenedy.firetv.ui.nativeapp.icons.IconPickerActivity;
import com.jeremykenedy.firetv.ui.nativeapp.icons.IconSelectionStore;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.HomeSlots;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.layoutschedule.LayoutSchedule;
import com.jeremykenedy.firetv.ui.nativeapp.layoutschedule.LayoutScheduleController;
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
    private LinearLayout statusBarArea;
    private String statusBarState;
    private Button settings;
    private Button allApps;
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
    private RemoteButtonDispatcher remoteButtons;
    private LayoutScheduleController dashboard;
    private boolean homeReady;
    private boolean activityResumed;
    private boolean contentStarted;
    private Bundle startupState;
    private final android.os.Handler startupHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable startupTimeout = new Runnable() {
        @Override public void run() { if (!homeReady && !isFinishing() && !isDestroyed()) showStartup(true); }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        startupState = saved;
        if (SourceStartup.ready(this)) { startHome(); return; }
        showStartup(false);
        beginStartup();
    }

    private void beginStartup() {
        showStartup(false);
        startupHandler.removeCallbacks(startupTimeout);
        startupHandler.postDelayed(startupTimeout, 15000);
        SourceStartup.start(this, new Runnable() {
            @Override public void run() {
                startupHandler.removeCallbacks(startupTimeout);
                if (isFinishing() || isDestroyed() || homeReady) return;
                if (!SourceStartup.ready(MainActivity.this)) { showStartup(true); return; }
                startHome();
                if (activityResumed) resumeHome();
            }
        });
    }

    private void showStartup(boolean failed) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(48, 48, 48, 48);
        body.setBackgroundColor(0xff101523);
        TextView message = new TextView(this);
        message.setTextColor(0xffffffff);
        message.setTextSize(22);
        String error = SourceStartup.error(this);
        message.setText(!failed ? "Preparing your saved settings..." : error == null
                ? "Preparing your settings is taking longer than expected. You can retry or open device Home."
                : "Your saved settings could not be prepared. They have been kept.\n" + error);
        body.addView(message);
        if (failed) {
            Button retry = new Button(this);
            retry.setText("Retry");
            retry.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { beginStartup(); }
            });
            body.addView(retry);
            Button exit = new Button(this);
            exit.setText("Open device Home");
            exit.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    Intent factory = DeviceRoutes.resolve(MainActivity.this, DeviceRoutes.Destination.FACTORY_UI);
                    try { if (factory != null) startActivity(factory); else Toast.makeText(MainActivity.this,
                            "Device Home is unavailable.", Toast.LENGTH_LONG).show(); }
                    catch (RuntimeException unavailable) { Toast.makeText(MainActivity.this,
                            "Device Home is unavailable.", Toast.LENGTH_LONG).show(); }
                }
            });
            body.addView(exit);
            retry.requestFocus();
        }
        setContentView(body);
    }

    private void startHome() {
        if (homeReady) return;
        homeReady = true;
        Bundle saved = startupState;
        startupState = null;
        remoteButtons = new RemoteButtonDispatcher(this);
        if (saved != null) focusedApp = saved.getString("focused_app");
        restoringGridFocus = focusedApp != null;
        LinearLayout body = Pages.page(this);
        statusBarArea = new LinearLayout(this);
        statusBarArea.setTag("home:statusbar-container");
        body.addView(statusBarArea, new LinearLayout.LayoutParams(-1, -2));
        refreshStatusBar();
        body.addView(Pages.text(this, "com.jeremykenedy.firetv.ui".equals(getPackageName())
                ? getApplicationInfo().loadLabel(getPackageManager()).toString() : "Fire TV UI Preview", 30));
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
        allApps = Pages.button(this, "All apps", new View.OnClickListener() {
            @Override public void onClick(View view) { startActivity(AllAppsActivity.intent(MainActivity.this)); }
        });
        allApps.setId(0x1003); allApps.setTag("home:all-apps");
        allApps.setVisibility(AllAppsPreferences.enabled(this) ? View.VISIBLE : View.GONE);
        actions.addView(allApps);
        View.OnFocusChangeListener headerFocus = new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean focused) {
                if (focused && resumed) { focusedApp = null; restoringGridFocus = false; }
            }
        };
        settings.setOnFocusChangeListener(headerFocus); help.setOnFocusChangeListener(headerFocus);
        allApps.setOnFocusChangeListener(headerFocus);
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
        dashboard = new LayoutScheduleController(this, new LayoutScheduleController.Listener() {
            @Override public void onDashboard(LayoutSchedule.Decision decision) {
                if (decision.status == LayoutSchedule.Status.READY) homeContent.setDashboardLayout(decision.profile.layout);
                else if (decision.status == LayoutSchedule.Status.BASE_LAYOUT) homeContent.setDashboardLayout(null);
                else Toast.makeText(MainActivity.this, "Dashboard settings need attention. Open Settings, Home layout, Dashboards and schedules.", Toast.LENGTH_LONG).show();
                startHomeContent();
            }
            @Override public void onDashboardUnavailable() {
                Toast.makeText(MainActivity.this, "Dashboard settings could not be read. Open Settings, Home layout, Dashboards and schedules to retry.", Toast.LENGTH_LONG).show();
                startHomeContent();
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
                AccessibilityStyle.apply(MainActivity.this, getWindow().getDecorView());
                int omitted = HomeSlots.omittedCount(LayoutPreferences.load(MainActivity.this, HomeSlots.defaults()), result.feeds);
                String capacity = omitted == 0 ? "" : "Home layout limit reached: " + omitted + " new items were not added. ";
                cardStatus.setText(capacity + result.message()
                        + (result.status == TvRecommendations.Status.PERMISSION_REQUIRED ? " Open Settings, Cards to allow access." : ""));
            }
        });
        appArea = new LinearLayout(this); appArea.setOrientation(LinearLayout.VERTICAL);
        body.addView(appArea, new LinearLayout.LayoutParams(-1, 0, 1));
        appArea.addView(Pages.text(this, "Loading apps…", 18));
        View savedHeader = saved == null ? null : findViewById(saved.getInt("focused_header", 0));
        if (savedHeader != null && savedHeader.getVisibility() == View.VISIBLE) savedHeader.requestFocus();
        else settings.requestFocus();
        background = BackgroundLayer.attach(this, body);
        awake = new LauncherAwakeController(body);
    }

    private void refreshStatusBar() {
        StatusBarOptions options = StatusBarOptions.load(this);
        String state = options.configured + ":" + options.minimal + ":" + options.position
                + ":" + options.hideUnlessFocused + ":" + options.tinted;
        if (state.equals(statusBarState)) return;
        statusBarState = state;
        statusBarArea.removeAllViews();
        if (!options.configured) {
            statusBarArea.setVisibility(View.GONE);
            return;
        }
        statusBarArea.setVisibility(View.VISIBLE);
        StatusBarView row = new StatusBarView(this, options,
                new Runnable() { @Override public void run() { openStatusDestination(DeviceRoutes.Destination.NETWORK_SETTINGS); } },
                new Runnable() { @Override public void run() { openStatusDestination(DeviceRoutes.Destination.SYSTEM_SETTINGS); } },
                new Runnable() { @Override public void run() { startActivity(new Intent(MainActivity.this, SettingsActivity.class)); } },
                new Runnable() { @Override public void run() { StatusBarQuickPanel.show(MainActivity.this); } });
        statusBarArea.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void openStatusDestination(DeviceRoutes.Destination destination) {
        if (DeviceRoutes.open(this, destination) != DeviceRoutes.Result.STARTED)
            Toast.makeText(this, "This device destination is unavailable.", Toast.LENGTH_LONG).show();
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
                    inputs.addAll(found.visibleInputs);
                    if (found.capability == InputRepository.Capability.UNAVAILABLE) messages.add(found.message);
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
        Map<String, FolderOptions.Folder> foldersByKey = new HashMap<>();
        for (FolderOptions.Folder folder : folders.folders) foldersByKey.put("folder:" + folder.id, folder);
        Map<String, InputEntry> inputsByKey = new HashMap<>();
        for (InputEntry input : inputs) inputsByKey.put(input.artworkKey(), input);
        List<String> visible;
        try {
            visible = HomeTilePreferences.load(this).visible(options, AppInventory.keys(apps), folders, inputs);
        } catch (RuntimeException unreadable) {
            messages.add("Saved Home order could not be read. Its stored data has been kept.");
            visible = new HomeTileOrder(java.util.Collections.<String>emptyList())
                    .visible(options, AppInventory.keys(apps), folders, inputs);
        }
        FocusTileView restore = null;
        for (final String key : visible) {
            final AppInventory.App app = byKey.get(key);
            final FolderOptions.Folder folder = foldersByKey.get(key);
            final InputEntry input = inputsByKey.get(key);
            FocusTileView tile;
            if (app != null) {
                tile = new FocusTileView(this);
                tile.setTitle(app.label); tile.setIcon(artwork.get(key)); tile.setTag(key);
                tile.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        focusedApp = app.key();
                        LauncherLocks.guard(MainActivity.this, app.key(), new Runnable() {
                            @Override public void run() {
                                try { startActivity(app.launchIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                                catch (RuntimeException unavailable) {
                                    Toast.makeText(MainActivity.this, "This app is currently unavailable.", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }
                });
                tile.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View view) { focusedApp = key; appActions(app); return true; }
                });
            } else if (folder != null) {
                tile = new FocusTileView(this);
                tile.setTitle(folder.name); tile.setIcon(artwork.get(key)); tile.setTag(key);
                tile.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        focusedApp = key;
                        startActivity(FolderActivity.intent(MainActivity.this, folder.id));
                    }
                });
                tile.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View view) { focusedApp = key; folderActions(folder); return true; }
                });
            } else if (input != null) {
                tile = InputViews.tile(this, input, artwork.get(key), new Runnable() {
                    @Override public void run() { focusedApp = key; loadApps(); }
                });
            } else continue;
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
        AccessibilityStyle.apply(this, getWindow().getDecorView());
    }

    private void addGridTile(GridLayout grid, FocusTileView tile, int width, int height) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = width; params.height = height;
        grid.addView(tile, params); tiles.add(tile);
    }

    private void folderActions(final FolderOptions.Folder folder) {
        final String key = "folder:" + folder.id;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(folder.name)
                .setItems(new String[]{"Open folder", "Manage folders", "Lock / unlock", "Move earlier", "Move later"}, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface ignored, int which) {
                        if (which == 0) startActivity(FolderActivity.intent(MainActivity.this, folder.id));
                        else if (which == 1) startActivity(new Intent(MainActivity.this, FolderActivity.class));
                        else if (which == 2) LauncherLocks.showControls(MainActivity.this, key, folder.name, null);
                        else moveHomeTile(key, which == 3 ? -1 : 1);
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
                if (which == 2 || which == 3) { moveHomeTile(app.key(), which == 2 ? -1 : 1); return; }
                AppGridOptions current = AppGridPreferences.load(MainActivity.this);
                if (which == 1) current = current.toggleFavorite(app.key());
                else if (which == 4) current = current.toggleHidden(app.key());
                AppGridPreferences.save(MainActivity.this, current); loadApps();
            }
        }).setNegativeButton("Cancel", null).show();
        actions.getListView().requestFocus();
        actions.getListView().setSelection(0);
    }

    private void moveHomeTile(String key, int delta) {
        try {
            com.jeremykenedy.firetv.ui.nativeapp.appgrid.HomeTileActions.move(this, key, delta);
            loadApps();
        } catch (RuntimeException failure) {
            Toast.makeText(this, failure.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        return remoteButtons != null && remoteButtons.dispatch(event, false) || super.dispatchKeyEvent(event);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus && remoteButtons != null) remoteButtons.clear();
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
        if (focus != null && (focus.getId() == 0x1001 || focus.getId() == 0x1002 || focus.getId() == 0x1003))
            state.putInt("focused_header", focus.getId());
        if (homeReady) {
            homeContent.saveState(state);
            state.putInt("slot_scroll_y", slotViewport.getScrollY());
        }
        super.onSaveInstanceState(state);
    }
    @Override protected void onPause() {
        activityResumed = false;
        if (!homeReady) { super.onPause(); return; }
        remoteButtons.clear();
        DeviceRoutes.launcherVisibility(this, false);
        resumed = false;
        generation++;
        awake.onPause();
        dashboard.onPause();
        if (contentStarted) { recommendations.stop(); homeContent.onHostPause(); contentStarted = false; }
        background.setResumed(false);
        super.onPause();
    }
    @Override protected void onDestroy() { startupHandler.removeCallbacks(startupTimeout); if (homeReady) { awake.onPause(); dashboard.close(); recommendations.close(); } loader.shutdownNow(); super.onDestroy(); }
    @Override protected void onResume() {
        super.onResume();
        activityResumed = true;
        if (homeReady) resumeHome();
    }
    private void resumeHome() {
        refreshStatusBar();
        DeviceRoutes.launcherVisibility(this, true);
        SourceStartup.startProtection(this, new Runnable() {
            @Override public void run() {
                String error = SourceStartup.protectionError(MainActivity.this);
                if (error != null && resumed && !isFinishing() && !isDestroyed()) Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
        resumed = true;
        AccessibilityStyle.apply(this, getWindow().getDecorView());
        boolean showAllApps = AllAppsPreferences.enabled(this);
        if (!showAllApps && allApps.isFocused()) settings.requestFocus();
        allApps.setVisibility(showAllApps ? View.VISIBLE : View.GONE);
        View focus = getCurrentFocus();
        if (!restoringGridFocus && !(focus instanceof FocusTileView) && focus != null
                && (focus.getId() == 0x1001 || focus.getId() == 0x1002 || focus.getId() == 0x1003)) focusedApp = null;
        awake.onResume(this);
        background.setResumed(true); dashboard.onResume();
        loadApps();
    }
    private void startHomeContent() {
        if (!resumed || contentStarted) return;
        contentStarted = true;
        homeContent.onHostResume();
        feedRequest = homeContent.beginRefresh(); recommendations.start();
    }
}
