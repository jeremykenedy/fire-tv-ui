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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CardsSettingsActivity extends Activity {
    private static final int[] PRESETS = {0, 5, 8, 15, 30};
    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    private List<TvRecommendations.Feed> feeds = Collections.emptyList();
    private String status = "Reading channel cards...";
    private String section = "cards";
    private CardOptions options;
    private LinearLayout content;
    private String focusTag;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) section = state.getString("section", "cards");
        options = CardPreferences.load(this);
        render(null);
        reader.execute(new Runnable() {
            @Override public void run() {
                final TvRecommendations.Result result = TvRecommendations.read(CardsSettingsActivity.this);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (isFinishing() || isDestroyed()) return;
                        feeds = result.feeds;
                        status = result.message();
                        if (section.equals("timers")) render(currentFocusTag());
                    }
                });
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        options = CardPreferences.load(this);
        if (content != null) render(focusTag);
    }

    @Override protected void onPause() {
        focusTag = currentFocusTag();
        super.onPause();
    }

    @Override protected void onDestroy() { reader.shutdownNow(); super.onDestroy(); }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("section", section);
        super.onSaveInstanceState(state);
    }

    @Override public void onBackPressed() {
        if (section.equals("cards")) super.onBackPressed();
        else {
            String previous = section;
            section = "cards";
            render(previous);
        }
    }

    private void render(String target) {
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, section.equals("timers") ? "Card timers" : section.equals("hidden") ? "Hidden items" : "Cards", 30));
        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        if (section.equals("cards")) {
            action("Channel Cards", "channels", new View.OnClickListener() {
                @Override public void onClick(View view) {
                    focusTag = "channels";
                    startActivity(new Intent(CardsSettingsActivity.this, LayoutSettingsActivity.class));
                }
            });
            action("Card timers", "timers", new View.OnClickListener() {
                @Override public void onClick(View view) { section = "timers"; render(null); }
            });
            action("Hidden items (" + options.hiddenItems().size() + ")", "hidden", new View.OnClickListener() {
                @Override public void onClick(View view) { section = "hidden"; render(null); }
            });
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
            content.addView(Pages.text(this, "Hidden items stay hidden for their publishing source. Restore an item to allow it to appear again when its source shares it.", 18));
            if (options.hiddenItems().isEmpty()) content.addView(Pages.text(this, "No hidden items. Open a card's actions to hide an item.", 18));
            for (final CardOptions.HiddenItem item : options.hiddenItems()) {
                action("Restore: " + (item.title.isEmpty() ? "Untitled item" : item.title) + "\n" + item.source,
                        item.source + ":" + item.key, new View.OnClickListener() {
                    @Override public void onClick(View view) { save(options.restore(item.source, item.key), null); }
                });
            }
        }
        View focus = target == null ? null : content.findViewWithTag(target);
        if (focus != null) focus.requestFocus();
        else if (content.getChildCount() > 0) {
            for (int i = 0; i < content.getChildCount(); i++) if (content.getChildAt(i).isFocusable()) {
                content.getChildAt(i).requestFocus(); break;
            }
        }
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
        } catch (IllegalStateException failed) {
            Toast.makeText(this, failed.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }
}
