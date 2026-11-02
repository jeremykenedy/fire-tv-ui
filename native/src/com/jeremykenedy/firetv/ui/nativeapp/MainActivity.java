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
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.cards.CardOptions;
import com.jeremykenedy.firetv.ui.nativeapp.cards.CardPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.cards.CardsSettingsActivity;
import com.jeremykenedy.firetv.ui.nativeapp.cards.ChannelCardView;
import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;
import com.jeremykenedy.firetv.ui.nativeapp.providers.RecommendationActions;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutOptions;
import com.jeremykenedy.firetv.ui.nativeapp.device.DeviceRoutes;
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
    private TextView cardStatus;
    private final List<ChannelCardView> cards = new ArrayList<>();
    private final List<TvRecommendations.Feed> feeds = new ArrayList<>();
    private int feedGeneration;
    private boolean resumed;
    private String focusedSlot;
    private List<AppInventory.App> apps = new ArrayList<>();
    private String focusedApp;
    private int generation;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null) { focusedApp = saved.getString("focused_app"); focusedSlot = saved.getString("focused_slot"); }
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
        cardStatus = Pages.text(this, "Checking channel cards…", 14);
        cardStatus.setTag("home:card-status");
        cardStatus.setMaxLines(2);
        cardStatus.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cardStatus.setPadding(Pages.dp(this, 16), 0, 0, 0);
        LinearLayout.LayoutParams statusLayout = new LinearLayout.LayoutParams(0, -2, 1);
        statusLayout.gravity = android.view.Gravity.CENTER_VERTICAL;
        actions.addView(cardStatus, statusLayout);
        homeSlots = new LinearLayout(this);
        homeSlots.setClipChildren(false); homeSlots.setClipToPadding(false);
        HorizontalScrollView slotScroll = new HorizontalScrollView(this);
        slotScroll.setClipChildren(false); slotScroll.setClipToPadding(false); slotScroll.addView(homeSlots);
        ScrollView slotViewport = new ScrollView(this); slotViewport.addView(slotScroll);
        body.addView(slotViewport, new LinearLayout.LayoutParams(-1, Pages.dp(this, 190)));
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

    private void loadFeeds() {
        final int request = ++feedGeneration;
        loader.execute(new Runnable() {
            @Override public void run() {
                final TvRecommendations.Result result = TvRecommendations.read(getApplicationContext());
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (isDestroyed() || !resumed || request != feedGeneration) return;
                        feeds.clear(); feeds.addAll(result.feeds);
                        cardStatus.setText(result.message() + (result.status == TvRecommendations.Status.PERMISSION_REQUIRED
                                ? " Open Settings, Cards to allow access." : ""));
                        showSlots();
                    }
                });
            }
        });
    }

    private void showSlots() {
        View oldFocus = getCurrentFocus();
        if (oldFocus != null && oldFocus.getTag() instanceof String
                && String.valueOf(oldFocus.getTag()).startsWith("slot:")) focusedSlot = String.valueOf(oldFocus.getTag());
        boolean restore = oldFocus != null && String.valueOf(oldFocus.getTag()).startsWith("slot:");
        for (ChannelCardView card : cards) card.onHostPause();
        cards.clear(); homeSlots.removeAllViews();
        List<LayoutItem> available = HomeSlots.available(feeds);
        LayoutOptions layout = LayoutPreferences.load(this, HomeSlots.defaults()).discover(available);
        LayoutPreferences.save(this, layout);
        CardOptions options = CardPreferences.load(this);
        Map<String, TvRecommendations.Feed> byKey = new HashMap<>();
        for (TvRecommendations.Feed feed : feeds) byKey.put(feed.item.key(), feed);
        for (LayoutItem item : layout.visible(available)) {
            View slot;
            if (item.equals(HomeSlots.CLOCK)) slot = new ClockWidgetView(this);
            else {
                TvRecommendations.Feed feed = byKey.get(item.key());
                if (feed == null) continue;
                ChannelCardView card = new ChannelCardView(this, feed, new ChannelCardView.Listener() {
                    @Override public void onSelect(TvRecommendations.Feed value, TvRecommendations.Program program) { cardActions(value, program); }
                    @Override public void onLongPress(TvRecommendations.Feed value, TvRecommendations.Program program) { cardActions(value, program); }
                });
                configureCard(card, options); cards.add(card); slot = card;
                if (resumed) card.onHostResume();
            }
            slot.setTag("slot:" + item.key());
            slot.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View view, boolean focused) {
                    if (focused) { focusedSlot = String.valueOf(view.getTag()); focusedApp = null; }
                }
            });
            homeSlots.addView(slot, new LinearLayout.LayoutParams(Pages.dp(this, layout.widthDp(item, 320)),
                    Pages.dp(this, layout.heightDp(item, 160))));
            if (restore && slot.getTag().equals(focusedSlot)) slot.requestFocus();
        }
    }

    private void configureCard(ChannelCardView card, CardOptions options) {
        List<TvRecommendations.Program> visible = new ArrayList<>();
        for (TvRecommendations.Program program : card.feed().programs)
            if (!options.isHidden(program.source, program.key)) visible.add(program);
        card.setPrograms(visible);
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
            if (links.title) {
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
        for (ChannelCardView card : cards) if (card.feed().item.key().equals(key)) return card;
        return null;
    }

    private void launchResult(RecommendationActions.Outcome result) {
        if (result != RecommendationActions.Outcome.OPENED)
            Toast.makeText(this, "This action is unavailable. Try opening the source app.", Toast.LENGTH_SHORT).show();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        View focus = getCurrentFocus();
        if (focus instanceof FocusTileView) state.putString("focused_app", String.valueOf(focus.getTag()));
        state.putString("focused_slot", focusedSlot);
        super.onSaveInstanceState(state);
    }
    @Override protected void onPause() {
        DeviceRoutes.launcherVisibility(false);
        resumed = false; ++feedGeneration;
        for (ChannelCardView card : cards) card.onHostPause();
        super.onPause();
    }
    @Override protected void onDestroy() { loader.shutdownNow(); super.onDestroy(); }
    @Override protected void onResume() {
        super.onResume();
        DeviceRoutes.launcherVisibility(true);
        resumed = true;
        View focus = getCurrentFocus();
        if (!(focus instanceof FocusTileView) && focus != null && (focus.getId() == 0x1001 || focus.getId() == 0x1002)) focusedApp = null;
        showSlots(); loadFeeds();
        loadApps();
    }
}
