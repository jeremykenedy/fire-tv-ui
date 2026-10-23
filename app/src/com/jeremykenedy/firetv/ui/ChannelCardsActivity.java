package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ChannelCardsActivity extends CardActivity {
    private LinearLayout body;
    private boolean changed;

    public static void open(Context context, String name, String source, List<Long> ids) {
        long[] channels = new long[ids.size()];
        for (int i = 0; i < channels.length; i++) channels[i] = ids.get(i);
        context.startActivity(
                new Intent(context, ChannelCardsActivity.class)
                        .putExtra("channel_name", name)
                        .putExtra("source", source)
                        .putExtra("channels", channels));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        title("Manage cards: " + getIntent().getStringExtra("channel_name"));
        body = scrollBody();
        body.addView(text("Reading this channel's cards...", 18, MUTED));
        new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    final List<HiddenCards.Card> cards =
                                            ChannelCards.read(
                                                    ChannelCardsActivity.this,
                                                    getIntent().getStringExtra("source"),
                                                    getIntent().getLongArrayExtra("channels"));
                                    runOnUiThread(
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (!isFinishing() && !isDestroyed())
                                                        render(cards);
                                                }
                                            });
                                } catch (Exception unavailable) {
                                    runOnUiThread(
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (isFinishing() || isDestroyed()) return;
                                                    body.removeAllViews();
                                                    body.addView(
                                                            text(
                                                                    "This source could not be read."
                                                                        + " Refresh sources and try"
                                                                        + " again.",
                                                                    18,
                                                                    MUTED));
                                                }
                                            });
                                }
                            }
                        },
                        "read-channel-cards")
                .start();
    }

    private void render(List<HiddenCards.Card> cards) {
        body.removeAllViews();
        body.addView(
                text(
                        cards.isEmpty()
                                ? "This channel has no published cards."
                                : "Hide individual cards without disabling this channel or changing"
                                      + " your watch history. Hidden cards stay in this list so you"
                                      + " can restore them.",
                        18,
                        MUTED));
        final Set<String> channelKeys = new HashSet<>();
        for (final HiddenCards.Card card : cards) {
            if (card.key == null) {
                body.addView(
                        text(
                                card.title
                                        + " - This source has not supplied a stable item identity.",
                                17,
                                MUTED));
                continue;
            }
            channelKeys.add(card.key);
            TextView action =
                    button(
                            label(card),
                            new android.view.View.OnClickListener() {
                                @Override
                                public void onClick(android.view.View view) {
                                    try {
                                        if (HiddenCards.keys(ChannelCardsActivity.this)
                                                .contains(card.key))
                                            HiddenCards.restore(
                                                    ChannelCardsActivity.this, card.key);
                                        else HiddenCards.hide(ChannelCardsActivity.this, card.key);
                                        changed = true;
                                        ((TextView) view).setText(label(card));
                                    } catch (IllegalStateException failure) {
                                        error(failure);
                                    }
                                }
                            });
            addButton(body, action);
        }
        if (!channelKeys.isEmpty())
            addButton(
                    body,
                    button(
                            "Restore all cards in this channel",
                            new android.view.View.OnClickListener() {
                                @Override
                                public void onClick(android.view.View view) {
                                    try {
                                        HiddenCards.restore(ChannelCardsActivity.this, channelKeys);
                                        changed = true;
                                        render(cards);
                                    } catch (IllegalStateException failure) {
                                        error(failure);
                                    }
                                }
                            }));
    }

    private String label(HiddenCards.Card card) {
        return (HiddenCards.keys(this).contains(card.key) ? "Restore " : "Hide ") + card.title;
    }

    @Override
    public void onBackPressed() {
        if (changed) FeaturedCardMenu.reload(this);
        finish();
    }
}
