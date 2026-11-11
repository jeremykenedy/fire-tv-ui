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
import com.jeremykenedy.firetv.ui.nativeapp.appearance.AppearanceOptions;
import com.jeremykenedy.firetv.ui.nativeapp.appearance.AppearancePreferences;
import com.jeremykenedy.firetv.ui.nativeapp.appearance.LegacyGradients;
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

/** Home for the independently maintained launcher. */
public final class MainActivity extends Activity {
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final List<FocusTileView> tiles = new ArrayList<>();
    private LinearLayout appArea;
    private LinearLayout homeBody;
    private boolean homeTextContrast;
    private final java.util.WeakHashMap<TextView, TextShadow> homeShadows = new java.util.WeakHashMap<>();
    private LinearLayout statusBarArea;
    private String statusBarState;
    private View settings;
    private Button allApps;
    private HomeContentView homeContent;
    private ScrollView slotViewport;
    private boolean groupedWidgetsVisible;
    private BackgroundLayer background;
    private ChannelCardView artworkOwner;
    private final java.util.List<View> appScrolls = new java.util.ArrayList<>();
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener appearanceChanged =
            new android.content.SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override public void onSharedPreferenceChanged(android.content.SharedPreferences preferences, String key) {
                    if (homeReady && SourceStartup.ready(MainActivity.this)) {
                        refreshHomeAppearance(); refreshStatusBar();
                    }
                }
            };
    private LauncherAwakeController awake;
    private RecommendationLoader recommendations;
    private long feedRequest;
    private boolean resumed;
    private List<AppInventory.App> apps = new ArrayList<>();
    private String focusedApp;
    private boolean restoringGridFocus;
    private boolean focusInitialApp;
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
        focusInitialApp = saved == null;
        LinearLayout body = Pages.page(this);
        homeBody = body;
        body.setClipChildren(false); body.setClipToPadding(false);
        body.setPadding(Pages.dp(this, 32), Pages.dp(this, 24), Pages.dp(this, 32), Pages.dp(this, 96));
        statusBarArea = new LinearLayout(this);
        statusBarState = null;
        statusBarArea.setTag("home:statusbar-container");
        body.addView(statusBarArea, new LinearLayout.LayoutParams(-1, Pages.dp(this, 44)));
        refreshStatusBar();
        allApps = Pages.button(this, "All apps", new View.OnClickListener() {
            @Override public void onClick(View view) { startActivity(AllAppsActivity.intent(MainActivity.this)); }
        });
        allApps.setId(0x1003); allApps.setTag("home:all-apps");
        allApps.setVisibility(AllAppsPreferences.enabled(this) ? View.VISIBLE : View.GONE);
        body.addView(allApps);
        homeContent = new HomeContentView(this, new ChannelCardView.Listener() {
            @Override public void onSelect(TvRecommendations.Feed feed, TvRecommendations.Program program) { cardActions(feed, program); }
            @Override public void onLongPress(TvRecommendations.Feed feed, TvRecommendations.Program program) { cardActions(feed, program); }
        }, new HomeContentView.Listener() {
            @Override public void onSlotFocused(String key) { focusedApp = null; }
            @Override public void onGroupedWidgetsVisibilityChanged(boolean visible) {
                groupedWidgetsVisible = visible;
                refreshHomeInsets();
            }
            @Override public void onArtworkChanged(ChannelCardView card, android.graphics.Bitmap artwork) {
                if (background == null) return;
                if (!com.jeremykenedy.firetv.ui.nativeapp.cards.CardDisplayPreferences.bigPicture(MainActivity.this)) {
                    artworkOwner = null; background.setFeaturedArtwork(null); return;
                }
                if (artwork != null) { artworkOwner = card; background.setFeaturedArtwork(artwork); }
                else if (artworkOwner == card) { artworkOwner = null; background.setFeaturedArtwork(null); }
            }
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
        body.addView(slotViewport, new LinearLayout.LayoutParams(-1, 0, 1));
        if (saved != null) {
            final int scrollY = saved.getInt("slot_scroll_y", 0);
            slotViewport.post(new Runnable() { @Override public void run() { slotViewport.scrollTo(0, scrollY); } });
        }
        recommendations = new RecommendationLoader(this, new RecommendationLoader.Listener() {
            @Override public void onRecommendations(TvRecommendations.Result result) {
                if (!homeContent.applyResult(feedRequest, result)) return;
                AccessibilityStyle.apply(MainActivity.this, getWindow().getDecorView());

            }
        });
        appArea = new LinearLayout(this); appArea.setOrientation(LinearLayout.VERTICAL);
        appArea.setClipChildren(false); appArea.setClipToPadding(false);
        appArea.setPadding(Pages.dp(this, 10), Pages.dp(this, 14), Pages.dp(this, 10), Pages.dp(this, 14));
        android.graphics.drawable.GradientDrawable tray = new android.graphics.drawable.GradientDrawable();
        tray.setColor(0x55000000);
        tray.setCornerRadius(Pages.dp(this, 22));
        tray.setStroke(Pages.dp(this, 1), 0x226d657e);
        appArea.setBackground(tray);
        appArea.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View view, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (background != null && (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom))
                    refreshHomeAppearance();
            }
        });
        body.addView(appArea, new LinearLayout.LayoutParams(-1, Pages.dp(this, 113)));
        appArea.addView(Pages.text(this, "Loading apps…", 18));
        View savedHeader = saved == null ? null : findViewById(saved.getInt("focused_header", 0));
        if (savedHeader != null && savedHeader.getVisibility() == View.VISIBLE) savedHeader.requestFocus();
        background = BackgroundLayer.attach(this, body);
        background.setContentChangedListener(new Runnable() {
            @Override public void run() { refreshHomeAppearance(); }
        });
        AppearancePreferences.file(this).registerOnSharedPreferenceChangeListener(appearanceChanged);
        awake = new LauncherAwakeController(body);
    }

    private void refreshHomeInsets() {
        int bottom = com.jeremykenedy.firetv.ui.nativeapp.appgrid.HomeVisualOptions.isDockBottom(this)
                ? 40 : groupedWidgetsVisible ? 24 : 96;
        homeBody.setPadding(Pages.dp(this, 32), Pages.dp(this, 24), Pages.dp(this, 32), Pages.dp(this, bottom));
        if (slotViewport != null) slotViewport.setFillViewport(groupedWidgetsVisible);
    }

    private void refreshStatusBar() {
        StatusBarOptions options = StatusBarOptions.load(this);
        String state = options.configured + ":" + options.minimal + ":" + options.position
                + ":" + options.hideUnlessFocused + ":" + options.tinted
                + ":" + AppearancePreferences.load(this).base;
        if (state.equals(statusBarState)) return;
        statusBarState = state;
        statusBarArea.removeAllViews();
        settings = null;
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
        statusBarArea.addView(row, new LinearLayout.LayoutParams(-1, -1));
        settings = row.findViewWithTag("statusbar:preferences");
        if (settings == null) settings = row.findViewWithTag("statusbar:clock");
        if (settings != null) settings.setId(0x1001);
    }

    private void openStatusDestination(DeviceRoutes.Destination destination) {
        if (DeviceRoutes.open(this, destination) != DeviceRoutes.Result.STARTED)
            Toast.makeText(this, "This device destination is unavailable.", Toast.LENGTH_LONG).show();
    }

    private void loadApps() {
        final int request = ++generation;
        final boolean mini = com.jeremykenedy.firetv.ui.nativeapp.appgrid.HomeVisualOptions.isMini(this);
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
                    try { artwork.put(app.key(), IconSelectionStore.resolve(getApplicationContext(), app.key(), mini ? com.jeremykenedy.firetv.ui.nativeapp.appgrid.MiniDockArtwork.load(getApplicationContext(), app.component, app.artwork) : app.artwork, null)); }
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
        appArea.removeAllViews(); tiles.clear(); appScrolls.clear();
        AppGridOptions options = AppGridPreferences.load(this);
        if (options.unrememberedCount(AppInventory.keys(apps)) > 0)
            messages.add("App order limit reached. New apps remain usable, but their order cannot be saved.");
        GridLayout grid = new GridLayout(this);
        grid.setClipChildren(false); grid.setClipToPadding(false);
        int width = getResources().getDisplayMetrics().widthPixels - Pages.dp(this, 84);
        boolean mini = com.jeremykenedy.firetv.ui.nativeapp.appgrid.HomeVisualOptions.isMini(this);
        float density = getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams trayBounds = (LinearLayout.LayoutParams) appArea.getLayoutParams();
        trayBounds.width = mini ? com.jeremykenedy.firetv.ui.nativeapp.appgrid.MiniDockLayout.width(width, density) : -1;
        trayBounds.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        int tileWidth = mini ? com.jeremykenedy.firetv.ui.nativeapp.appgrid.MiniDockLayout.cell(density) : Math.max(1, width / options.columns);
        boolean legacyAppFocus = com.jeremykenedy.firetv.ui.nativeapp.focus.FocusPreferences.legacyDefaultAppMode(this);
        int tileHeight = legacyAppFocus ? Math.round(tileWidth * 9f / 16f) + Pages.dp(this, 3)
                : Math.max(Pages.dp(this, 96), Math.round(tileWidth * 9f / 16f) + Pages.dp(this, 46));
        if (mini) tileHeight = tileWidth;
        int rows = mini ? 1 : options.rows == 0 ? 1 : options.rows;
        int maxHeight = Math.round(getResources().getDisplayMetrics().heightPixels * 0.45f);
        appArea.getLayoutParams().height = mini ? com.jeremykenedy.firetv.ui.nativeapp.appgrid.MiniDockLayout.height(density)
                : Math.min(maxHeight, rows * tileHeight + Pages.dp(this, 28));
        appArea.setPadding(Pages.dp(this, mini ? 4 : 10), Pages.dp(this, mini ? 6 : 14),
                Pages.dp(this, mini ? 4 : 10), Pages.dp(this, mini ? 6 : 14));
        refreshHomeInsets();
        appArea.requestLayout();
        if (mini || options.horizontal) {
            int availableHeight = Math.max(tileHeight, appArea.getLayoutParams().height - Pages.dp(this, 28));
            grid.setOrientation(GridLayout.VERTICAL); grid.setRowCount(rows);
            HorizontalScrollView scroll = new HorizontalScrollView(this);
            watchAppScroll(scroll);
            scroll.setClipChildren(false); scroll.setClipToPadding(false);
            scroll.addView(grid);
            if (rows * tileHeight > availableHeight) {
                ScrollView vertical = new ScrollView(this); vertical.setClipChildren(false); vertical.setClipToPadding(false);
                watchAppScroll(vertical); vertical.addView(scroll); appArea.addView(vertical);
            } else appArea.addView(scroll);
        } else {
            grid.setColumnCount(options.columns);
            ScrollView scroll = new ScrollView(this); scroll.setClipChildren(false); scroll.setClipToPadding(false);
            watchAppScroll(scroll);
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
                tile.useLegacyDefaultAppPresentation(mini ? com.jeremykenedy.firetv.ui.nativeapp.appgrid.MiniDockLayout.artwork(density)
                        : com.jeremykenedy.firetv.ui.nativeapp.focus.FocusOptions.legacyAppArtworkWidth(tileWidth));
                tile.setMiniAppPresentation(mini);
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
                tile.useAppFocusPresentation();
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
        for (String message : messages) grid.addView(homeMessage(message, 16));
        if (tiles.isEmpty()) grid.addView(homeMessage("No visible apps. Show apps in Settings → Home layout → Apps grid.", 18));
        if (restoreTile || oldFocus == null || focusInitialApp) {
            if (restore != null) restore.requestFocus();
            else if (!tiles.isEmpty()) { tiles.get(0).requestFocus(); focusedApp = String.valueOf(tiles.get(0).getTag()); }
            else { if (settings != null) settings.requestFocus(); focusedApp = null; }
        }
        restoringGridFocus = false;
        focusInitialApp = false;
        updateScrollBlur();
        AccessibilityStyle.apply(this, getWindow().getDecorView());
    }

    private void watchAppScroll(View view) {
        appScrolls.add(view);
        view.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override public void onScrollChange(View changed, int x, int y, int oldX, int oldY) { updateScrollBlur(); }
        });
    }

    private void updateScrollBlur() {
        if (background == null) return;
        boolean scrolled = false;
        for (View view : appScrolls) if (view.getScrollX() != 0 || view.getScrollY() != 0) scrolled = true;
        background.setScrollBlur(scrolled && com.jeremykenedy.firetv.ui.nativeapp.appgrid.HomeVisualOptions.isBlurOnScroll(this));
    }

    private TextView homeMessage(String message, int size) {
        TextView text = Pages.text(this, message, size);
        setHomeTextContrast(text);
        return text;
    }

    private static final class TextShadow {
        final float radius, dx, dy;
        final int color;
        TextShadow(TextView text) {
            radius = text.getShadowRadius(); dx = text.getShadowDx(); dy = text.getShadowDy();
            color = text.getShadowColor();
        }
        void restore(TextView text) { text.setShadowLayer(radius, dx, dy, color); }
    }

    private void setHomeTextContrast(TextView text) {
        if (homeTextContrast) {
            if (!homeShadows.containsKey(text)) homeShadows.put(text, new TextShadow(text));
            text.setShadowLayer(Pages.dp(this, 2), 0, Pages.dp(this, 1), 0xff000000);
        } else {
            TextShadow original = homeShadows.remove(text);
            if (original != null) original.restore(text);
        }
    }

    private void setStatusClockContrast(View view) {
        if (view instanceof TextView) setHomeTextContrast((TextView) view);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) setStatusClockContrast(group.getChildAt(i));
        }
    }

    private void refreshHomeAppearance() {
        AppearanceOptions options = AppearancePreferences.load(this);
        boolean clearLegacyGradient = options.base.equals("still_legacy_gradient") && options.scene.isEmpty()
                && LegacyGradients.index(options.legacyGradient) >= 0;
        homeBody.setBackgroundColor(0x00000000);
        boolean lightTheme = options.base.equals("still_light") && options.scene.isEmpty();
        android.graphics.drawable.GradientDrawable tray = new android.graphics.drawable.GradientDrawable();
        String trayMode = com.jeremykenedy.firetv.ui.nativeapp.appgrid.HomeVisualOptions.favoritesBackgroundMode(this);
        int trayColor = lightTheme ? 0x33ffffff : 0x55000000;
        if ("Color".equals(trayMode)) {
            trayColor = com.jeremykenedy.firetv.ui.nativeapp.appgrid.HomeVisualOptions.favoritesBackgroundColor(this, 0xff000000) & 0xffffff;
            trayColor |= Math.round(com.jeremykenedy.firetv.ui.nativeapp.appgrid.HomeVisualOptions.favoritesBackgroundOpacity(this) * 255f / 100f) << 24;
        }
        tray.setColor(trayColor);
        tray.setCornerRadius(Pages.dp(this, 22));
        tray.setStroke(Pages.dp(this, 1), lightTheme ? 0x44ffffff : 0x226d657e);
        if ("Hide".equals(trayMode)) appArea.setBackground(null);
        else if (background != null && appArea.getWidth() > 0
                && com.jeremykenedy.firetv.ui.nativeapp.appgrid.HomeVisualOptions.isFavoritesBackgroundBlurred(this)) {
            int[] position = new int[2], origin = new int[2];
            appArea.getLocationOnScreen(position); background.getLocationOnScreen(origin);
            android.graphics.Rect region = new android.graphics.Rect(position[0] - origin[0], position[1] - origin[1],
                    position[0] - origin[0] + appArea.getWidth(), position[1] - origin[1] + appArea.getHeight());
            android.graphics.Bitmap blurred = background.blurredRegion(region);
            appArea.setBackground(new com.jeremykenedy.firetv.ui.nativeapp.appearance.FavoritesBackdrop(blurred, trayColor, Pages.dp(this, 22)));
        } else appArea.setBackground(tray);
        homeTextContrast = clearLegacyGradient || !options.scene.isEmpty();
        homeContent.setWidgetReadability(homeTextContrast);
        View clock = statusBarArea.findViewWithTag("statusbar:clock");
        if (clock != null) setStatusClockContrast(clock);
    }

    private void addGridTile(GridLayout grid, FocusTileView tile, int width, int height) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = width; params.height = height;
        grid.addView(tile, params); tiles.add(tile);
    }

    private void folderActions(final FolderOptions.Folder folder) {
        final String key = "folder:" + folder.id;
        AlertDialog dialog = new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(this).setTitle(folder.name)
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
        AlertDialog actions = new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(this).setTitle(app.label).setItems(new String[] {
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
                try {
                    AppGridPreferences.save(MainActivity.this, current);
                    loadApps();
                } catch (IllegalStateException failure) {
                    Toast.makeText(MainActivity.this, failure.getMessage(), Toast.LENGTH_LONG).show();
                }
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

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_HOME)) HomeDialogBuilder.dismissForHome(this);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (homeReady && event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()
                    && !homeContent.showFocusedCardActions())
                startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return remoteButtons != null && remoteButtons.dispatch(event, false) || super.dispatchKeyEvent(event);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus && remoteButtons != null) remoteButtons.clear();
    }

    private void cardActions(final TvRecommendations.Feed feed, final TvRecommendations.Program program) {
        final ChannelCardView card = findCard(feed.item.key());
        if (card == null) return;
        card.onHostPause();
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        final View[] nextFocus = new View[1];
        final Runnable refreshVisibility = new Runnable() {
            @Override public void run() {
                homeContent.refreshPreferences();
                List<String> order = LayoutPreferences.load(MainActivity.this, HomeSlots.defaults()).order;
                int current = order.indexOf(feed.item.key());
                for (int i = current + 1; i < order.size() && nextFocus[0] == null; i++) {
                    View candidate = homeContent.findViewWithTag("slot:" + order.get(i));
                    if (candidate != null && candidate.isShown()) nextFocus[0] = candidate;
                }
                for (int i = current - 1; i >= 0 && nextFocus[0] == null; i--) {
                    View candidate = homeContent.findViewWithTag("slot:" + order.get(i));
                    if (candidate != null && candidate.isShown()) nextFocus[0] = candidate;
                }
            }
        };
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
            labels.add("Previous title"); actions.add(new Runnable() { @Override public void run() { card.showPreviousProgram(); } });
            labels.add("Next title"); actions.add(new Runnable() { @Override public void run() { card.showNextProgram(); } });
            labels.add("Hide this item"); actions.add(new Runnable() {
                @Override public void run() {
                    try {
                        CardOptions updated = CardPreferences.load(MainActivity.this).hide(program.source, program.key, program.title);
                        CardPreferences.save(MainActivity.this, updated); homeContent.refreshPreferences();
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
        labels.add("Hide this card"); actions.add(new Runnable() {
            @Override public void run() {
                try {
                    LayoutPreferences.save(MainActivity.this, LayoutPreferences.load(MainActivity.this, HomeSlots.defaults())
                            .discover(java.util.Collections.singletonList(feed.item)).setVisible(feed.item.key(), false));
                    refreshVisibility.run();
                    Toast.makeText(MainActivity.this, "Card hidden. Restore it in Card settings.", Toast.LENGTH_SHORT).show();
                } catch (RuntimeException failed) { Toast.makeText(MainActivity.this, "This card could not be hidden. Please try again.", Toast.LENGTH_SHORT).show(); }
            }
        });
        String publisher = feed.source;
        try {
            publisher = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(feed.source, 0)).toString();
        } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException unavailable) { }
        labels.add("Hide all cards from " + publisher); actions.add(new Runnable() {
            @Override public void run() {
                try {
                    CardPreferences.setPackageHidden(MainActivity.this, feed.source, true);
                    refreshVisibility.run();
                    Toast.makeText(MainActivity.this, "App cards hidden. Restore them in App Manager.", Toast.LENGTH_SHORT).show();
                } catch (RuntimeException failed) { Toast.makeText(MainActivity.this, "This app's cards could not be hidden. Please try again.", Toast.LENGTH_SHORT).show(); }
            }
        });
        labels.add("Card settings"); actions.add(new Runnable() {
            @Override public void run() { startActivity(new Intent(MainActivity.this, CardsSettingsActivity.class)); }
        });
        AlertDialog dialog = new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(this).setTitle(program == null ? feed.title : program.title)
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { actions.get(which).run(); }
                }).setNegativeButton("Cancel", null).create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface ignored) {
                if (resumed && card.isShown()) card.onHostResume();
                if (nextFocus[0] != null && nextFocus[0].isShown()) nextFocus[0].requestFocus();
            }
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

    @Override public void onBackPressed() {
        Object override = getSharedPreferences("launcher_prefs", MODE_PRIVATE).getAll().get("override_back_button");
        if (homeReady && !Boolean.FALSE.equals(override)) return;
        super.onBackPressed();
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
        HomeDialogBuilder.deactivate(this);
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
    @Override protected void onDestroy() { AppearancePreferences.file(this).unregisterOnSharedPreferenceChangeListener(appearanceChanged); HomeDialogBuilder.release(this); startupHandler.removeCallbacks(startupTimeout); if (homeReady) { awake.onPause(); dashboard.close(); recommendations.close(); } loader.shutdownNow(); super.onDestroy(); }
    @Override protected void onResume() {
        super.onResume();
        activityResumed = true;
        if (homeReady && !SourceStartup.ready(this)) {
            HomeDialogBuilder.release(this);
            remoteButtons.clear(); awake.onPause(); dashboard.close(); recommendations.close();
            homeReady = false;
            showStartup(true);
            return;
        }
        if (homeReady) resumeHome();
    }
    private void resumeHome() {
        HomeDialogBuilder.activate(this);
        refreshStatusBar();
        refreshHomeAppearance();
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
        if (!showAllApps && allApps.isFocused() && settings != null) settings.requestFocus();
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
