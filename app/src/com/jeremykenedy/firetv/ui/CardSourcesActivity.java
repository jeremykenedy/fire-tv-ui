package com.jeremykenedy.firetv.ui;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CardSourcesActivity extends CardActivity {
    private TextView report;
    private TextView refresh;
    private int generation;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        title("Featured card sources");
        LinearLayout body = scrollBody();
        report = text("Reading card sources...", 18, INK);
        body.addView(report);
        body.addView(
                text(
                        "Fire TV recommendations inside Amazon Home use a separate service. Only"
                                + " Android TV cards published by apps appear here. Widgets are a"
                                + " separate Appearance option.",
                        17,
                        MUTED));
        refresh =
                button(
                        "Refresh sources",
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                readSources();
                            }
                        });
        addButton(body, refresh);
        addButton(
                body,
                button(
                        "Reload home cards",
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                Intent home =
                                        getPackageManager()
                                                .getLaunchIntentForPackage(getPackageName());
                                if (home != null) {
                                    home.addCategory(Intent.CATEGORY_HOME);
                                    home.addFlags(
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                                                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                    startActivity(home);
                                    finish();
                                }
                            }
                        }));
        addButton(
                body,
                button(
                        "Connect your own Plex library",
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                startActivity(
                                        new Intent(
                                                CardSourcesActivity.this, PlexCardsActivity.class));
                            }
                        }));
        addButton(body, button("App recommendation cards", new View.OnClickListener() {
            @Override public void onClick(View view) {
                startActivity(new Intent(CardSourcesActivity.this, RecommendationCardsActivity.class));
            }
        }));
        refresh.requestFocus();
        readSources();
    }

    private void readSources() {
        final int request = ++generation;
        refresh.setClickable(false);
        report.setText("Reading card sources...");
        new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                final Source preview = read("preview_program");
                                final Source watchNext = read("watch_next_program");
                                final Source channels = read("channel");
                                final boolean hidden =
                                        DeviceSettings.prefs(CardSourcesActivity.this)
                                                .getBoolean("hide_watch_next", false);
                                final String value =
                                        CardSourceStatus.summary(
                                                        hidden,
                                                        preview.count,
                                                        watchNext.count,
                                                        preview.error != null
                                                                || watchNext.error != null)
                                                + "\n\n"
                                                + preview.describe(
                                                        "Featured cards", CardSourcesActivity.this)
                                                + "\n\n"
                                                + watchNext.describe(
                                                        "System Watch Next",
                                                        CardSourcesActivity.this)
                                                + "\n\n"
                                                + channels.describe(
                                                        "Live TV and app channels",
                                                        CardSourcesActivity.this)
                                                + "\n\n"
                                                + personalFeeds();
                                runOnUiThread(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                if (!isFinishing()
                                                        && !isDestroyed()
                                                        && request == generation) {
                                                    report.setText(value);
                                                    refresh.setClickable(true);
                                                }
                                            }
                                        });
                            }
                        },
                        "read-card-sources")
                .start();
    }

    private String personalFeeds() {
        Map<Long, String> feeds = new LinkedHashMap<>();
        Map<Long, Integer> counts = new LinkedHashMap<>();
        try {
            try (Cursor cursor =
                    getContentResolver()
                            .query(
                                    Uri.parse("content://android.media.tv/channel"),
                                    new String[] {"_id", "package_name", "internal_provider_id"},
                                    null,
                                    null,
                                    null)) {
                if (cursor == null) return "Your Plex library feeds: unavailable.";
                while (cursor.moveToNext()) {
                    if (!getPackageName().equals(cursor.getString(1))) continue;
                    String logical = cursor.getString(2);
                    String name =
                            "fire-tv-ui-plex-continue".equals(logical)
                                    ? "Plex Continue Watching"
                                    : "fire-tv-ui-plex-recent".equals(logical)
                                            ? "Plex Recently Added"
                                            : null;
                    if (name != null) {
                        feeds.put(cursor.getLong(0), name);
                        counts.put(cursor.getLong(0), 0);
                    }
                }
            }
            if (feeds.isEmpty())
                return "Your Plex library: no personal feeds have been published."
                        + " Connect your own server to add Continue Watching and Recently Added.";
            try (Cursor cursor =
                    getContentResolver()
                            .query(
                                    Uri.parse("content://android.media.tv/preview_program"),
                                    new String[] {"channel_id"},
                                    null,
                                    null,
                                    null)) {
                if (cursor == null) return "Your Plex library feeds: card counts unavailable.";
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    if (counts.containsKey(id)) counts.put(id, counts.get(id) + 1);
                }
            }
            StringBuilder report = new StringBuilder("Your Plex library feeds:");
            for (Map.Entry<Long, String> feed : feeds.entrySet()) {
                report.append("\n")
                        .append(feed.getValue())
                        .append(": ")
                        .append(counts.get(feed.getKey()))
                        .append(" published cards");
            }
            return report.toString();
        } catch (Exception unavailable) {
            return "Your Plex library feeds: unavailable. The TV provider could not read these"
                       + " feeds.";
        }
    }

    private Source read(String table) {
        Source source = new Source();
        try (Cursor cursor =
                getContentResolver()
                        .query(
                                Uri.parse("content://android.media.tv/" + table),
                                new String[] {"_id", "package_name"},
                                null,
                                null,
                                null)) {
            if (cursor == null) {
                source.error = "The TV provider returned no cursor.";
                return source;
            }
            int packageIndex = cursor.getColumnIndexOrThrow("package_name");
            while (cursor.moveToNext()) {
                source.count++;
                String name = cursor.getString(packageIndex);
                Integer previous = source.packages.get(name);
                source.packages.put(name, previous == null ? 1 : previous + 1);
            }
        } catch (SecurityException error) {
            source.error = "The TV provider did not permit this app to read this source.";
        } catch (Exception error) {
            source.error =
                    "The TV provider could not read this source ("
                            + error.getClass().getSimpleName()
                            + ").";
        }
        return source;
    }

    private static final class Source {
        int count;
        String error;
        final Map<String, Integer> packages = new LinkedHashMap<>();

        String describe(String label, android.content.Context context) {
            if (error != null) {
                return label + ": unavailable. " + error;
            }
            StringBuilder value = new StringBuilder(label).append(": ").append(count);
            for (Map.Entry<String, Integer> item : packages.entrySet()) {
                String name;
                try {
                    name =
                            context.getPackageManager()
                                    .getApplicationLabel(
                                            context.getPackageManager()
                                                    .getApplicationInfo(item.getKey(), 0))
                                    .toString();
                } catch (android.content.pm.PackageManager.NameNotFoundException unavailable) {
                    name = "An app that is no longer installed";
                }
                value.append("\n").append(name).append(": ").append(item.getValue());
            }
            return value.toString();
        }
    }
}
