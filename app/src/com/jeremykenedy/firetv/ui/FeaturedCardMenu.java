package com.jeremykenedy.firetv.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.KeyEvent;

public final class FeaturedCardMenu {
    private FeaturedCardMenu() {}

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
        new AlertDialog.Builder(context)
                .setTitle(card.title)
                .setItems(
                        new String[] {"Hide this card", "Cancel"},
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
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
