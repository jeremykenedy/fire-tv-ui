package com.jeremykenedy.firetv.ui.nativeapp.layout;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;
import com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroups;
import com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroupsStore;
import com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroupsView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LayoutSettingsActivity extends com.jeremykenedy.firetv.ui.nativeapp.MenuActivity {
    public static final String DASHBOARD_PROFILE = "dashboard_profile";
    public static final String BASE_LAYOUT = "base";
    private boolean startupBlocked;
    private String profileId, profileName;
    private LayoutOptions options;
    private List<LayoutItem> available;
    private LinearLayout rows;
    private LinearLayout general;
    private TextView heading;
    private TextView explanation;
    private TextView orderHeading;
    private Button widgetSettings;
    private boolean widgetPage;
    private boolean enteredWidgets;
    private Button enableWidgets;
    private Button widgetBackground;
    private Button widgetText;
    private TextView size;
    private TextView mode;
    private TextView providerStatus;
    private Button providerAction;
    private List<TvRecommendations.Feed> feeds = Collections.emptyList();
    private TvRecommendations.Status status;
    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    private int readGeneration;
    private boolean permissionDenied;
    private static final int LISTINGS_REQUEST = 41;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        enteredWidgets = state == null ? getIntent().getBooleanExtra("widgets", false) : state.getBoolean("entered_widgets", false);
        widgetPage = state == null ? enteredWidgets : state.getBoolean("widgets", false);
        permissionDenied = state != null && state.getBoolean("listings_denied", false);
        profileId = getIntent().getStringExtra(DASHBOARD_PROFILE);
        if (BASE_LAYOUT.equals(profileId)) profileId = null;
        available = availableItems();
        try {
            LayoutOptions selected = LayoutPreferences.load(this, HomeSlots.defaults());
            if (profileId != null) {
                if (!com.jeremykenedy.firetv.ui.nativeapp.layoutschedule.LayoutSchedule.validId(profileId))
                    throw new IllegalStateException("This dashboard is unavailable.");
                com.jeremykenedy.firetv.ui.nativeapp.layoutschedule.LayoutSchedule.Profile profile =
                        new com.jeremykenedy.firetv.ui.nativeapp.layoutschedule.LayoutScheduleStore(this).read().profile(profileId);
                if (profile == null) throw new IllegalStateException("This dashboard no longer exists.");
                profileName = profile.name;
                selected = profile.layout;
                enteredWidgets = false; widgetPage = false;
            }
            options = HomeSlots.discover(selected, feeds).discover(available);
        } catch (Exception unavailable) {
            startupBlocked = true;
            Toast.makeText(this, unavailable.getMessage(), Toast.LENGTH_LONG).show();
            finish(); return;
        }
        LinearLayout page = Pages.sidePanel(this);
        page.setClipChildren(true);
        heading = Pages.text(this, "", 26);
        heading.setTextColor(0xff8d8d8d);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        explanation = Pages.text(this, "", 17);
        page.addView(heading);
        page.addView(explanation);
        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout content = column();
        scroll.addView(content);
        widgetSettings = Pages.menuButton(this, "Widget settings", new View.OnClickListener() {
            @Override public void onClick(View view) { showWidgets(!widgetPage); }
        });
        widgetSettings.setTag("layout:widget-settings");
        enableWidgets = Pages.menuButton(this, "Enable widgets", new View.OnClickListener() {
            @Override public void onClick(View view) {
                save(options.withMode(LayoutPreferences.toggleWidgetsMode(LayoutSettingsActivity.this, options)),
                        "layout:enable-widgets");
            }
        });
        enableWidgets.setTag("layout:enable-widgets");
        content.addView(enableWidgets);
        widgetBackground = Pages.menuButton(this, "Widget Background Color", new View.OnClickListener() {
            @Override public void onClick(View view) { com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetAppearance.choose(LayoutSettingsActivity.this, false); }
        });
        widgetText = Pages.menuButton(this, "Widget Text Color", new View.OnClickListener() {
            @Override public void onClick(View view) { com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetAppearance.choose(LayoutSettingsActivity.this, true); }
        });
        content.addView(widgetBackground); content.addView(widgetText);
        content.addView(widgetSettings);
        Button addGroup = Pages.menuButton(this, "Add widget column", new View.OnClickListener() {
            @Override public void onClick(View view) { addWidgetColumn(); }
        });
        addGroup.setTag("layout:add-widget-column");
        if (profileId == null) content.addView(addGroup);
        general = column();
        content.addView(general);
        Button statusBar = Pages.menuButton(this, "Status bar", new View.OnClickListener() {
            @Override public void onClick(View view) {
                com.jeremykenedy.firetv.ui.nativeapp.statusbar.StatusBarSettings.show(LayoutSettingsActivity.this);
            }
        });
        statusBar.setTag("layout:statusbar-settings");
        if (profileId == null) general.addView(statusBar);
        general.addView(Pages.text(this, "Home content", 22));
        mode = Pages.text(this, "", 18);
        mode.setTag("layout:mode-status");
        general.addView(mode);
        LinearLayout modes = column();
        mode(modes, "Cards and widgets", LayoutOptions.Mode.BOTH);
        mode(modes, "Cards only", LayoutOptions.Mode.CARDS_ONLY);
        mode(modes, "Widgets only", LayoutOptions.Mode.WIDGETS_ONLY);
        general.addView(modes);
        LinearLayout hideContent = column();
        mode(hideContent, "Hide cards and widgets", LayoutOptions.Mode.NONE);
        general.addView(hideContent);
        general.addView(Pages.text(this, "Changing this mode keeps every item's saved order and Show/Hide choice.", 16));
        general.addView(Pages.text(this, "Channel card size", 22));
        size = Pages.text(this, "", 18);
        general.addView(size);
        LinearLayout presets = column();
        preset(presets, "Compact", 240, 135);
        preset(presets, "Standard", 320, 180);
        preset(presets, "Large", 400, 225);
        presets.addView(Pages.menuButton(this, "Custom size", new View.OnClickListener() {
            @Override public void onClick(View view) { customSize(); }
        }));
        general.addView(presets);
        general.addView(Pages.text(this, "Every channel card shares this size. Widget sizes stay independent.", 16));
        providerStatus = Pages.text(this, "Checking TV recommendations…", 17);
        providerStatus.setTag("layout:provider-status");
        general.addView(providerStatus);
        providerAction = Pages.menuButton(this, "Refresh channel cards", new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (status == TvRecommendations.Status.PERMISSION_REQUIRED) requestListings();
                else refreshFeeds();
            }
        });
        providerAction.setTag("layout:provider-action");
        general.addView(providerAction);
        orderHeading = Pages.text(this, "", 22);
        content.addView(orderHeading);
        rows = column();
        content.addView(rows);
        render(null);
    }

    private List<LayoutItem> availableItems() {
        List<LayoutItem> result = new ArrayList<>(HomeSlots.available(feeds));
        if (getSharedPreferences("native_layout", MODE_PRIVATE).contains("widget_groups"))
            result.add(WidgetGroupsView.ITEM);
        return result;
    }

    private void addWidgetColumn() {
        try {
            WidgetGroupsStore store = new WidgetGroupsStore(this);
            String previous = store.raw();
            WidgetGroups groups = store.load();
            if (groups == null) groups = WidgetGroups.decode("[]");
            if (!store.save(previous, groups.appendEmptyGroup())) {
                Toast.makeText(this, "Widget groups changed or could not be saved. Try again.", Toast.LENGTH_LONG).show();
                return;
            }
            available = availableItems();
            save(options.discover(available), "layout:add-widget-column");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            Toast.makeText(this, "Widget groups could not be read or saved. Existing data was kept.", Toast.LENGTH_LONG).show();
        }
    }

    private void showWidgets(boolean show) {
        widgetPage = show;
        render(null);
        if (show && rows.getChildCount() > 0) {
            View first = rows.getChildAt(0).findViewWithTag(firstWidgetKey() + ":visibility");
            if (first != null) first.requestFocus();
        } else widgetSettings.requestFocus();
    }

    private String firstWidgetKey() {
        for (String key : options.order) if (key.startsWith("widget:")) return key;
        return "";
    }

    @Override public void onBackPressed() {
        if (startupBlocked) { super.onBackPressed(); return; }
        if (widgetPage && !enteredWidgets) showWidgets(false);
        else super.onBackPressed();
    }

    @Override protected void onStart() {
        if (startupBlocked) { super.onStart(); return; }
        super.onStart();
        refreshFeeds();
    }

    @Override protected void onResume() {
        if (startupBlocked) { super.onResume(); return; }
        super.onResume();
        if (rows != null) {
            View focus = getCurrentFocus();
            render(focus != null && focus.getTag() instanceof String ? (String) focus.getTag() : null);
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        if (startupBlocked) { super.onWindowFocusChanged(hasFocus); return; }
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && rows != null) {
            View focus = getCurrentFocus();
            render(focus != null && focus.getTag() instanceof String ? (String) focus.getTag() : null);
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (startupBlocked) { super.onSaveInstanceState(state); return; }
        state.putBoolean("listings_denied", permissionDenied);
        state.putBoolean("widgets", widgetPage);
        state.putBoolean("entered_widgets", enteredWidgets);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        if (startupBlocked) { super.onDestroy(); return; }
        readGeneration++;
        reader.shutdownNow();
        super.onDestroy();
    }

    private void refreshFeeds() {
        final int generation = ++readGeneration;
        providerStatus.setText("Checking TV recommendations…");
        providerAction.setEnabled(false);
        reader.execute(new Runnable() {
            @Override public void run() {
                final TvRecommendations.Result result = TvRecommendations.read(getApplicationContext());
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (isFinishing() || isDestroyed() || generation != readGeneration) return;
                        View focused = getCurrentFocus();
                        String tag = focused != null && focused.getTag() instanceof String ? (String) focused.getTag() : null;
                        status = result.status;
                        feeds = result.feeds;
                        available = availableItems();
                        try {
                            options = HomeSlots.discover(options, feeds).discover(available);
                        } catch (IllegalArgumentException full) {
                            providerStatus.setText("Your saved layout is full. Existing slots and their order are preserved.");
                            providerAction.setEnabled(true);
                            return;
                        }
                        providerStatus.setText(permissionDenied && status == TvRecommendations.Status.PERMISSION_REQUIRED
                                ? "TV listings access was denied. Your saved cards and order are preserved. Allow access to read shared recommendations."
                                : result.message());
                        providerAction.setText(status == TvRecommendations.Status.PERMISSION_REQUIRED
                                ? "Allow TV listings access" : "Refresh channel cards");
                        providerAction.setEnabled(true);
                        render(tag);
                    }
                });
            }
        });
    }

    private void requestListings() {
        if (Build.VERSION.SDK_INT < 23) {
            providerStatus.setText("TV listings permission cannot be requested on this Android version.");
            return;
        }
        try {
            requestPermissions(new String[]{TvRecommendations.PERMISSION}, LISTINGS_REQUEST);
        } catch (RuntimeException unsupported) {
            providerStatus.setText("TV listings access cannot be requested on this device.");
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (startupBlocked) { super.onRequestPermissionsResult(requestCode, permissions, results); return; }
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != LISTINGS_REQUEST) return;
        permissionDenied = results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED;
        refreshFeeds();
    }

    private void mode(LinearLayout row, String label, final LayoutOptions.Mode value) {
        Button button = Pages.menuButton(this, label, new View.OnClickListener() {
            @Override public void onClick(View view) { save(options.withMode(value), "layout:mode:" + value.name()); }
        });
        button.setTag("layout:mode:" + value.name());
        row.addView(button);
    }

    private void preset(LinearLayout row, String label, final int width, final int height) {
        row.addView(Pages.menuButton(this, label, new View.OnClickListener() {
            @Override public void onClick(View view) { save(options.resizeCards(width, height), null); }
        }));
    }

    private void render(String focusTag) {
        heading.setText(profileName == null ? (widgetPage ? "Widgets" : "Your cards and widgets") : "Dashboard: " + profileName);
        widgetSettings.setVisibility(profileId == null ? View.VISIBLE : View.GONE);
        enableWidgets.setVisibility(widgetPage ? View.VISIBLE : View.GONE);
        widgetBackground.setVisibility(widgetPage ? View.VISIBLE : View.GONE);
        widgetText.setVisibility(widgetPage ? View.VISIBLE : View.GONE);
        enableWidgets.setText("Enable widgets: " + (LayoutPreferences.widgetsEnabled(this, options) ? "On" : "Off"));
        explanation.setVisibility(widgetPage ? View.GONE : View.VISIBLE);
        explanation.setText(widgetPage ? "Configure your local widgets and choose which ones appear on Home. Set shared order in Your cards and widgets."
                : profileName == null ? "Keep cards and widgets together in the order you choose."
                : "Card visibility, order and size apply only to this dashboard. Your base layout and other dashboards stay unchanged.");
        widgetSettings.setText(widgetPage ? "Your cards and widgets" : "Widget settings");
        general.setVisibility(widgetPage ? View.GONE : View.VISIBLE);
        orderHeading.setText(widgetPage ? "Widgets" : "Order and visibility");
        mode.setText(options.mode == LayoutOptions.Mode.BOTH ? "Showing cards and widgets"
                : options.mode == LayoutOptions.Mode.CARDS_ONLY ? "Showing cards only"
                : options.mode == LayoutOptions.Mode.WIDGETS_ONLY ? "Showing widgets only" : "Cards and widgets hidden");
        size.setText(options.cardWidthDp + " × " + options.cardHeightDp + " for every channel card");
        rows.removeAllViews();
        Set<String> present = new HashSet<>();
        for (LayoutItem item : available) present.add(item.key());
        for (int index = 0; index < options.order.size(); index++) {
            final String key = options.order.get(index);
            if (widgetPage && !key.startsWith("widget:")) continue;
            final int position = index;
            LinearLayout item = column();
            final WidgetProvider widget = HomeSlots.widget(key);
            String status = present.contains(key) ? (options.hidden.contains(key) ? "Hidden" : "Shown") : "Source unavailable";
            LayoutItem.Kind kind = key.startsWith("channel:") ? LayoutItem.Kind.CHANNEL_CARD : LayoutItem.Kind.WIDGET;
            if (present.contains(key) && !options.includes(kind)) status += " · Excluded by Home content mode";
            if (widget != null) status += "\n" + stateLabel(widget.state(this)) + ": " + widget.message(this);
            String title = key.equals(WidgetGroupsView.ITEM.key()) ? "Grouped widgets" : HomeSlots.title(key, feeds);
            item.addView(Pages.text(this, (index + 1) + ". " + title + "\n" + status, 19));
            LinearLayout actions = column();
            Button visibility = Pages.menuButton(this, options.hidden.contains(key) ? "Show" : "Hide", new View.OnClickListener() {
                @Override public void onClick(View view) { save(options.setVisible(key, options.hidden.contains(key)), key + ":visibility"); }
            });
            visibility.setTag(key + ":visibility");
            actions.addView(visibility);
            Button up = Pages.menuButton(this, "Move up", new View.OnClickListener() {
                @Override public void onClick(View view) { save(options.moveBefore(key, options.order.get(position - 1)), key + ":up"); }
            });
            up.setEnabled(index > 0);
            up.setTag(key + ":up");
            if (!widgetPage) actions.addView(up);
            Button down = Pages.menuButton(this, "Move down", new View.OnClickListener() {
                @Override public void onClick(View view) {
                    String before = position + 2 < options.order.size() ? options.order.get(position + 2) : null;
                    save(options.moveBefore(key, before), key + ":down");
                }
            });
            down.setEnabled(index + 1 < options.order.size());
            down.setTag(key + ":down");
            if (!widgetPage) actions.addView(down);
            item.addView(actions);
            if (widget != null) {
                Button configure = Pages.menuButton(this, "Configure " + widget.title(), new View.OnClickListener() {
                    @Override public void onClick(View view) { widget.configure(LayoutSettingsActivity.this); }
                });
                configure.setTag(key + ":configure");
                item.addView(configure);
            }
            rows.addView(item);
        }
        if (focusTag != null) {
            View target = getWindow().getDecorView().findViewWithTag(focusTag);
            if (target != null && target.isEnabled()) target.requestFocus();
            else if (focusTag.startsWith("channel:") || focusTag.startsWith("widget:")) {
                int separator = focusTag.lastIndexOf(':');
                View fallback = rows.findViewWithTag(focusTag.substring(0, separator) + ":visibility");
                if (fallback != null) fallback.requestFocus();
            }
        }
    }

    private String stateLabel(WidgetProvider.State state) {
        switch (state) {
            case EMPTY: return "Empty";
            case UNAVAILABLE: return "Unavailable";
            case SETUP_REQUIRED: return "Needs setup";
            default: return "Ready";
        }
    }

    private boolean save(LayoutOptions value, String focusTag) {
        try {
            if (profileId == null) LayoutPreferences.save(this, value);
            else {
                com.jeremykenedy.firetv.ui.nativeapp.layoutschedule.LayoutScheduleStore store =
                        new com.jeremykenedy.firetv.ui.nativeapp.layoutschedule.LayoutScheduleStore(this);
                com.jeremykenedy.firetv.ui.nativeapp.layoutschedule.LayoutSchedule.Profile profile = store.read().profile(profileId);
                if (profile == null) throw new IllegalStateException("This dashboard no longer exists.");
                store.save(profile.name, profile.id, value);
                profileName = profile.name;
            }
            options = value;
            render(focusTag);
            return true;
        } catch (Exception failed) {
            Toast.makeText(this, failed.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void customSize() {
        LinearLayout fields = column();
        int padding = Pages.dp(this, 24);
        fields.setPadding(padding, 0, padding, 0);
        fields.addView(Pages.text(this, "Sizes scale to your TV's display. Width: 64–1024. Height: 48–1024.", 16));
        fields.addView(Pages.text(this, "Width", 17));
        final EditText width = number(options.cardWidthDp, "Width");
        fields.addView(width);
        fields.addView(Pages.text(this, "Height", 17));
        final EditText height = number(options.cardHeightDp, "Height");
        fields.addView(height);
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Custom card size").setView(fields)
                .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        try {
                            LayoutOptions value = options.resizeCards(Integer.parseInt(width.getText().toString()),
                                    Integer.parseInt(height.getText().toString()));
                            if (save(value, null)) dialog.dismiss();
                        } catch (IllegalArgumentException invalid) {
                            width.setError("Use width 64–1024 and height 48–1024.");
                        }
                    }
                });
            }
        });
        dialog.show();
    }

    private EditText number(int value, String label) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setText(Integer.toString(value));
        field.setContentDescription(label);
        return field;
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }
}
