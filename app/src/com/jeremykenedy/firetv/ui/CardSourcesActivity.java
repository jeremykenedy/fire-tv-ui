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
        body.addView(text("Fire TV recommendations inside Amazon Home use a separate service. Only Android TV cards published by apps appear here. Widgets are a separate Appearance option.", 17, MUTED));
        refresh = button("Refresh sources", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                readSources();
            }
        });
        addButton(body, refresh);
        addButton(body, button("Reload home cards", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent home = getPackageManager().getLaunchIntentForPackage(getPackageName());
                if (home != null) {
                    home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(home);
                    finish();
                }
            }
        }));
        refresh.requestFocus();
        readSources();
    }

    private void readSources() {
        final int request = ++generation;
        refresh.setEnabled(false);
        report.setText("Reading card sources...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Source preview = read("preview_program");
                final Source watchNext = read("watch_next_program");
                final Source channels = read("channel");
                final boolean hidden = DeviceSettings.prefs(CardSourcesActivity.this)
                        .getBoolean("hide_watch_next", false);
                final String value = CardSourceStatus.summary(hidden, preview.count,
                        watchNext.count, preview.error != null || watchNext.error != null)
                        + "\n\n" + preview.describe("Preview cards")
                        + "\n\n" + watchNext.describe("Continue Watching")
                        + "\n\n" + channels.describe("TV channels (includes live TV, not only card sources)");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing() && !isDestroyed() && request == generation) {
                            report.setText(value);
                            refresh.setEnabled(true);
                        }
                    }
                });
            }
        }, "read-card-sources").start();
    }

    private Source read(String table) {
        Source source = new Source();
        try (Cursor cursor = getContentResolver().query(
                Uri.parse("content://android.media.tv/" + table),
                new String[] {"_id", "package_name"}, null, null, null)) {
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
            source.error = "The TV provider could not read this source ("
                    + error.getClass().getSimpleName() + ").";
        }
        return source;
    }

    private static final class Source {
        int count;
        String error;
        final Map<String, Integer> packages = new LinkedHashMap<>();

        String describe(String label) {
            if (error != null) {
                return label + ": unavailable. " + error;
            }
            StringBuilder value = new StringBuilder(label).append(": ").append(count);
            for (Map.Entry<String, Integer> item : packages.entrySet()) {
                value.append("\n").append(item.getKey()).append(": ").append(item.getValue());
            }
            return value.toString();
        }
    }
}
