package com.jeremykenedy.firetv.ui;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

public final class FeaturedCardsActivity extends CardActivity {
    private LinearLayout sources;
    private TextView enabled;
    private TextView artwork;
    private TextView rotation;
    private TextView status;
    private int generation;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        title("Featured cards");
        LinearLayout body = scrollBody();
        body.addView(
                text(
                        "Show recommendations and Continue Watching published by compatible apps."
                                + " Amazon Home recommendations use a separate service. Installed"
                                + " Netflix or Prime apps do not guarantee a card feed here.",
                        18,
                        MUTED));
        enabled =
                button(
                        "",
                        new android.view.View.OnClickListener() {
                            @Override
                            public void onClick(android.view.View view) {
                                boolean hidden =
                                        DeviceSettings.prefs(FeaturedCardsActivity.this)
                                                .getBoolean("hide_watch_next", false);
                                if (DeviceSettings.prefs(FeaturedCardsActivity.this)
                                        .edit()
                                        .putBoolean("hide_watch_next", !hidden)
                                        .commit()) labels();
                                else status.setText("The card setting could not be saved.");
                            }
                        });
        addButton(body, enabled);
        addButton(body, button("Arrange top-level cards", new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View view) {
                startActivity(new Intent(FeaturedCardsActivity.this, FeedOrderActivity.class));
            }
        }));
        artwork =
                button(
                        "",
                        new android.view.View.OnClickListener() {
                            @Override
                            public void onClick(android.view.View view) {
                                boolean fit = fit(FeaturedCardsActivity.this);
                                if (DeviceSettings.prefs(FeaturedCardsActivity.this)
                                        .edit()
                                        .putBoolean("featured_artwork_fit", !fit)
                                        .commit()) labels();
                                else status.setText("The artwork setting could not be saved.");
                            }
                        });
        addButton(body, artwork);
        rotation =
                button(
                        "",
                        new android.view.View.OnClickListener() {
                            @Override
                            public void onClick(android.view.View view) {
                                rotationOptions();
                            }
                        });
        addButton(body, rotation);
        body.addView(text("Each app or channel stays in place and rotates its own visible titles. Manual only stops automatic title changes.", 17, MUTED));

        addButton(
                body,
                button(
                        "Hidden cards",
                        new android.view.View.OnClickListener() {
                            @Override
                            public void onClick(android.view.View view) {
                                startActivity(
                                        new Intent(
                                                FeaturedCardsActivity.this,
                                                HiddenCardsActivity.class));
                            }
                        }));
        addButton(
                body,
                button(
                        "Connect your own Plex library",
                        new android.view.View.OnClickListener() {
                            @Override
                            public void onClick(android.view.View view) {
                                startActivity(
                                        new Intent(
                                                FeaturedCardsActivity.this,
                                                PlexCardsActivity.class));
                            }
                        }));
        addButton(
                body,
                button(
                        "Refresh sources",
                        new android.view.View.OnClickListener() {
                            @Override
                            public void onClick(android.view.View view) {
                                readSources();
                            }
                        }));
        addButton(
                body,
                button(
                        "Source details and refresh",
                        new android.view.View.OnClickListener() {
                            @Override
                            public void onClick(android.view.View view) {
                                startActivity(
                                        new Intent(
                                                FeaturedCardsActivity.this,
                                                CardSourcesActivity.class));
                            }
                        }));
        addButton(
                body,
                button(
                        "Reload home cards",
                        new android.view.View.OnClickListener() {
                            @Override
                            public void onClick(android.view.View view) {
                                FeaturedCardMenu.reload(FeaturedCardsActivity.this);
                            }
                        }));
        status = text("Reading card sources...", 17, MUTED);
        body.addView(status);
        sources = new LinearLayout(this);
        sources.setOrientation(LinearLayout.VERTICAL);
        body.addView(sources);
        body.addView(
                text(
                        "Press Menu while a featured card is focused to hide only that card."
                                + " Changes are saved. Reload home cards to apply setup changes.",
                        17,
                        MUTED));
        labels();
        enabled.requestFocus();
        readSources();
    }

    public static boolean fit(android.content.Context context) {
        return DeviceSettings.prefs(context).getBoolean("featured_artwork_fit", true);
    }

    private void labels() {
        enabled.setText(
                "Featured cards: "
                        + (DeviceSettings.prefs(this).getBoolean("hide_watch_next", false)
                                ? "Off"
                                : "On"));
        artwork.setText("Artwork: " + (fit(this) ? "Fit entire image" : "Fill card (crop edges)"));
        rotation.setText(
                "Titles inside each card: "
                        + FeaturedCardTiming.label(
                                DeviceSettings.prefs(this).getInt(FeaturedCardTiming.SETTING, 8)));
    }

    private void rotationOptions() {
        String[] labels = new String[FeaturedCardTiming.OPTIONS.length + 1];
        int selected = labels.length - 1;
        int current =
                FeaturedCardTiming.seconds(
                        DeviceSettings.prefs(this).getInt(FeaturedCardTiming.SETTING, 8));
        for (int i = 0; i < FeaturedCardTiming.OPTIONS.length; i++) {
            labels[i] = FeaturedCardTiming.label(FeaturedCardTiming.OPTIONS[i]);
            if (FeaturedCardTiming.OPTIONS[i] == current) selected = i;
        }
        labels[labels.length - 1] = "Custom interval (seconds)";
        new android.app.AlertDialog.Builder(this)
                .setTitle("Titles inside each card")
                .setSingleChoiceItems(
                        labels,
                        selected,
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                if (which == FeaturedCardTiming.OPTIONS.length) {
                                    dialog.dismiss();
                                    customRotation();
                                    return;
                                }
                                if (DeviceSettings.prefs(FeaturedCardsActivity.this)
                                        .edit()
                                        .putInt(
                                                FeaturedCardTiming.SETTING,
                                                FeaturedCardTiming.OPTIONS[which])
                                        .commit()) {
                                    labels();
                                    dialog.dismiss();
                                } else status.setText("The rotation setting could not be saved.");
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void customRotation() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setHint("Seconds (1 to 86400)");
        input.setContentDescription("Card rotation interval in seconds");
        int current = FeaturedCardTiming.seconds(DeviceSettings.prefs(this)
                .getInt(FeaturedCardTiming.SETTING, 8));
        if (current > 0) input.setText(String.valueOf(current));
        input.selectAll();
        final android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("Custom card interval")
                .setMessage("Seconds between title changes inside each card (1 to 86400).")
                .setView(input).setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null).create();
        dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override public void onShow(android.content.DialogInterface ignored) {
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                        new android.view.View.OnClickListener() {
                    @Override public void onClick(android.view.View view) {
                        int seconds;
                        try {
                            seconds = Integer.parseInt(input.getText().toString().trim());
                            if (seconds < FeaturedCardTiming.MIN_CUSTOM_SECONDS
                                    || seconds > FeaturedCardTiming.MAX_CUSTOM_SECONDS) {
                                throw new NumberFormatException();
                            }
                        } catch (NumberFormatException invalid) {
                            input.setError("Enter whole seconds from 1 to 86400.");
                            input.requestFocus();
                            return;
                        }
                        if (DeviceSettings.prefs(FeaturedCardsActivity.this).edit()
                                .putInt(FeaturedCardTiming.SETTING, seconds).commit()) {
                            labels();
                            dialog.dismiss();
                            rotation.requestFocus();
                        } else input.setError("Could not save. Try again.");
                    }
                });
            }
        });
        dialog.show();
    }

    private void readSources() {
        final int request = ++generation;
        status.setText("Reading card sources...");
        new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                Map<String, Integer> counts = new LinkedHashMap<>();
                                int failed = 0;
                                for (String table :
                                        new String[] {"preview_program", "watch_next_program"}) {
                                    try (Cursor cursor =
                                            getContentResolver()
                                                    .query(
                                                            Uri.parse(
                                                                    "content://android.media.tv/"
                                                                            + table),
                                                            new String[] {
                                                                "package_name",
                                                                "title",
                                                                "internal_provider_id",
                                                                "content_id",
                                                                "intent_uri"
                                                            },
                                                            null,
                                                            null,
                                                            null)) {
                                        if (cursor == null) {
                                            failed++;
                                            continue;
                                        }
                                        int column = cursor.getColumnIndexOrThrow("package_name");
                                        while (cursor.moveToNext()) {
                                            String source = cursor.getString(column);
                                            if (source == null || source.isEmpty()) continue;
                                            Integer old = counts.get(source);
                                            counts.put(source, old == null ? 1 : old + 1);
                                            HiddenCards.visible(
                                                    FeaturedCardsActivity.this,
                                                    cursor,
                                                    "watch_next_program".equals(table));
                                        }
                                    } catch (Exception unavailable) {
                                        failed++;
                                    }
                                }
                                final int unavailable = failed;
                                runOnUiThread(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                if (isFinishing()
                                                        || isDestroyed()
                                                        || request != generation) return;
                                                sources.removeAllViews();
                                                status.setText(
                                                        unavailable > 0
                                                                ? "Some sources could not be read."
                                                                        + " Open Source details for"
                                                                        + " permission or provider"
                                                                        + " errors."
                                                                : counts.isEmpty()
                                                                        ? "No compatible cards have"
                                                                              + " been published."
                                                                              + " Open a compatible"
                                                                              + " app and use its"
                                                                              + " recent channels"
                                                                              + " or recommendations,"
                                                                              + " then refresh."
                                                                              + " Your own Plex"
                                                                              + " library can be"
                                                                              + " connected"
                                                                              + " separately."
                                                                        : "Published sources. Show"
                                                                              + " or hide a source"
                                                                              + " without changing"
                                                                              + " its app tile:");
                                                for (Map.Entry<String, Integer> item :
                                                        counts.entrySet())
                                                    source(item.getKey(), item.getValue());
                                            }
                                        });
                            }
                        },
                        "read-featured-card-setup")
                .start();
    }

    private void source(String name, int count) {
        String label;
        try {
            label =
                    getPackageManager()
                            .getApplicationLabel(getPackageManager().getApplicationInfo(name, 0))
                            .toString();
        } catch (android.content.pm.PackageManager.NameNotFoundException missing) {
            label = "Unavailable app";
        }
        final String title = label + ": " + count + " published cards";
        TextView source =
                button(
                        title + (sourceHidden(name) ? " (hidden)" : " (shown)"),
                        new android.view.View.OnClickListener() {
                            @Override
                            public void onClick(android.view.View view) {
                                try {
                                    JSONObject all =
                                            new JSONObject(
                                                    DeviceSettings.prefs(FeaturedCardsActivity.this)
                                                            .getString("app_visibility", "{}"));
                                    JSONObject app = all.optJSONObject(name);
                                    if (app == null) app = new JSONObject();
                                    boolean hidden = app.optBoolean("hideWatchNext", false);
                                    app.put("hideWatchNext", !hidden);
                                    all.put(name, app);
                                    if (!DeviceSettings.prefs(FeaturedCardsActivity.this)
                                            .edit()
                                            .putString("app_visibility", all.toString())
                                            .commit()) throw new IllegalStateException();
                                    ((TextView) view)
                                            .setText(title + (!hidden ? " (hidden)" : " (shown)"));
                                } catch (Exception unavailable) {
                                    status.setText(
                                            "The source change could not be saved. Existing app"
                                                    + " settings were kept.");
                                }
                            }
                        });
        addButton(sources, source);
    }

    private boolean sourceHidden(String name) {
        try {
            JSONObject value =
                    new JSONObject(DeviceSettings.prefs(this).getString("app_visibility", "{}"))
                            .optJSONObject(name);
            return value != null && value.optBoolean("hideWatchNext", false);
        } catch (Exception unavailable) {
            return false;
        }
    }
}
