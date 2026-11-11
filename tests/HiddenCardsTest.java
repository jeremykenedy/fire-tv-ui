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
        sharedRoutesAndRowFallback(context);
        channelManagement(context);
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

    private static void sharedRoutesAndRowFallback(Context context) {
        HiddenCards.restoreAll(context);
        Cursor first = new Cursor("source.shared", "First shared title", "shared-a", "same-route", 9, 101);
        Cursor second = new Cursor("source.shared", "Second shared title", "shared-b", "same-route", 9, 102);
        HiddenCards.visible(context, first, false);
        HiddenCards.visible(context, second, false);
        String firstKey = HiddenCards.key(context, first, false);
        String secondKey = HiddenCards.key(context, second, false);
        check(HiddenCards.card("source.shared", Uri.parse("same-route"), false) == null,
                "A shared route must never select an arbitrary title.");
        check(HiddenCards.card(firstKey).title.equals("First shared title")
                && HiddenCards.card(secondKey).title.equals("Second shared title"),
                "Each displayed identity must retain its own menu target despite a shared route.");
        HiddenCards.hide(context, firstKey);
        check(!HiddenCards.visible(context, first, false) && HiddenCards.visible(context, second, false),
                "Hiding the selected shared-route title must retain its neighbor.");
        Cursor rowOnly = new Cursor("source.rows", "No provider ID", "", "shared-row-route", 7, 51);
        Cursor otherRow = new Cursor("source.rows", "Other row", "", "shared-row-route", 7, 52);
        Cursor watchNext = new Cursor("source.rows", "Other table", "", "shared-row-route", 7, 51);
        HiddenCards.visible(context, rowOnly, false);
        String rowKey = HiddenCards.key(context, rowOnly, false);
        check(rowKey != null && HiddenCards.card(rowKey) != null,
                "A published provider row remains individually dismissible without an optional provider ID.");
        HiddenCards.hide(context, rowKey);
        check(!HiddenCards.visible(context, rowOnly, false)
                && HiddenCards.visible(context, otherRow, false)
                && HiddenCards.visible(context, watchNext, true),
                "Row fallback must retain other rows and the separate Watch Next table.");
        check(!rowKey.equals(FeaturedCardRules.key("source.rows", false, "51", "")),
                "A row ID must not collide with an optional provider ID.");
        for (int i = 0; i < 2050; i++)
            HiddenCards.visible(context, new Cursor("source.eviction", "Other", "eviction-" + i, "route-" + i), false);
        check(HiddenCards.card(firstKey) == null, "Fixture must evict the original label cache entry.");
        HiddenCards.Card selected = HiddenCards.selected(firstKey, "source.shared", "First shared title", 9);
        check(selected != null && selected.key.equals(firstKey) && selected.title.equals("First shared title"),
                "The selected model must remain dismissible after its label cache entry is evicted.");
        check(HiddenCards.selected(firstKey, "source.shared", "||First shared title", 9).title.equals("||First shared title"),
                "The original preview title must be retained literally, without interpreting renderer prefixes.");
        check(HiddenCards.selected(firstKey, "source.shared", "||||Literal preview title", 9).title.equals("||||Literal preview title"),
                "Repeated literal title prefixes must remain unchanged.");
        check(HiddenCards.selected(firstKey, "source.shared", "||Literal Watch Next title", -1).title.equals("||Literal Watch Next title"),
                "Watch Next title text must remain literal.");
        HiddenCards.hide(context, selected.key);
        check(!HiddenCards.visible(context, first, false), "Dismissal must use the exact carried key after cache eviction.");
        check(HiddenCards.selected("not-an-item-key", "source.shared", "Other", 9) == null,
                "An unestablished identity must remain rejected.");
        HiddenCards.restoreAll(context);
    }

    private static void channelManagement(Context context) {
        HiddenCards.restoreAll(context);
        String uri = "content://android.media.tv/channel/7/preview_program";
        android.content.ContentResolver.rows.put(
                uri,
                new Cursor(
                        new Cursor("source.one", "Betty", "betty", "betty-route", 7),
                        new Cursor("source.one", "Next", "next", "next-route", 7),
                        new Cursor("source.two", "Wrong source", "foreign", "foreign-route", 7),
                        new Cursor(
                                "source.one", "Wrong channel", "unrelated", "unrelated-route", 8),
                        new Cursor("source.one", "Missing ID", "", "missing-route", 7)));
        java.util.List<HiddenCards.Card> listed =
                ChannelCards.read(context, "source.one", new long[] {7});
        check(
                listed.size() == 3 && listed.get(2).key == null,
                "Manage cards lists only its exact channel/source, retaining nonhideable items"
                    + " honestly.");
        String betty = listed.get(0).key;
        String next = listed.get(1).key;
        String other = FeaturedCardRules.key("source.two", false, "elsewhere", "");
        HiddenCards.hide(context, betty);
        HiddenCards.hide(context, next);
        HiddenCards.hide(context, other);
        android.content.ContentResolver.rows.put(
                uri,
                new Cursor(
                        new Cursor("source.one", "Betty", "betty", "betty-route", 7),
                        new Cursor("source.one", "Next", "next", "next-route", 7)));
        listed = ChannelCards.read(context, "source.one", new long[] {7});
        check(
                listed.size() == 2 && HiddenCards.keys(context).contains(listed.get(0).key),
                "Hidden items must remain in Channel Manager so they can be restored.");
        HiddenCards.restore(
                context, new java.util.HashSet<String>(java.util.Arrays.asList(betty, next)));
        check(
                HiddenCards.keys(context).size() == 1 && HiddenCards.keys(context).contains(other),
                "Restore all for one channel must preserve hidden choices in other channels.");
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
