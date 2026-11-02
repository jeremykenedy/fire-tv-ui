package com.jeremykenedy.firetv.ui.nativeapp.widget.watchlist;

import android.content.Context;
import org.json.JSONObject;

public final class WatchlistTest {
    public static void main(String[] args) throws Exception {
        Context context = new Context(); WatchlistStore store = new WatchlistStore(context);
        Watchlist list = store.load().add("Arrival", "https://example.com/title/1").add("Second", "");
        String first = list.items.get(0).id, second = list.items.get(1).id;
        store.save(null, list);
        String saved = store.raw();
        Watchlist changed = store.load().watched(first, true).edit(second, "Second revised", "https://example.org/");
        store.save(saved, changed);
        check(store.load().items.get(0).watched && store.load().items.get(1).title.equals("Second revised"), "Edit and watched choices persist independently.");
        reject(() -> store.save(saved, list));
        String beforeFailure = store.raw(); context.prefs.failNext = true;
        reject(() -> store.save(beforeFailure, store.load().remove(first)));
        check(beforeFailure.equals(store.raw()), "Failed write restores exact previous document.");
        JSONObject future = new JSONObject(beforeFailure); future.put("future_root", "kept"); future.getJSONArray("items").getJSONObject(0).put("future_item", 7);
        Watchlist preserved = Watchlist.decode(future.toString()).watched(first, false);
        JSONObject roundtrip = new JSONObject(preserved.encode());
        check("kept".equals(roundtrip.getString("future_root")) && roundtrip.getJSONArray("items").getJSONObject(0).getInt("future_item") == 7, "Unknown fields survive an edit.");
        check(preserved.remove(first).items.size() == 1 && preserved.items.size() == 2, "Remove only affects the selected title.");
        String source = new String(new char[64]).replace('\0', 'a');
        Watchlist plex = list.addPlex("Plex title", "plex:" + source + ":12");
        check(plex.items.get(2).plexKey.equals("plex:" + source + ":12") && plex.items.get(2).url.isEmpty(), "Plex saves only a source-scoped identity.");
        check(!plex.encode().contains("token"), "Plex watchlist data does not contain credentials.");
        reject(() -> list.addPlex("Plex title", "plex:" + source + ":12?token=secret"));
        reject(() -> list.add("", "")); reject(() -> list.add("Unsafe", "http://example.com/"));
        check(list.add(new String(new char[512]).replace('\0', 'x'), "").items.size() == 3, "Full-length source titles are retained.");
        reject(() -> list.add(new String(new char[513]).replace('\0', 'x'), ""));
        reject(() -> list.add("Unsafe", "https://user:password@example.com/"));
        reject(() -> Watchlist.decode("{\"version\":1,\"version\":1,\"items\":[]}"));
        reject(() -> Watchlist.decode("{\"version\":2,\"items\":[]}"));
        System.out.println("Watchlist behavior, privacy boundary, unknown fields and failed-write recovery passed.");
    }
    private static void reject(Runnable action) { try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; } throw new AssertionError("Invalid operation accepted."); }
    private static void check(boolean value, String text) { if (!value) throw new AssertionError(text); }
}
