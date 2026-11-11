package com.jeremykenedy.firetv.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import android.net.Uri;
import android.view.KeyEvent;

public final class FeaturedCardMenu {
    private FeaturedCardMenu() {}

    public static String label(String source) {
        return source == null ? "" : source;
    }

    public static boolean handle(
            Context context, KeyEvent event, String source, Uri intent, long channel,
            String key, String title, Runnable open) {
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_MENU && code != KeyEvent.KEYCODE_DPAD_CENTER
                && code != KeyEvent.KEYCODE_ENTER && code != KeyEvent.KEYCODE_NUMPAD_ENTER) return false;
        if (event.getAction() != KeyEvent.ACTION_UP || event.isCanceled()) return true;
        show(context, source, intent, channel, key, title, open);
        return true;
    }

    public static void show(Context context, String source, Uri intent, long channel,
            String key, String title, Runnable open) {
        final HiddenCards.Card card = HiddenCards.selected(key, source, title, channel);
        final String sourceOption = validSource(source) ? "Hide cards from " + FeedOrder.appName(context, source) : null;
        final long feed = channel;
        List<String> choices = new ArrayList<>();
        if (open != null) choices.add("Open");
        if (card != null) choices.add("Hide this title");
        if (sourceOption != null) choices.add(sourceOption);
        if (card != null) {
            choices.add("Next title in this feed");
            choices.add("Previous title in this feed");
            if (feed >= 0) choices.add("Manage this feed and card order");
        }
        choices.add("Choose feeds and card settings");
        choices.add("Arrange top-level cards");
        choices.add("Cancel");
        final String[] options = choices.toArray(new String[0]);
        FeaturedCardClock.pause();
        new AlertDialog.Builder(context)
                .setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                    @Override public void onDismiss(android.content.DialogInterface dialog) {
                        FeaturedCardClock.resume();
                    }
                })
                .setTitle(title == null || title.isEmpty() ? "Card options" : title)
                .setItems(
                        options,
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                String action = options[which];
                                if (action.equals("Open")) {
                                    open.run();
                                    return;
                                }
                                if (action.equals(sourceOption)) {
                                    try {
                                        hideSource(context, source);
                                        reload(context);
                                    } catch (IllegalStateException error) {
                                        new AlertDialog.Builder(context).setTitle("App cards could not be hidden")
                                                .setMessage(error.getMessage()).setPositiveButton("OK", null).show();
                                    }
                                    return;
                                }
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
                                if (!action.equals("Hide this title") || card == null) return;
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
    }

    private static boolean validSource(String source) {
        return source != null && source.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+");
    }

    static void hideSource(Context context, String source) {
        if (!validSource(source)) throw new IllegalStateException("This app's cards are unavailable.");
        SharedPreferences preferences = DeviceSettings.prefs(context);
        Object previous = preferences.getAll().get("app_visibility");
        if (previous != null && !(previous instanceof String))
            throw new IllegalStateException("The app card settings could not be read. Existing settings were kept.");
        final String updated;
        try {
            JSONObject all = new JSONObject(previous == null ? "{}" : (String) previous);
            JSONObject app = all.optJSONObject(source);
            if (all.has(source) && app == null) throw new IllegalStateException();
            if (app == null) app = new JSONObject();
            app.put("hideWatchNext", true);
            all.put(source, app);
            updated = all.toString();
        } catch (Exception invalid) {
            throw new IllegalStateException("The app card settings could not be read. Existing settings were kept.", invalid);
        }
        if (preferences.edit().putString("app_visibility", updated).commit()
                && updated.equals(preferences.getAll().get("app_visibility"))) return;
        SharedPreferences.Editor rollback = preferences.edit();
        if (previous == null) rollback.remove("app_visibility");
        else rollback.putString("app_visibility", (String) previous);
        if (!rollback.commit() || !java.util.Objects.equals(previous, preferences.getAll().get("app_visibility")))
            throw new IllegalStateException("The app card settings could not be recovered. Please try again.");
        throw new IllegalStateException("The app card setting could not be saved. Existing settings were kept.");
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
