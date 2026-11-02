package com.jeremykenedy.firetv.ui.nativeapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.focus.FocusSettingsActivity;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppGridSettingsActivity;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutSettingsActivity;
import com.jeremykenedy.firetv.ui.nativeapp.cards.CardsSettingsActivity;
import com.jeremykenedy.firetv.ui.nativeapp.device.DeviceSettingsActivity;
import com.jeremykenedy.firetv.ui.nativeapp.icons.IconPackStore;
import com.jeremykenedy.firetv.ui.nativeapp.allapps.AllAppsPreferences;

public final class SettingsActivity extends MenuActivity {
    private boolean startupBlocked;
    private LinearLayout menu;
    private TextView heading;
    private ScrollView scroll;
    private String screen = "root";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        LinearLayout page = Pages.sidePanel(this);
        heading = new TextView(this);
        heading.setText("Settings"); heading.setTextSize(26);
        heading.setTextColor(0xff8d8d8d);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setPadding(0, Pages.dp(this, 4), 0, Pages.dp(this, 26));
        page.addView(heading);
        scroll = new ScrollView(this);
        scroll.setClipChildren(false); scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        menu = new LinearLayout(this); menu.setOrientation(LinearLayout.VERTICAL);
        menu.setClipChildren(false); menu.setClipToPadding(false);
        menu.setPadding(0, Pages.dp(this, 4), 0, Pages.dp(this, 8));
        scroll.addView(menu);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        show(state == null ? "root" : state.getString("screen", "root"),
                state == null ? 0 : state.getInt("focus", 0));
    }

    private void show(String selected, int restoreFocus) {
        screen = selected; menu.removeAllViews(); scroll.scrollTo(0, 0);
        if ("ui".equals(screen)) {
            heading.setText("UI settings");
            route("Apps", AppGridSettingsActivity.class, 0x2010);
            submenu("App Icons", "icons", 0x2001);
            route("Widgets", LayoutSettingsActivity.class, 0x2041);
            submenu("Cards", "cards", 0x2020);
            item("Status bar", 0x2071, new View.OnClickListener() {
                @Override public void onClick(View view) {
                    com.jeremykenedy.firetv.ui.nativeapp.statusbar.StatusBarSettings.show(SettingsActivity.this);
                }
            });
            route("Favorites", AppGridSettingsActivity.class, 0x2072);
            submenu("Layout and focus", "layout", 0x2003);
        } else if ("icons".equals(screen)) {
            heading.setText("App Icons");
            route("Icons and focus", FocusSettingsActivity.class, 0x2002);
            item("Icon Pack", 0x2005, new View.OnClickListener() {
                @Override public void onClick(View view) { IconPackStore.showPicker(SettingsActivity.this, null); }
            });
        } else if ("layout".equals(screen)) {
            heading.setText("Layout and focus");
            route("Apps grid", AppGridSettingsActivity.class, 0x2010);
            route("Your cards and widgets", LayoutSettingsActivity.class, 0x2011);
            route("Icons and focus", FocusSettingsActivity.class, 0x2002);
            route("App folders", com.jeremykenedy.firetv.ui.nativeapp.folders.FolderActivity.class, 0x2012);
            route("TV inputs", com.jeremykenedy.firetv.ui.nativeapp.inputs.InputsSettingsActivity.class, 0x2014);
            route("All apps", com.jeremykenedy.firetv.ui.nativeapp.allapps.AllAppsActivity.class, 0x2015);
            AllAppsPreferences.addControl(this, menu).setId(0x2016);
            route("Dashboards and schedules", com.jeremykenedy.firetv.ui.nativeapp.layoutschedule.LayoutScheduleActivity.class, 0x2017);
            route("Text and contrast", com.jeremykenedy.firetv.ui.nativeapp.accessibility.AccessibilitySettingsActivity.class, 0x2006);
            final Button transitions = item("Settings transitions: " + MenuTransition.label(this), 0x2007, null);
            transitions.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    MenuTransition.choose(SettingsActivity.this, new Runnable() {
                        @Override public void run() {
                            transitions.setText("Settings transitions: " + MenuTransition.label(SettingsActivity.this));
                        }
                    });
                }
            });
        } else if ("cards".equals(screen)) {
            heading.setText("Cards");
            route("Channel cards and timers", CardsSettingsActivity.class, 0x2021);
            route("Recommendation sources", com.jeremykenedy.firetv.ui.nativeapp.providers.RecommendationSourcesActivity.class, 0x2022);
            route("Plex", com.jeremykenedy.firetv.ui.nativeapp.providers.plex.PlexSettingsActivity.class, 0x2023);
            route("What to Watch from Plex", com.jeremykenedy.firetv.ui.nativeapp.providers.discovery.DiscoveryActivity.class, 0x2024);
        } else if ("backup".equals(screen)) {
            heading.setText("Backup/Restore");
            route("Backup and import", com.jeremykenedy.firetv.ui.nativeapp.backup.BackupActivity.class, 0x2052);
            route("Saved setups", com.jeremykenedy.firetv.ui.nativeapp.setups.SavedSetupsActivity.class, 0x2053);
        } else if ("advanced".equals(screen)) {
            heading.setText("Advanced");
            route("Device screens", DeviceSettingsActivity.class, 0x2031);
            route("Network and IP", com.jeremykenedy.firetv.ui.nativeapp.device.network.NetworkActivity.class, 0x2033);
            route("Diagnostics", com.jeremykenedy.firetv.ui.nativeapp.device.network.DiagnosticsActivity.class, 0x2034);
            route("Launcher locks", com.jeremykenedy.firetv.ui.nativeapp.locks.LockSettingsActivity.class, 0x2013);
            route("Calendar", com.jeremykenedy.firetv.ui.nativeapp.widget.calendar.CalendarActivity.class, 0x2042);
            route("Ambient audio", com.jeremykenedy.firetv.ui.nativeapp.widget.ambient.AmbientAudioActivity.class, 0x2043);
            route("Live TV favorites now/next", com.jeremykenedy.firetv.ui.nativeapp.widget.livetv.LiveTvSettingsActivity.class, 0x2044);
        } else if ("about".equals(screen)) {
            heading.setText("About");
            String version = "";
            try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
            catch (android.content.pm.PackageManager.NameNotFoundException unavailable) { }
            menu.addView(Pages.text(this, "Fire TV UI\n" + version, 20));
            route("Updates", com.jeremykenedy.firetv.ui.nativeapp.updates.UpdatesActivity.class, 0x2051);
        } else {
            screen = "root"; heading.setText("Settings");
            route("Screensavers", com.jeremykenedy.firetv.ui.nativeapp.device.screensavers.ScreensaverSettingsActivity.class, 0x2032);
            route("Theme and wallpaper", com.jeremykenedy.firetv.ui.nativeapp.appearance.BackgroundSettingsActivity.class, 0x2004);
            submenu("UI settings", "ui", 0x2070);
            route("Remote controls", com.jeremykenedy.firetv.ui.nativeapp.device.remotebuttons.RemoteButtonsActivity.class, 0x2036);
            submenu("Backup/Restore", "backup", 0x2050);
            submenu("About", "about", 0x2080);
            route("Help", HelpActivity.class, 0x2061);
            route("Startup and protection", com.jeremykenedy.firetv.ui.nativeapp.device.protection.ProtectionActivity.class, 0x2035);
            submenu("Advanced", "advanced", 0x2030);
        }
        View focus = restoreFocus == 0 ? null : menu.findViewById(restoreFocus);
        if (focus == null) for (int i = 0; i < menu.getChildCount(); i++) {
            if (menu.getChildAt(i).isFocusable()) { focus = menu.getChildAt(i); break; }
        }
        if (focus != null) focus.requestFocus();
    }

    private Button item(String label, int id, View.OnClickListener click) {
        Button button = Pages.menuButton(this, label, click);
        button.setId(id); menu.addView(button); return button;
    }

    private void submenu(String label, final String target, int id) {
        item(label, id, new View.OnClickListener() {
            @Override public void onClick(View view) { show(target, 0); }
        });
    }

    private void route(String label, final Class<?> target, int id) {
        item(label, id, new View.OnClickListener() {
            @Override public void onClick(View view) { startActivity(new Intent(SettingsActivity.this, target)); }
        });
    }

    @Override public void onBackPressed() {
        if ("root".equals(screen) || startupBlocked) { super.onBackPressed(); return; }
        if ("icons".equals(screen)) show("ui", 0x2001);
        else if ("layout".equals(screen)) show("ui", 0x2003);
        else if ("cards".equals(screen)) show("ui", 0x2020);
        else show("root", "ui".equals(screen) ? 0x2070 : "backup".equals(screen) ? 0x2050
                : "about".equals(screen) ? 0x2080 : 0x2030);
    }

    @Override protected void onResume() {
        super.onResume();
        if (startupBlocked) return;
        if (!SourceStartup.ready(this)) {
            startupBlocked = SourceStartup.redirectIfBlocked(this);
            return;
        }
        com.jeremykenedy.firetv.ui.nativeapp.accessibility.AccessibilityStyle.apply(this, getWindow().getDecorView());
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("screen", screen);
        View focus = getCurrentFocus();
        if (focus != null) state.putInt("focus", focus.getId());
        super.onSaveInstanceState(state);
    }
}
