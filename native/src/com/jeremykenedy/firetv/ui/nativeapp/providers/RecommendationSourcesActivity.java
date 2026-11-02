package com.jeremykenedy.firetv.ui.nativeapp.providers;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Discovery reports only feeds actually shared through the device's TV provider. */
public final class RecommendationSourcesActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<String, String> installed = new LinkedHashMap<>();
    private RecommendationLoader loader;
    private TvRecommendations.Result snapshot;
    private LinearLayout sources;
    private TextView status;
    private Button permission;
    private Button notifications;
    private boolean inventoryUnavailable;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout body = Pages.page(this);
        body.addView(Pages.text(this, "App recommendation sources", 28));
        body.addView(Pages.text(this, "Only recommendations shared by apps on this device appear here. Installing an app does not provide access to its personal watch history.", 17));
        status = Pages.text(this, "Checking shared TV recommendations…", 18);
        status.setTag("sources:status");
        body.addView(status);
        Button refresh = Pages.button(this, "Refresh shared recommendations", new View.OnClickListener() {
            @Override public void onClick(View view) { loader.refresh(); }
        });
        refresh.setTag("sources:refresh");
        body.addView(refresh);
        Button plex = Pages.button(this, "Plex library setup", new View.OnClickListener() {
            @Override public void onClick(View view) {
                startActivity(new Intent().setClassName(RecommendationSourcesActivity.this,
                        "com.jeremykenedy.firetv.ui.nativeapp.providers.plex.PlexSettingsActivity"));
            }
        });
        plex.setTag("sources:plex");
        body.addView(plex);
        notifications = Pages.button(this, "", new View.OnClickListener() {
            @Override public void onClick(View view) {
                try {
                    NotificationRecommendations.setEnabled(RecommendationSourcesActivity.this,
                            !NotificationRecommendations.enabled(RecommendationSourcesActivity.this));
                    loader.refresh();
                    render();
                } catch (RuntimeException unavailable) {
                    Toast.makeText(RecommendationSourcesActivity.this, "Notification card setting could not be changed.", Toast.LENGTH_SHORT).show();
                }
            }
        });
        notifications.setTag("sources:notifications");
        body.addView(notifications);
        Button access = Pages.button(this, "Open notification access settings", new View.OnClickListener() {
            @Override public void onClick(View view) {
                try { startActivity(NotificationRecommendations.accessSettings()); }
                catch (RuntimeException unavailable) {
                    Toast.makeText(RecommendationSourcesActivity.this, "This device has no notification access settings screen.", Toast.LENGTH_LONG).show();
                }
            }
        });
        access.setTag("sources:notification-access");
        body.addView(access);
        body.addView(Pages.text(this, "Optional notification access lets Android share notifications with this app. Only active TV recommendation notifications with an action owned by their publisher become cards. Other notifications are not displayed or stored.", 15));
        permission = Pages.button(this, "Allow TV listings", new View.OnClickListener() {
            @Override public void onClick(View view) { requestPermissions(new String[]{TvRecommendations.PERMISSION}, 1); }
        });
        permission.setTag("sources:permission");
        body.addView(permission);
        ScrollView scroll = new ScrollView(this);
        sources = new LinearLayout(this);
        sources.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(sources);
        body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        loader = new RecommendationLoader(this, new RecommendationLoader.Listener() {
            @Override public void onRecommendations(TvRecommendations.Result result) {
                snapshot = result;
                render();
            }
        });
        worker.execute(new Runnable() {
            @Override public void run() {
                final Map<String, String> apps = new LinkedHashMap<>();
                boolean unavailable = false;
                try {
                    PackageManager packages = getPackageManager();
                    List<ResolveInfo> activities = packages.queryIntentActivities(new Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
                    for (ResolveInfo activity : activities) {
                        if (apps.size() >= 128) break;
                        if (activity.activityInfo != null && !getPackageName().equals(activity.activityInfo.packageName))
                            apps.put(activity.activityInfo.packageName, activity.loadLabel(packages).toString());
                    }
                } catch (RuntimeException failure) { unavailable = true; }
                final boolean failed = unavailable;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (isDestroyed()) return;
                        installed.clear(); installed.putAll(apps);
                        inventoryUnavailable = failed;
                        render();
                    }
                });
            }
        });
        render();
        refresh.requestFocus();
    }

    private void render() {
        notifications.setText(NotificationRecommendations.enabled(this) ? "Turn off notification recommendations" : "Enable notification recommendations");
        permission.setVisibility(checkSelfPermission(TvRecommendations.PERMISSION) == PackageManager.PERMISSION_GRANTED
                ? View.GONE : View.VISIBLE);
        if (snapshot == null) return;
        status.setText(snapshot.message());
        View focused = getCurrentFocus();
        Object focusTag = focused == null ? null : focused.getTag();
        sources.removeAllViews();
        for (TvRecommendations.SourceStatus source : snapshot.sources)
            sources.addView(Pages.text(this, source.label + "\n" + source.message, 17));
        Map<String, String> apps = new LinkedHashMap<>(installed);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (TvRecommendations.Feed feed : snapshot.feeds) {
            if (!apps.containsKey(feed.source)) apps.put(feed.source, feed.source);
            Integer count = counts.get(feed.source);
            counts.put(feed.source, count == null ? 1 : count + 1);
        }
        if (inventoryUnavailable) sources.addView(Pages.text(this, "Installed TV apps could not be listed. Shared feeds are still shown when available.", 17));
        for (Map.Entry<String, String> app : apps.entrySet()) {
            final String source = app.getKey();
            Integer count = counts.get(source);
            String state = count != null ? count + (count == 1 ? " shared channel" : " shared channels")
                    : snapshot.status == TvRecommendations.Status.READY || snapshot.status == TvRecommendations.Status.EMPTY
                    ? "No shared recommendations in this snapshot" : "Sharing status unavailable";
            sources.addView(Pages.text(this, app.getValue() + "\n" + state, 18));
            Button open = Pages.button(this, "Open " + app.getValue(), new View.OnClickListener() {
                @Override public void onClick(View view) {
                    TvRecommendations.Program app = new TvRecommendations.Program("app", source, "", null, null);
                    RecommendationActions.Outcome result = RecommendationActions.openApp(RecommendationSourcesActivity.this, app);
                    if (result != RecommendationActions.Outcome.OPENED && result != RecommendationActions.Outcome.PENDING)
                        Toast.makeText(RecommendationSourcesActivity.this, "This app is unavailable.", Toast.LENGTH_SHORT).show();
                }
            });
            open.setTag("sources:open:" + source);
            sources.addView(open);
        }
        if (focusTag instanceof String) {
            View restore = sources.findViewWithTag(focusTag);
            if (restore != null) restore.requestFocus();
        }
        if (apps.isEmpty() && !inventoryUnavailable)
            sources.addView(Pages.text(this, "No installed TV apps or shared recommendation sources were found.", 17));
    }

    @Override protected void onResume() { super.onResume(); loader.start(); }
    @Override protected void onPause() { loader.stop(); super.onPause(); }
    @Override protected void onDestroy() { loader.close(); worker.shutdownNow(); super.onDestroy(); }
    @Override public void onRequestPermissionsResult(int request, String[] names, int[] results) {
        super.onRequestPermissionsResult(request, names, results);
        if (request == 1) loader.refresh();
    }
}
