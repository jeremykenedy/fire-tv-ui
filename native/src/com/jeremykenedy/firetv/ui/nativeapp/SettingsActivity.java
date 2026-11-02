package com.jeremykenedy.firetv.ui.nativeapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.jeremykenedy.firetv.ui.nativeapp.focus.FocusSettingsActivity;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppGridSettingsActivity;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutSettingsActivity;
import com.jeremykenedy.firetv.ui.nativeapp.cards.CardsSettingsActivity;
import com.jeremykenedy.firetv.ui.nativeapp.device.DeviceSettingsActivity;
import com.jeremykenedy.firetv.ui.nativeapp.icons.IconPackStore;
import com.jeremykenedy.firetv.ui.nativeapp.allapps.AllAppsPreferences;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SettingsActivity extends Activity {
    private boolean startupBlocked;
    private LinearLayout detail;
    private final Map<String, Button> categories = new LinkedHashMap<>();
    private String selected = "appearance";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        if (state != null) selected = state.getString("category", "appearance");
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "Settings", 30));
        LinearLayout columns = new LinearLayout(this);
        page.addView(columns, new LinearLayout.LayoutParams(-1, 0, 1));
        ScrollView categoryScroll = new ScrollView(this);
        LinearLayout menu = new LinearLayout(this); menu.setOrientation(LinearLayout.VERTICAL);
        categoryScroll.addView(menu);
        columns.addView(categoryScroll, new LinearLayout.LayoutParams(Pages.dp(this, 245), -1));
        ScrollView detailScroll = new ScrollView(this);
        detail = new LinearLayout(this); detail.setOrientation(LinearLayout.VERTICAL);
        detail.setPadding(Pages.dp(this, 30), 0, 0, 0);
        detailScroll.addView(detail);
        columns.addView(detailScroll, new LinearLayout.LayoutParams(0, -1, 1));
        category(menu, "appearance", "Appearance", 0x2001);
        category(menu, "cards", "Cards", 0x2020);
        category(menu, "widgets", "Widgets", 0x2040);
        category(menu, "home", "Home layout", 0x2003);
        category(menu, "device", "Device", 0x2030);
        category(menu, "updates", "Updates and backup", 0x2050);
        category(menu, "help", "Help", 0x2060);
        if (!categories.containsKey(selected)) selected = "appearance";
        show(selected, false);
        if (state == null) categories.get(selected).requestFocus();
    }

    private void category(LinearLayout menu, final String key, String title, int id) {
        Button button = Pages.button(this, title, new View.OnClickListener() {
            @Override public void onClick(View view) { show(key, true); }
        });
        button.setId(id); categories.put(key, button); menu.addView(button);
    }

    private void show(String category, boolean focus) {
        selected = category; detail.removeAllViews();
        Button owner = categories.get(category);
        detail.addView(Pages.text(this, owner.getText().toString(), 24));
        Button first;
        if ("device".equals(category)) {
            first = route("Device screens", DeviceSettingsActivity.class, 0x2031, owner);
            route("Screensavers", com.jeremykenedy.firetv.ui.nativeapp.device.screensavers.ScreensaverSettingsActivity.class, 0x2032, owner);
            route("Network and IP", com.jeremykenedy.firetv.ui.nativeapp.device.network.NetworkActivity.class, 0x2033, owner);
            route("Diagnostics", com.jeremykenedy.firetv.ui.nativeapp.device.network.DiagnosticsActivity.class, 0x2034, owner);
            route("Sleep and protection", com.jeremykenedy.firetv.ui.nativeapp.device.protection.ProtectionActivity.class, 0x2035, owner);
            route("Remote buttons", com.jeremykenedy.firetv.ui.nativeapp.device.remotebuttons.RemoteButtonsActivity.class, 0x2036, owner);
        } else if ("cards".equals(category)) {
            first = route("Channel cards and timers", CardsSettingsActivity.class, 0x2021, owner);
            route("Recommendation sources", com.jeremykenedy.firetv.ui.nativeapp.providers.RecommendationSourcesActivity.class, 0x2022, owner);
            route("Plex", com.jeremykenedy.firetv.ui.nativeapp.providers.plex.PlexSettingsActivity.class, 0x2023, owner);
            route("What to Watch from Plex", com.jeremykenedy.firetv.ui.nativeapp.providers.discovery.DiscoveryActivity.class, 0x2024, owner);
        } else if ("widgets".equals(category)) {
            first = route("Your cards and widgets", LayoutSettingsActivity.class, 0x2041, owner);
            detail.addView(Pages.text(this, "Choose a widget to show it on Home and configure its content. New widgets start hidden.", 18));
            route("Calendar", com.jeremykenedy.firetv.ui.nativeapp.widget.calendar.CalendarActivity.class, 0x2042, owner);
            route("Ambient audio", com.jeremykenedy.firetv.ui.nativeapp.widget.ambient.AmbientAudioActivity.class, 0x2043, owner);
            route("Live TV favorites now/next", com.jeremykenedy.firetv.ui.nativeapp.widget.livetv.LiveTvSettingsActivity.class, 0x2044, owner);
        } else if ("home".equals(category)) {
            first = route("Apps grid", AppGridSettingsActivity.class, 0x2010, owner);
            route("Your cards and widgets", LayoutSettingsActivity.class, 0x2011, owner);
            route("App folders", com.jeremykenedy.firetv.ui.nativeapp.folders.FolderActivity.class, 0x2012, owner);
            route("Launcher locks", com.jeremykenedy.firetv.ui.nativeapp.locks.LockSettingsActivity.class, 0x2013, owner);
            route("TV inputs", com.jeremykenedy.firetv.ui.nativeapp.inputs.InputsSettingsActivity.class, 0x2014, owner);
            route("All apps", com.jeremykenedy.firetv.ui.nativeapp.allapps.AllAppsActivity.class, 0x2015, owner);
            Button allApps = AllAppsPreferences.addControl(this, detail);
            allApps.setId(0x2016); allApps.setNextFocusLeftId(owner.getId());
            route("Dashboards and schedules", com.jeremykenedy.firetv.ui.nativeapp.layoutschedule.LayoutScheduleActivity.class, 0x2017, owner);
        } else if ("updates".equals(category)) {
            first = route("Updates", com.jeremykenedy.firetv.ui.nativeapp.updates.UpdatesActivity.class, 0x2051, owner);
            route("Backup and import", com.jeremykenedy.firetv.ui.nativeapp.backup.BackupActivity.class, 0x2052, owner);
            route("Saved setups", com.jeremykenedy.firetv.ui.nativeapp.setups.SavedSetupsActivity.class, 0x2053, owner);
        } else if ("help".equals(category)) {
            first = route("Help topics", HelpActivity.class, 0x2061, owner);
        } else {
            first = route("Icons and focus", FocusSettingsActivity.class, 0x2002, owner);
            route("Backgrounds and overlays", com.jeremykenedy.firetv.ui.nativeapp.appearance.BackgroundSettingsActivity.class, 0x2004, owner);
            Button packs = Pages.button(this, "Icon packs", new View.OnClickListener() {
                @Override public void onClick(View view) { IconPackStore.showPicker(SettingsActivity.this, null); }
            });
            packs.setId(0x2005); packs.setNextFocusLeftId(owner.getId()); detail.addView(packs);
            route("Text and contrast", com.jeremykenedy.firetv.ui.nativeapp.accessibility.AccessibilitySettingsActivity.class, 0x2006, owner);
        }
        owner.setNextFocusRightId(first.getId());
        if (focus) first.requestFocus();
    }

    private Button route(String title, final Class<?> target, int id, Button category) {
        Button button = Pages.button(this, title, new View.OnClickListener() {
            @Override public void onClick(View view) { startActivity(new Intent(SettingsActivity.this, target)); }
        });
        button.setId(id); button.setNextFocusLeftId(category.getId()); detail.addView(button);
        return button;
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (startupBlocked) { super.onSaveInstanceState(state); return; } state.putString("category", selected); super.onSaveInstanceState(state); }
    @Override protected void onResume() {
        if (startupBlocked) { super.onResume(); return; }
        super.onResume();
        com.jeremykenedy.firetv.ui.nativeapp.accessibility.AccessibilityStyle.apply(this, getWindow().getDecorView());
    }
}
