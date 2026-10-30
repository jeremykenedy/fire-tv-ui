package com.jeremykenedy.firetv.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CardTimersActivity extends CardActivity {
    private TextView global;
    private LinearLayout channels;
    private final Map<String, TextView> controls = new LinkedHashMap<>();
    private final Map<String, String> names = new LinkedHashMap<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        title("Card timers");
        LinearLayout body = scrollBody();
        body.addView(text("Choose when titles change inside each channel card. The global timer applies unless a channel has its own timer. Manual only keeps the current title until you change it.", 18, MUTED));
        global = button("", new View.OnClickListener() {
            @Override public void onClick(View view) { choose(null, "Global timer", global); }
        });
        addButton(body, global);
        addButton(body, button("Use global timer for all channels", new View.OnClickListener() {
            @Override public void onClick(View view) {
                new AlertDialog.Builder(CardTimersActivity.this).setTitle("Use global timer for all channels?")
                        .setMessage("This removes individual channel timers. The global timer stays unchanged.")
                        .setNegativeButton("Cancel", null).setPositiveButton("Use global", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (DeviceSettings.prefs(CardTimersActivity.this).edit().remove(FeaturedCardTiming.OVERRIDES).commit()) refresh();
                        else error(new IllegalStateException("The timers could not be saved. Try again."));
                    }
                }).show();
            }
        }));
        channels = new LinearLayout(this);
        channels.setOrientation(LinearLayout.VERTICAL);
        body.addView(channels);
        channels.addView(text("Reading channel cards...", 17, MUTED));
        refresh();
        global.requestFocus();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final List<FeedOrder.Feed> feeds = FeedOrder.read(CardTimersActivity.this);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (isFinishing() || isDestroyed()) return;
                            channels.removeAllViews();
                            if (feeds.isEmpty()) channels.addView(text("No channel cards have been published yet. New channels use the global timer automatically.", 17, MUTED));
                            for (final FeedOrder.Feed feed : feeds) {
                                TextView action = button("", new View.OnClickListener() {
                                    @Override public void onClick(View view) { choose(feed.key, feed.label, (TextView) view); }
                                });
                                controls.put(feed.key, action); names.put(feed.key, feed.label);
                                addButton(channels, action);
                            }
                            refresh();
                        }
                    });
                } catch (Exception unavailable) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (isFinishing() || isDestroyed()) return;
                            channels.removeAllViews();
                            channels.addView(text("Channel cards could not be read. The global timer is still available. Open Source details to check access, then return here.", 17, MUTED));
                        }
                    });
                }
            }
        }, "read-card-timers").start();
    }

    private void refresh() {
        android.content.SharedPreferences prefs = DeviceSettings.prefs(this);
        global.setText("Global timer: " + FeaturedCardTiming.label(prefs.getInt(FeaturedCardTiming.SETTING, 8)));
        Map<String, Integer> custom = FeaturedCardTiming.overrides(prefs);
        for (Map.Entry<String, TextView> entry : controls.entrySet()) {
            String feed = entry.getKey();
            entry.getValue().setText(names.get(feed) + "\n" + (custom.containsKey(feed) ? "Own timer: " : "Use global: ")
                    + FeaturedCardTiming.label(FeaturedCardTiming.seconds(prefs, feed)));
        }
    }

    private void choose(final String feed, final String title, final TextView control) {
        final int offset = feed == null ? 0 : 1;
        String[] labels = new String[FeaturedCardTiming.OPTIONS.length + offset + 1];
        if (offset == 1) labels[0] = "Use global: " + FeaturedCardTiming.label(DeviceSettings.prefs(this).getInt(FeaturedCardTiming.SETTING, 8));
        int current = FeaturedCardTiming.seconds(DeviceSettings.prefs(this), feed), selected = labels.length - 1;
        for (int i = 0; i < FeaturedCardTiming.OPTIONS.length; i++) {
            labels[i + offset] = FeaturedCardTiming.label(FeaturedCardTiming.OPTIONS[i]);
            if (FeaturedCardTiming.OPTIONS[i] == current) selected = i + offset;
        }
        if (feed != null && !FeaturedCardTiming.overrides(DeviceSettings.prefs(this)).containsKey(feed)) selected = 0;
        labels[labels.length - 1] = "Custom interval (seconds)";
        new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels, selected, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (which == FeaturedCardTiming.OPTIONS.length + offset) {
                    dialog.dismiss(); custom(feed, title, control);
                } else if (save(feed, offset == 1 && which == 0 ? null : FeaturedCardTiming.OPTIONS[which - offset])) {
                    dialog.dismiss(); control.requestFocus();
                }
            }
        }).setNegativeButton("Cancel", null).show();
    }

    private void custom(final String feed, String title, final TextView control) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setHint("Seconds (1 to 86400)");
        int current = FeaturedCardTiming.seconds(DeviceSettings.prefs(this), feed);
        if (current > 0) input.setText(Integer.toString(current));
        input.selectAll();
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title)
                .setMessage("Enter whole seconds from 1 to 86400. Choose Manual only to stop automatic changes.")
                .setView(input).setPositiveButton("Save", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        try {
                            int seconds = Integer.parseInt(input.getText().toString().trim());
                            if (seconds < 1 || seconds > FeaturedCardTiming.MAX_CUSTOM_SECONDS) throw new NumberFormatException();
                            if (save(feed, seconds)) { dialog.dismiss(); control.requestFocus(); }
                        } catch (NumberFormatException invalid) {
                            input.setError("Enter whole seconds from 1 to 86400."); input.requestFocus();
                        }
                    }
                });
            }
        });
        dialog.show();
    }

    private boolean save(String feed, Integer seconds) {
        try {
            FeaturedCardTiming.save(DeviceSettings.prefs(this), feed, seconds);
            refresh();
            return true;
        } catch (RuntimeException failure) { error(failure); return false; }
    }
}
