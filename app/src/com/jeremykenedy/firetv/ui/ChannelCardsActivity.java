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
    private String focusKey;

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
        cards = CardOrder.sorted(this, cards);
        final List<HiddenCards.Card> ordered = cards;
        body.removeAllViews();
        body.addView(
                text(
                        cards.isEmpty()
                                ? "This channel has no published cards."
                                : "Choose which cards appear and their order. Move a card up to show"
                                      + " it first on Home. Hidden cards stay here for restoration;"
                                      + " your watch history and other feeds stay unchanged.",
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
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.addView(action, new LinearLayout.LayoutParams(0, -2, 3));
            for (final int direction : new int[]{-1, 1}) {
                TextView move = button(direction < 0 ? "Move up" : "Move down",
                        new android.view.View.OnClickListener() {
                            @Override public void onClick(android.view.View view) {
                                try {
                                    CardOrder.move(ChannelCardsActivity.this, ordered, card, direction);
                                    changed = true;
                                    focusKey = card.key;
                                    render(ordered);
                                } catch (RuntimeException failure) { error(failure); }
                            }
                        });
                int position = 0, count = 0;
                for (HiddenCards.Card other : ordered) {
                    if (other.channel != card.channel || other.key == null) continue;
                    if (other.key.equals(card.key)) position = count;
                    count++;
                }
                move.setEnabled(direction < 0 ? position > 0 : position + 1 < count);
                actions.addView(move, new LinearLayout.LayoutParams(0, -2, 1));
            }
            body.addView(actions);
            if (card.key.equals(focusKey)) action.requestFocus();
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
                                        render(ordered);
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
