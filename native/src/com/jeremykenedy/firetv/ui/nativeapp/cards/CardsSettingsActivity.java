package com.jeremykenedy.firetv.ui.nativeapp.cards;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutSettingsActivity;
import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;
import com.jeremykenedy.firetv.ui.nativeapp.providers.RecommendationLoader;
import java.util.Collections;
import java.util.List;

public final class CardsSettingsActivity extends com.jeremykenedy.firetv.ui.nativeapp.MenuActivity {
    private boolean startupBlocked;
    private static final int[] PRESETS = {0, 5, 8, 15, 30};
    private RecommendationLoader recommendations;
    private List<TvRecommendations.Feed> feeds = Collections.emptyList();
    private String status = "Reading channel cards...";
    private String section = "cards";
    private String selectedFeed;
    private CardOptions options;
    private LinearLayout content;
    private String focusTag;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        if (state != null) { section = state.getString("section", "cards"); selectedFeed = state.getString("feed"); }
        options = CardPreferences.load(this);
        render(null);
        recommendations = new RecommendationLoader(this, new RecommendationLoader.Listener() {
            @Override public void onRecommendations(TvRecommendations.Result result) {
                feeds = result.feeds;
                status = result.message();
                if (!section.equals("cards") && !section.equals("hidden")) render(currentFocusTag());
            }
        });
    }

    @Override protected void onResume() {
        if (startupBlocked) { super.onResume(); return; }
        super.onResume();
        options = CardPreferences.load(this);
        if (content != null) render(focusTag);
        recommendations.start();
    }

    @Override protected void onPause() {
        if (startupBlocked) { super.onPause(); return; }
        focusTag = currentFocusTag();
        recommendations.stop();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (startupBlocked) { super.onDestroy(); return; } recommendations.close(); super.onDestroy(); }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (startupBlocked) { super.onSaveInstanceState(state); return; }
        state.putString("section", section);
        state.putString("feed", selectedFeed);
        super.onSaveInstanceState(state);
    }

    @Override public void onBackPressed() {
        if (startupBlocked) { super.onBackPressed(); return; }
        if (section.equals("cards")) super.onBackPressed();
        else if (section.equals("items")) { section = "channels"; render(selectedFeed); }
        else {
            String previous = section;
            section = "cards";
            render(previous);
        }
    }

    private void render(String target) {
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, section.equals("timers") ? "Card timers" : section.equals("hidden") ? "Hidden items"
                : section.equals("channels") ? "Channel Cards" : section.equals("items") ? "Channel titles" : "Cards", 30));
        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        if (section.equals("cards")) {
            action("Channel Cards", "channels", new View.OnClickListener() {
                @Override public void onClick(View view) {
                    section = "channels";
                    render(null);
                }
            });
            action("Card timers", "timers", new View.OnClickListener() {
                @Override public void onClick(View view) { section = "timers"; render(null); }
            });
            action("Hidden items and series (" + (options.hiddenItems().size() + options.hiddenSeries().size()) + ")", "hidden", new View.OnClickListener() {
                @Override public void onClick(View view) { section = "hidden"; render(null); }
            });
        } else if (section.equals("channels")) {
            action("Artwork: " + (CardPreferences.artworkFits(this) ? "Fit entire image" : "Fill and crop"),
                    "artwork-fit", new View.OnClickListener() {
                @Override public void onClick(View view) {
                    try {
                        CardPreferences.setArtworkFit(CardsSettingsActivity.this,
                                !CardPreferences.artworkFits(CardsSettingsActivity.this));
                        render("artwork-fit");
                    } catch (IllegalStateException failed) {
                        Toast.makeText(CardsSettingsActivity.this, failed.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            });
            action("Card size, channel order and visibility", "layout", new View.OnClickListener() {
                @Override public void onClick(View view) {
                    focusTag = "layout";
                    startActivity(new Intent(CardsSettingsActivity.this, LayoutSettingsActivity.class));
                }
            });
            content.addView(Pages.text(this, "Choose a channel to change its title order or hide items and supported series.", 18));
            content.addView(Pages.text(this, status, 17));
            for (final TvRecommendations.Feed feed : feeds) {
                action(feed.title + "\n" + feed.source, feed.item.id, new View.OnClickListener() {
                    @Override public void onClick(View view) { selectedFeed = feed.item.id; section = "items"; render(null); }
                });
            }
        } else if (section.equals("items")) {
            renderItems();
        } else if (section.equals("timers")) {
            content.addView(Pages.text(this, "Titles change inside each channel card. Manual only keeps the current title until you choose the next item.", 18));
            timer(null, "Global timer");
            content.addView(Pages.text(this, status, 17));
            for (TvRecommendations.Feed feed : feeds) timer(feed.item.id, feed.title + "\n" + feed.source);
            for (String feed : options.feedSeconds.keySet()) {
                boolean available = false;
                for (TvRecommendations.Feed current : feeds) if (current.item.id.equals(feed)) available = true;
                if (!available) timer(feed, "Unavailable channel\n" + feed);
            }
        } else {
            content.addView(Pages.text(this, "Hidden items and series stay scoped to their publishing source. Restoring a series keeps independently hidden episodes hidden. Restoring an item does not override a hidden series.", 18));
            if (options.hiddenItems().isEmpty() && options.hiddenSeries().isEmpty()) content.addView(Pages.text(this, "No hidden items or series. Open a card's actions or Channel Cards to hide an item.", 18));
            for (final CardOptions.HiddenItem item : options.hiddenItems()) {
                action("Restore: " + (item.title.isEmpty() ? "Untitled item" : item.title) + "\n" + item.source,
                        item.source + ":" + item.key, new View.OnClickListener() {
                    @Override public void onClick(View view) { save(options.restore(item.source, item.key), null); }
                });
            }
            for (final CardOptions.HiddenItem series : options.hiddenSeries()) {
                action("Restore series: " + series.title + "\n" + series.source, "series:" + series.source + ":" + series.key,
                        new View.OnClickListener() {
                    @Override public void onClick(View view) { save(options.restoreSeries(series.source, series.key), null); }
                });
            }
        }
        View focus = target == null ? null : content.findViewWithTag(target);
        if (focus != null && !focus.isEnabled() && target != null && (target.endsWith(":up") || target.endsWith(":down")))
            focus = content.findViewWithTag(target.substring(0, target.lastIndexOf(':')) + ":item");
        if (focus != null && focus.isEnabled()) focus.requestFocus();
        else if (content.getChildCount() > 0) {
            for (int i = 0; i < content.getChildCount(); i++) if (content.getChildAt(i).isFocusable() && content.getChildAt(i).isEnabled()) {
                content.getChildAt(i).requestFocus(); break;
            }
        }
    }

    private void renderItems() {
        TvRecommendations.Feed selected = null;
        for (TvRecommendations.Feed feed : feeds) if (feed.item.id.equals(selectedFeed)) selected = feed;
        if (selected == null) {
            content.addView(Pages.text(this, "This channel is unavailable. Its saved title order and hidden preferences are retained.", 18));
            return;
        }
        final TvRecommendations.Feed feed = selected;
        content.addView(Pages.text(this, feed.title + "\n" + feed.source, 20));
        content.addView(Pages.text(this, "Order controls include hidden titles. New titles append; missing titles keep their saved position when they return.", 17));
        action("Reset title order to source order", "reset-order", new View.OnClickListener() {
            @Override public void onClick(View view) { save(options.resetItemOrder(feed.source, feed.item.id), "reset-order"); }
        });
        final List<TvRecommendations.Program> programs = CardPreferences.orderedPrograms(options, feed);
        for (int index = 0; index < programs.size(); index++) {
            final int position = index;
            final TvRecommendations.Program program = programs.get(index);
            boolean itemHidden = CardPreferences.itemHidden(options, program);
            boolean seriesHidden = options.isSeriesHidden(program.source, program.seriesKey);
            content.addView(Pages.text(this, (index + 1) + ". " + program.title + "\n"
                    + (itemHidden ? "Item hidden" : seriesHidden ? "Hidden by series" : "Shown"), 19));
            action(itemHidden ? "Restore this item" : "Hide this item", program.key + ":item", new View.OnClickListener() {
                @Override public void onClick(View view) {
                    save(CardPreferences.itemHidden(options, program) ? CardPreferences.restoreItem(options, program)
                            : options.hide(program.source, program.key, program.title), program.key + ":item");
                }
            });
            if (program.seriesKey != null && !program.seriesKey.isEmpty()) {
                action(seriesHidden ? "Restore series" : "Hide series", program.key + ":series", new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        String label = program.seriesTitle == null || program.seriesTitle.isEmpty()
                                ? "Series containing " + program.title : program.seriesTitle;
                        if (label.length() > 512) label = label.substring(0, 512);
                        save(options.isSeriesHidden(program.source, program.seriesKey)
                                ? options.restoreSeries(program.source, program.seriesKey)
                                : options.hideSeries(program.source, program.seriesKey, label), program.key + ":series");
                    }
                });
            } else content.addView(Pages.text(this, "Series hiding is unavailable: this source has not supplied a trusted series identity. You can hide this item.", 16));
            Button up = Pages.button(this, "Move title up", new View.OnClickListener() {
                @Override public void onClick(View view) {
                    CardOptions value = options.discoverItems(feed.source, feed.item.id, CardPreferences.programKeys(feed));
                    save(value.moveItemBefore(feed.source, feed.item.id, program.key, programs.get(position - 1).key), program.key + ":up");
                }
            });
            up.setTag(program.key + ":up"); up.setEnabled(index > 0); content.addView(up);
            Button down = Pages.button(this, "Move title down", new View.OnClickListener() {
                @Override public void onClick(View view) {
                    CardOptions value = options.discoverItems(feed.source, feed.item.id, CardPreferences.programKeys(feed));
                    String before = position + 2 < programs.size() ? programs.get(position + 2).key : null;
                    save(value.moveItemBefore(feed.source, feed.item.id, program.key, before), program.key + ":down");
                }
            });
            down.setTag(program.key + ":down"); down.setEnabled(index + 1 < programs.size()); content.addView(down);
        }
        if (programs.isEmpty()) content.addView(Pages.text(this, "This channel currently shares no titles. Saved preferences are retained.", 18));
    }

    private String currentFocusTag() {
        View view = getCurrentFocus();
        return view != null && view.getTag() instanceof String ? (String) view.getTag() : null;
    }

    private void action(String label, String tag, View.OnClickListener listener) {
        Button button = Pages.button(this, label, listener);
        button.setTag(tag);
        content.addView(button);
    }

    private void timer(final String feed, final String title) {
        String label = feed == null ? title + ": " + CardOptions.timerLabel(options.globalSeconds)
                : title + "\n" + (options.feedSeconds.containsKey(feed) ? "Own timer: " : "Use global: ")
                    + CardOptions.timerLabel(options.timerSeconds(feed));
        action(label, feed == null ? "global" : feed, new View.OnClickListener() {
            @Override public void onClick(View view) { chooseTimer(feed, title); }
        });
    }

    private void chooseTimer(final String feed, String title) {
        final int offset = feed == null ? 0 : 1;
        String[] labels = new String[PRESETS.length + offset + 1];
        if (offset == 1) labels[0] = "Use global: " + CardOptions.timerLabel(options.globalSeconds);
        int selected = labels.length - 1;
        int seconds = feed == null ? options.globalSeconds : options.timerSeconds(feed);
        for (int i = 0; i < PRESETS.length; i++) {
            labels[i + offset] = CardOptions.timerLabel(PRESETS[i]);
            if (seconds == PRESETS[i]) selected = i + offset;
        }
        if (feed != null && !options.feedSeconds.containsKey(feed)) selected = 0;
        labels[labels.length - 1] = "Custom interval (seconds)";
        new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels, selected, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (which == PRESETS.length + offset) { dialog.dismiss(); customTimer(feed); }
                else {
                    CardOptions value = offset == 1 && which == 0 ? options.useGlobal(feed)
                            : feed == null ? options.withGlobalSeconds(PRESETS[which])
                            : options.withFeedSeconds(feed, PRESETS[which - offset]);
                    if (save(value, feed == null ? "global" : feed)) dialog.dismiss();
                }
            }
        }).setNegativeButton("Cancel", null).show();
    }

    private void customTimer(final String feed) {
        final EditText field = new EditText(this);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setSingleLine(true);
        field.setContentDescription("Whole seconds from 1 to 86400");
        field.setText(Integer.toString(Math.max(1, feed == null ? options.globalSeconds : options.timerSeconds(feed))));
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Custom timer: 1 to 86400 seconds")
                .setView(field).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        try {
                            String text = field.getText().toString();
                            if (!text.matches("[1-9][0-9]{0,4}")) throw new IllegalArgumentException();
                            int value = Integer.parseInt(text);
                            if (save(feed == null ? options.withGlobalSeconds(value) : options.withFeedSeconds(feed, value),
                                    feed == null ? "global" : feed)) dialog.dismiss();
                        } catch (IllegalArgumentException invalid) { field.setError("Enter whole seconds from 1 to 86400."); }
                    }
                });
            }
        });
        dialog.show();
    }

    private boolean save(CardOptions value, String target) {
        try {
            CardPreferences.save(this, value);
            options = value;
            render(target);
            return true;
        } catch (IllegalStateException | IllegalArgumentException failed) {
            Toast.makeText(this, failed.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }
}
