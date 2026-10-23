package com.jeremykenedy.firetv.ui;

import android.os.Bundle;
import android.widget.LinearLayout;

public final class HiddenCardsActivity extends CardActivity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        title("Hidden cards");
        LinearLayout body = scrollBody();
        body.addView(text("Reading hidden cards...", 18, MUTED));
        new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                final boolean available =
                                        HiddenCards.scan(HiddenCardsActivity.this);
                                runOnUiThread(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                if (isFinishing() || isDestroyed()) return;
                                                body.removeAllViews();
                                                if (!available)
                                                    body.addView(
                                                            text(
                                                                    "Some sources could not be"
                                                                        + " read. You can still"
                                                                        + " restore their hidden"
                                                                        + " card choices.",
                                                                    17,
                                                                    MUTED));
                                                render(body);
                                            }
                                        });
                            }
                        },
                        "read-hidden-card-labels")
                .start();
    }

    private void render(LinearLayout body) {
        java.util.Set<String> hidden = HiddenCards.keys(this);
        body.addView(
                text(
                        hidden.isEmpty()
                                ? "No individual cards are hidden."
                                : "Restoring a card leaves its source and watch history unchanged."
                                      + " A removed item can be restored here even when its source"
                                      + " no longer publishes it.",
                        18,
                        MUTED));
        for (String key : hidden) {
            addButton(
                    body,
                    button(
                            "Restore " + HiddenCards.label(key),
                            new android.view.View.OnClickListener() {
                                @Override
                                public void onClick(android.view.View view) {
                                    restore(key);
                                }
                            }));
        }
        if (!hidden.isEmpty())
            addButton(
                    body,
                    button(
                            "Restore all hidden cards",
                            new android.view.View.OnClickListener() {
                                @Override
                                public void onClick(android.view.View view) {
                                    restore(null);
                                }
                            }));
    }

    private void restore(String key) {
        try {
            if (key == null) HiddenCards.restoreAll(this);
            else HiddenCards.restore(this, key);
            FeaturedCardMenu.reload(this);
        } catch (IllegalStateException failure) {
            error(failure);
        }
    }
}
