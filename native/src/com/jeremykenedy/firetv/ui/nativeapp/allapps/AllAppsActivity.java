package com.jeremykenedy.firetv.ui.nativeapp.allapps;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppGridOptions;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppGridPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppInventory;
import com.jeremykenedy.firetv.ui.nativeapp.locks.LauncherLocks;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AllAppsActivity extends Activity {
    private boolean startupBlocked;
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private String focusedKey;
    private int request;
    private boolean active;
    public static Intent intent(Context context) { return new Intent(context, AllAppsActivity.class); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        if (state != null) focusedKey = state.getString("focused_app");
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "All apps", 30));
        page.addView(Pages.text(this, "Loading launchable apps…", 18));
    }
    @Override protected void onResume() {
        if (startupBlocked) { super.onResume(); return; }
        super.onResume(); active = true;
        final int generation = ++request;
        loader.execute(new Runnable() {
            @Override public void run() {
                List<AppInventory.App> loaded;
                try { loaded = AppInventory.loadAllLaunchable(getApplicationContext()); }
                catch (RuntimeException failed) { loaded = null; }
                final List<AppInventory.App> result = loaded;
                runOnUiThread(new Runnable() { @Override public void run() {
                    if (!isDestroyed() && active && generation == request) render(result);
                } });
            }
        });
    }
    @Override protected void onPause() {
        if (startupBlocked) { super.onPause(); return; } active = false; request++; super.onPause(); }
    @Override protected void onDestroy() {
        if (startupBlocked) { super.onDestroy(); return; } loader.shutdownNow(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (startupBlocked) { super.onSaveInstanceState(state); return; } state.putString("focused_app", focusedKey); super.onSaveInstanceState(state); }
    private void render(List<AppInventory.App> apps) {
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "All apps", 30));
        page.addView(Pages.text(this, "Launchable installed apps, including apps hidden from Home. Locks still apply. Some ordinary Android apps need touch or a mouse.", 16));
        if (apps == null) { page.addView(Pages.text(this, "Apps could not be read. Go Back and try again. Saved choices are kept.", 18)); return; }
        if (apps.isEmpty()) { page.addView(Pages.text(this, "No launchable apps are available.", 18)); return; }
        AppGridOptions saved = AppGridPreferences.load(this);
        AppGridOptions visible = new AppGridOptions(saved.columns, saved.rows, saved.horizontal, saved.order,
                Collections.<String>emptySet(), saved.favorites);
        Map<String, AppInventory.App> byKey = new HashMap<>();
        for (AppInventory.App app : apps) byKey.put(app.key(), app);
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        String restore = focusedKey;
        Button target = null, first = null;
        for (String key : visible.visible(AppInventory.keys(apps))) {
            final AppInventory.App app = byKey.get(key);
            Button button = Pages.button(this, app.label + (saved.hidden.contains(key) ? " · Hidden from Home" : ""), new View.OnClickListener() {
                @Override public void onClick(View view) { open(app); }
            });
            button.setTag(key);
            button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View view, boolean focused) { if (focused) focusedKey = app.key(); }
            });
            list.addView(button);
            if (first == null) first = button;
            if (key.equals(restore)) target = button;
        }
        if (target != null) target.requestFocus(); else if (first != null) first.requestFocus();
    }
    private void open(final AppInventory.App app) {
        focusedKey = app.key();
        LauncherLocks.guard(this, app.key(), new Runnable() {
            @Override public void run() {
                try {
                    startActivity(app.launchIntent());
                } catch (RuntimeException removed) { unavailable(); }
            }
        });
    }
    private void unavailable() { Toast.makeText(this, "This app is no longer available. Your Home choices are kept.", Toast.LENGTH_LONG).show(); }
}
