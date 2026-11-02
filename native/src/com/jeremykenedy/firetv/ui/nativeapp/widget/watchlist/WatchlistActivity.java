package com.jeremykenedy.firetv.ui.nativeapp.widget.watchlist;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.net.Uri;
import android.content.Intent;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import com.jeremykenedy.firetv.ui.nativeapp.widget.feeds.FeedLinks;
import com.jeremykenedy.firetv.ui.nativeapp.providers.discovery.DiscoveryItem;
import com.jeremykenedy.firetv.ui.nativeapp.providers.plex.PlexDiscoverySource;
import com.jeremykenedy.firetv.ui.nativeapp.providers.plex.PlexWatchlistLookup;
import com.jeremykenedy.firetv.ui.nativeapp.providers.RecommendationActions;
import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;

public final class WatchlistActivity extends com.jeremykenedy.firetv.ui.nativeapp.MenuActivity {
    private LinearLayout body;
    private String focus = "add";
    private boolean blocked;
    private final java.util.concurrent.ExecutorService worker = new java.util.concurrent.ThreadPoolExecutor(1, 1, 0,
            java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<Runnable>(1),
            new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy());
    private final java.util.Map<String, PlexWatchlistLookup.Result> checked = new java.util.HashMap<>();
    private CancellationSignal request;
    private int generation;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { blocked = true; return; }
        if (state != null) focus = state.getString("focus", "add");
        render();
    }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putString("focus", focus); }
    @Override protected void onResume() { super.onResume(); if (!blocked) render(); }
    @Override protected void onPause() { generation++; if (request != null) request.cancel(); request = null; checked.clear(); super.onPause(); }
    @Override protected void onDestroy() { if (request != null) request.cancel(); worker.shutdownNow(); super.onDestroy(); }
    private void render() {
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "Tonight watchlist", 30));
        page.addView(Pages.text(this, "Titles and links stay on this TV and are excluded from portable backups. Links do not verify availability or subscription access. Do not add passwords or tokens.", 16));
        ScrollView scroll = new ScrollView(this); body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        final Watchlist list;
        try { list = new WatchlistStore(this).load(); }
        catch (IllegalStateException error) { body.addView(Pages.text(this, error.getMessage(), 18)); return; }
        add("add", "Add title", new Runnable() { @Override public void run() { edit(null); } });
        add("plex", "Add from your Plex library", new Runnable() { @Override public void run() { choosePlex(); } });
        if (list.items.isEmpty()) body.addView(Pages.text(this, "No saved titles yet.", 18));
        for (final Watchlist.Item item : list.items) add(item.id, (item.watched ? "Watched: " : "To watch: ") + item.title,
                new Runnable() { @Override public void run() { actions(item); } });
        View desired = body.findViewWithTag("watchlist:" + focus);
        if (desired != null) desired.requestFocus(); else body.findViewWithTag("watchlist:add").requestFocus();
    }
    private Button add(final String key, String label, final Runnable action) {
        Button button = Pages.button(this, label, new View.OnClickListener() { @Override public void onClick(View view) { focus = key; action.run(); } });
        button.setTag("watchlist:" + key);
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() { @Override public void onFocusChange(View view, boolean focused) { if (focused) focus = key; } });
        body.addView(button); return button;
    }
    private void actions(final Watchlist.Item item) {
        PlexWatchlistLookup.Result saved = checked.get(item.id);
        final PlexWatchlistLookup.Result result = saved != null && saved.found && !PlexWatchlistLookup.current(saved) ? null : saved;
        String availability = result == null ? "Availability unknown" : result.found ? "Found in your Plex library" : "Plex availability unknown";
        if (result != null) {
            java.util.Date checkedAt = new java.util.Date(result.checkedAtMillis);
            availability += " (checked " + android.text.format.DateFormat.getDateFormat(this).format(checkedAt)
                    + " " + android.text.format.DateFormat.getTimeFormat(this).format(checkedAt) + ")";
        }
        String first = item.plexKey.isEmpty() ? (item.url.isEmpty() ? "No link saved" : "Open saved link") : "Check Plex library";
        String watched = item.watched ? "Mark unwatched" : "Mark watched";
        String[] actions = item.plexKey.isEmpty()
                ? new String[]{first, watched, "Edit title or link", "Remove"}
                : item.url.isEmpty()
                ? new String[]{first, watched, "Edit title or link", "Remove", result != null && result.found ? "Open Plex details" : "Plex details not verified"}
                : new String[]{first, watched, "Edit title or link", "Remove", result != null && result.found ? "Open Plex details" : "Plex details not verified", "Open saved link"};
        AlertDialog dialog = new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(this).setTitle(item.title + " - " + availability)
                .setItems(actions, new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog, int which) {
                    if (which == 0 && !item.plexKey.isEmpty()) checkPlex(item);
                    else if (which == 0 && !item.url.isEmpty()) FeedLinks.confirmSavedLink(WatchlistActivity.this, item.title, item.url);
                    else if (which == 1) change(item, false);
                    else if (which == 2) edit(item);
                    else if (which == 4 && result != null && result.found) openPlex(item, result);
                    else if (which == 5 && !item.url.isEmpty()) FeedLinks.confirmSavedLink(WatchlistActivity.this, item.title, item.url);
                    else if (which == 3) new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(WatchlistActivity.this).setTitle("Remove title?")
                            .setNegativeButton("Cancel", null).setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface d, int w) { change(item, true); }
                            }).show();
                }}).setNegativeButton("Cancel", null).show();
        dialog.getListView().requestFocus(); dialog.getListView().setSelection(0);
    }
    private void choosePlex() {
        final int serial = ++generation; if (request != null) request.cancel(); final CancellationSignal signal = request = new CancellationSignal();
        toast("Reading your authorized Plex library sample...");
        worker.execute(new Runnable() { @Override public void run() {
            final PlexDiscoverySource.Result result;
            try { result = PlexDiscoverySource.read(getApplicationContext(), signal); }
            catch (android.os.OperationCanceledException cancelled) { return; }
            catch (RuntimeException unavailable) {
                runOnUiThread(new Runnable() { @Override public void run() {
                    if (serial == generation && !signal.isCanceled() && !isFinishing() && !isDestroyed()) toast("Plex library could not be read. Saved titles were kept.");
                }}); return;
            }
            runOnUiThread(new Runnable() { @Override public void run() {
                if (serial != generation || signal.isCanceled() || isFinishing() || isDestroyed()) return;
                if (result.items.isEmpty()) { toast(result.message); return; }
                String[] choices = new String[result.items.size()];
                for (int i = 0; i < choices.length; i++) choices[i] = result.items.get(i).title;
                new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(WatchlistActivity.this).setTitle("Choose a title from the Plex sample")
                        .setItems(choices, new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog, int which) {
                            DiscoveryItem item = result.items.get(which);
                            try {
                                WatchlistStore store = new WatchlistStore(WatchlistActivity.this); String expected = store.raw();
                                Watchlist next = store.load().addPlex(item.title, item.key); store.save(expected, next);
                                focus = next.items.get(next.items.size() - 1).id; render();
                            } catch (IllegalArgumentException | IllegalStateException failure) { toast(failure.getMessage()); }
                        }}).setNegativeButton("Cancel", null).show();
            }});
        }});
    }
    private void checkPlex(final Watchlist.Item item) {
        final int serial = ++generation; if (request != null) request.cancel(); final CancellationSignal signal = request = new CancellationSignal();
        toast("Checking your Plex library...");
        worker.execute(new Runnable() { @Override public void run() {
            final PlexWatchlistLookup.Result result;
            try { result = PlexWatchlistLookup.read(getApplicationContext(), item.plexKey, signal); }
            catch (android.os.OperationCanceledException cancelled) { return; }
            runOnUiThread(new Runnable() { @Override public void run() {
                if (serial != generation || signal.isCanceled() || isFinishing() || isDestroyed()) return;
                try {
                    boolean present = false;
                    for (Watchlist.Item current : new WatchlistStore(WatchlistActivity.this).load().items)
                        if (current.id.equals(item.id) && current.plexKey.equals(item.plexKey)) present = true;
                    if (!present) { toast("Title changed. Reopen and try again."); render(); return; }
                } catch (IllegalStateException unavailable) { toast(unavailable.getMessage()); return; }
                checked.put(item.id, result); actions(item);
            }});
        }});
    }
    private void openPlex(Watchlist.Item item, final PlexWatchlistLookup.Result result) {
        if (!PlexWatchlistLookup.current(result)) { toast("Plex connection changed. Check this title again."); return; }
        String link = new Intent(Intent.ACTION_VIEW, Uri.parse(result.detailsUri)).setPackage("com.plexapp.android").toUri(Intent.URI_INTENT_SCHEME);
        TvRecommendations.Program program = new TvRecommendations.Program(item.plexKey, "com.plexapp.android", item.title, null, link, null, null, link, null);
        RecommendationActions.Outcome outcome = RecommendationActions.launchTitle(this, program, new java.util.concurrent.Callable<Boolean>() {
            @Override public Boolean call() { return !isFinishing() && !isDestroyed() && PlexWatchlistLookup.current(result); }
        });
        if (outcome != RecommendationActions.Outcome.OPENED && outcome != RecommendationActions.Outcome.PENDING)
            toast("Plex details could not be opened. The saved title was kept.");
    }
    private void change(Watchlist.Item item, boolean remove) {
        try {
            WatchlistStore store = new WatchlistStore(this); String expected = store.raw(); Watchlist old = store.load();
            boolean same = false;
            for (Watchlist.Item current : old.items) if (current.id.equals(item.id))
                same = current.title.equals(item.title) && current.url.equals(item.url) && current.watched == item.watched;
            if (!same) throw new IllegalStateException("Title changed. Reopen and try again.");
            Watchlist next = remove ? old.remove(item.id) : old.watched(item.id, !item.watched);
            store.save(expected, next); if (remove) focus = "add"; render();
        } catch (IllegalStateException | IllegalArgumentException failure) { toast(failure.getMessage()); }
    }
    private void edit(final Watchlist.Item item) {
        final WatchlistStore editStore = new WatchlistStore(this);
        final String expected;
        final Watchlist original;
        try { expected = editStore.raw(); original = editStore.load(); }
        catch (IllegalStateException error) { toast(error.getMessage()); return; }
        if (item != null) {
            boolean same = false;
            for (Watchlist.Item current : original.items) if (current.id.equals(item.id))
                same = current.title.equals(item.title) && current.url.equals(item.url) && current.watched == item.watched
                        && current.plexKey.equals(item.plexKey);
            if (!same) { toast("Title changed. Reopen and try again."); return; }
        }
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL);
        final EditText title = new EditText(this); title.setHint("Title"); title.setSingleLine(); title.setFilters(new InputFilter[]{new InputFilter.LengthFilter(512)});
        final EditText link = new EditText(this); link.setHint("HTTPS link (optional)"); link.setSingleLine(); link.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        link.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2048)}); link.setSaveEnabled(false); link.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        if (item != null) { title.setText(item.title); link.setText(item.url); }
        fields.addView(title); fields.addView(link);
        final AlertDialog dialog = new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(this).setTitle(item == null ? "Add title" : "Edit title")
                .setMessage("Optional HTTPS links stay on this TV. Availability is unknown; do not enter credentials or tokens.")
                .setView(fields).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() { @Override public void onShow(DialogInterface ignored) {
            title.requestFocus(); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    try {
                        Watchlist next = item == null ? original.add(title.getText().toString(), link.getText().toString().trim())
                                : original.edit(item.id, title.getText().toString(), link.getText().toString().trim());
                        editStore.save(expected, next); focus = item == null ? next.items.get(next.items.size() - 1).id : item.id;
                        dialog.dismiss(); render();
                    } catch (IllegalArgumentException failure) { title.setError(failure.getMessage()); }
                    catch (IllegalStateException failure) { toast(failure.getMessage()); }
                }
            });
        }}); dialog.show();
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
