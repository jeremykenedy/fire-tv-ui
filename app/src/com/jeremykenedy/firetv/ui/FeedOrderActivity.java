package com.jeremykenedy.firetv.ui;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

public final class FeedOrderActivity extends CardActivity {
    private LinearLayout body;
    private boolean changed;
    private String focusKey;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        title("Arrange top-level cards");
        body = scrollBody();
        load();
    }

    private void load() {
        body.removeAllViews();
        body.addView(text("Reading published card feeds...", 18, MUTED));
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final List<FeedOrder.Feed> feeds = FeedOrder.read(FeedOrderActivity.this);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (!isFinishing() && !isDestroyed()) render(feeds);
                        }
                    });
                } catch (final Exception unavailable) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (!isFinishing() && !isDestroyed()) {
                                body.removeAllViews();
                                body.addView(text("Card feeds could not be read. Refresh sources and try again.", 18, MUTED));
                            }
                        }
                    });
                }
            }
        }, "read-top-level-feeds").start();
    }

    private void render(final List<FeedOrder.Feed> feeds) {
        body.removeAllViews();
        body.addView(text("Move Plex, IPTV and other feed cards left or right on Home. This is separate"
                + " from ordering the items inside a feed. Hidden sources keep their position.", 18, MUTED));
        if (feeds.isEmpty()) body.addView(text("No card feeds have been published yet.", 18, MUTED));
        for (int i = 0; i < feeds.size(); i++) {
            final FeedOrder.Feed feed = feeds.get(i);
            body.addView(text((i + 1) + ". " + feed.label, 20, INK));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (final int direction : new int[]{-1, 1}) {
                TextView action = button(direction < 0 ? "Move left" : "Move right",
                        new android.view.View.OnClickListener() {
                            @Override public void onClick(android.view.View view) {
                                try {
                                    FeedOrder.move(FeedOrderActivity.this, feeds, feed, direction);
                                    changed = true;
                                    focusKey = feed.key;
                                    load();
                                } catch (RuntimeException unavailable) { error(unavailable); }
                            }
                        });
                action.setEnabled(direction < 0 ? i > 0 : i + 1 < feeds.size());
                row.addView(action, new LinearLayout.LayoutParams(0, -2, 1));
                if (feed.key.equals(focusKey) && action.isEnabled()) action.requestFocus();
            }
            body.addView(row);
        }
        addButton(body, button("Reset top-level order", new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View view) {
                try { FeedOrder.reset(FeedOrderActivity.this); changed = true; load(); }
                catch (RuntimeException unavailable) { error(unavailable); }
            }
        }));
    }

    @Override public void onBackPressed() {
        if (changed) FeaturedCardMenu.reload(this);
        finish();
    }
}
