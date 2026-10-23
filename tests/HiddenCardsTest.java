package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

public final class HiddenCardsTest {
    public static void main(String[] args) {
        Context context = new Context();
        Cursor first = new Cursor("source.one", "First", "item-a", "route-a");
        Cursor next = new Cursor("source.one", "Next", "item-b", "route-b");
        Cursor other = new Cursor("source.two", "Other", "item-a", "route-a");
        check(HiddenCards.visible(context, first, false), "New items must remain visible.");
        check(
                HiddenCards.visible(context, next, false),
                "Other cards in a source must remain visible.");
        String a = HiddenCards.card("source.one", Uri.parse("route-a"), false).key;
        String b = HiddenCards.card("source.one", Uri.parse("route-b"), false).key;
        HiddenCards.hide(context, a);
        check(
                !HiddenCards.visible(context, first, false)
                        && HiddenCards.visible(context, next, false)
                        && HiddenCards.visible(context, other, false),
                "Hiding a single item must leave the next program and other sources eligible.");
        Cursor updated = new Cursor("source.one", "Renamed", "item-a", "new-route-a");
        check(
                !HiddenCards.visible(context, updated, false),
                "Changing a title or playback intent must not resurface a hidden stable item.");
        check(
                HiddenCards.visible(context, first, true),
                "A separate Watch Next publication must not be hidden by a preview-card choice.");
        HiddenCards.restore(context, a);
        check(HiddenCards.visible(context, first, false), "One hidden item can be restored.");
        HiddenCards.hide(context, a);
        HiddenCards.hide(context, b);
        check(
                !HiddenCards.visible(context, first, false)
                        && !HiddenCards.visible(context, next, false),
                "Hiding the final visible item must produce an empty eligible list safely.");
        HiddenCards.restore(context, a);
        check(
                HiddenCards.visible(context, first, false)
                        && !HiddenCards.visible(context, next, false),
                "Restoring one must preserve other hidden choices.");
        HiddenCards.restoreAll(context);
        check(
                HiddenCards.keys(context).isEmpty() && HiddenCards.visible(context, next, false),
                "Restore all must remove every item-level suppression.");
        HiddenCards.hide(context, a);
        HiddenCards.restore(context, a);
        check(
                HiddenCards.keys(context).isEmpty(),
                "A removed provider item can still be restored by hash.");
        check(
                HiddenCards.visible(
                        context, new Cursor("source.one", "No ID", "", "route-no-id"), false),
                "Missing stable identity must not hide unrelated content.");
        HiddenCards.hide(context, a);
        for (String persisted : HiddenCards.keys(context)) {
            check(
                    FeaturedCardRules.validKey(persisted) && !persisted.contains("route"),
                    "Settings must persist only opaque provider identities, never intents or"
                            + " credentials.");
        }
        HiddenCards.restoreAll(context);
        String serverA = new String(new char[64]).replace('\0', 'a');
        String serverB = new String(new char[64]).replace('\0', 'b');
        String routeA = plexRoute(serverA, "42");
        Cursor oldPlex = new Cursor("ui.test", "Continue", "42", routeA);
        HiddenCards.visible(context, oldPlex, false);
        String plexKey = HiddenCards.card("ui.test", Uri.parse(routeA), false).key;
        HiddenCards.hide(context, plexKey);
        check(
                !HiddenCards.visible(
                        context, new Cursor("ui.test", "Recent", serverA + ":42", routeA), false),
                "The same Plex item remains hidden across both feeds and a provider ID upgrade.");
        check(
                HiddenCards.visible(
                        context,
                        new Cursor("ui.test", "Different server", "42", plexRoute(serverB, "42")),
                        false),
                "A repeated rating key on a different server must remain visible.");
        check(
                HiddenCards.visible(
                        context, new Cursor("source.two", "Other publisher", "42", routeA), false),
                "Other publishers must not inherit Plex identity normalization.");
        context.prefs.fail = true;
        try {
            HiddenCards.hide(context, b);
            throw new AssertionError("A storage failure must be reported.");
        } catch (IllegalStateException expected) {
            check(
                    !HiddenCards.keys(context).contains(b),
                    "A failed save must not mutate the active hidden set.");
        }
    }

    private static String plexRoute(String server, String item) {
        return "firetvui://plex/item/"
                + server
                + "/"
                + item
                + "#Intent;component=ui.test/.PlexItemActivity;end";
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
