package com.jeremykenedy.firetv.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.KeyEvent;

public final class FeaturedCardMenu {
    private FeaturedCardMenu() {}

    public static String label(String source) {
        String original = source == null ? "" : source;
        return original.endsWith("Menu: Card options") ? original
                : original + " | Menu: Card options";
    }

    public static boolean handle(
            Context context, KeyEvent event, String source, Uri intent, long channel) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_MENU) return false;
        if (event.getAction() != KeyEvent.ACTION_UP || event.isCanceled()) return true;
        final HiddenCards.Card card = HiddenCards.card(source, intent, channel < 0);
        if (card == null) {
            new AlertDialog.Builder(context)
                    .setTitle("Card options")
                    .setMessage(
                            "This source has not supplied a stable item identity for this card.")
                    .setPositiveButton("OK", null)
                    .show();
            return true;
        }
        final long feed = channel;
        final String[] options = feed >= 0
                ? new String[]{"Hide this card", "Next title in this feed", "Previous title in this feed", "Manage this feed and card order", "Choose feeds and card settings", "Arrange top-level cards", "Cancel"}
                : new String[]{"Hide this card", "Next title in this feed", "Previous title in this feed", "Choose feeds and card settings", "Arrange top-level cards", "Cancel"};
        FeaturedCardClock.pause();
        new AlertDialog.Builder(context)
                .setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                    @Override public void onDismiss(android.content.DialogInterface dialog) {
                        FeaturedCardClock.resume();
                    }
                })
                .setTitle(card.title)
                .setItems(
                        options,
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                String action = options[which];
                                if (action.equals("Next title in this feed") || action.equals("Previous title in this feed")) {
                                    FeaturedGroups.move(FeedOrder.key(context, card.source, feed),
                                            action.startsWith("Next") ? 1 : -1);
                                    FeaturedCardClock.changed();
                                    return;
                                }
                                if (action.equals("Manage this feed and card order")) {
                                    ChannelCardsActivity.open(context, "Current feed", card.source,
                                            java.util.Collections.singletonList(feed));
                                    return;
                                }
                                if (action.equals("Choose feeds and card settings")) {
                                    context.startActivity(new Intent(context, FeaturedCardsActivity.class));
                                    return;
                                }
                                if (action.equals("Arrange top-level cards")) {
                                    context.startActivity(new Intent(context, FeedOrderActivity.class));
                                    return;
                                }
                                if (which != 0) return;
                                try {
                                    HiddenCards.hide(context, card.key);
                                    reload(context);
                                } catch (IllegalStateException error) {
                                    new AlertDialog.Builder(context)
                                            .setTitle("Card could not be hidden")
                                            .setMessage(error.getMessage())
                                            .setPositiveButton("OK", null)
                                            .show();
                                }
                            }
                        })
                .show();
        return true;
    }

    static void reload(Context context) {
        Intent home =
                context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (home != null) {
            home.addCategory(Intent.CATEGORY_HOME);
            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(home);
        }
    }
}
