package com.jeremykenedy.firetv.ui.nativeapp.providers.discovery;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import com.jeremykenedy.firetv.ui.nativeapp.cards.CardOptions;
import com.jeremykenedy.firetv.ui.nativeapp.cards.CardPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.providers.RecommendationActions;
import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;
import com.jeremykenedy.firetv.ui.nativeapp.providers.plex.PlexDiscoverySource;
import com.jeremykenedy.firetv.ui.nativeapp.providers.plex.PlexSettingsActivity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/** Explicit library discovery with local filters and source-labeled metadata. */
public final class DiscoveryActivity extends com.jeremykenedy.firetv.ui.nativeapp.MenuActivity {
    private boolean startupBlocked;
    private final ExecutorService worker = new java.util.concurrent.ThreadPoolExecutor(1, 1, 0,
            java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<Runnable>(1),
            new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy());
    private DiscoveryOptions options;
    private List<DiscoveryItem> catalog = Collections.emptyList();
    private LinearLayout body;
    private CancellationSignal cancellation;
    private int generation;
    private String status = "Refresh to read a bounded sample from your authorized Plex library.";

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        options = DiscoveryPreferences.load(this);
        LinearLayout page = Pages.page(this);
        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body); page.addView(scroll);
        render();
    }
    private void render() {
        View focused = getCurrentFocus();
        Object focusTag = focused == null ? null : focused.getTag();
        body.removeAllViews();
        body.addView(Pages.text(this, "What to Watch", 28));
        body.addView(Pages.text(this, "Suggestions from the connected account's authorized Plex libraries. Filters use only available metadata. A rating count is the count supplied by Plex, not a verified external service vote total. Playback and subscriptions stay with the source app. Review text is not imported. Open a title's Plex details to check the reviews Plex makes available.", 16));
        android.widget.TextView state = Pages.text(this, status, 17); state.setTag("discovery:status"); body.addView(state);
        button("Refresh authorized Plex library", "discovery:refresh", new Runnable() {
            @Override public void run() { refresh(); }
        });
        button("Plex account and source setup", "discovery:setup", new Runnable() {
            @Override public void run() { startActivity(new Intent(DiscoveryActivity.this, PlexSettingsActivity.class)); }
        });
        button("Titles: " + (options.movies && options.shows ? "Movies and shows" : options.movies ? "Movies" : "Shows"), "discovery:types", new Runnable() {
            @Override public void run() { choices("Title types", new String[]{"Movies and shows", "Movies", "Shows"}, new Choice() {
                @Override public void selected(int index) { save(options.withLimits(index != 2, index != 1, options.maximumMinutes, options.minimumScore, options.minimumRatingCount)); }
            }); }
        });
        button("Include any selected genre (" + options.includedGenres.size() + ")", "discovery:include", new Runnable() {
            @Override public void run() { genres(false); }
        });
        button("Exclude selected genres (" + options.excludedGenres.size() + ")", "discovery:exclude", new Runnable() {
            @Override public void run() { genres(true); }
        });
        button("Plex libraries: " + (options.libraries.isEmpty() ? "All available" : options.libraries.size() + " selected"), "discovery:libraries", new Runnable() {
            @Override public void run() { libraries(); }
        });
        button("Maximum runtime: " + (options.maximumMinutes == 0 ? "Any / unknown allowed" : options.maximumMinutes + " minutes"), "discovery:runtime", new Runnable() {
            @Override public void run() { choices("Maximum runtime", new String[]{"Any, including unknown", "30 minutes", "60 minutes", "90 minutes", "120 minutes", "180 minutes"}, new Choice() {
                @Override public void selected(int index) { save(options.withLimits(options.movies, options.shows, new int[]{0,30,60,90,120,180}[index], options.minimumScore, options.minimumRatingCount)); }
            }); }
        });
        button("Minimum rating: " + (options.minimumScore == 0 ? "Any / unknown allowed" : options.minimumScore + "/10"), "discovery:rating", new Runnable() {
            @Override public void run() { choices("Minimum source rating", new String[]{"Any, including unknown", "5/10", "6/10", "7/10", "8/10", "9/10"}, new Choice() {
                @Override public void selected(int index) { save(options.withLimits(options.movies, options.shows, options.maximumMinutes, new double[]{0,5,6,7,8,9}[index], options.minimumRatingCount)); }
            }); }
        });
        button("Minimum Plex rating count: " + (options.minimumRatingCount == 0 ? "Any / unknown allowed" : options.minimumRatingCount), "discovery:votes", new Runnable() {
            @Override public void run() { choices("Minimum Plex supplied rating count", new String[]{"Any, including unknown", "100", "1000", "10000"}, new Choice() {
                @Override public void selected(int index) { save(options.withLimits(options.movies, options.shows, options.maximumMinutes, options.minimumScore, new long[]{0,100,1000,10000}[index])); }
            }); }
        });
        button("Restore Not interested titles (" + options.notInterested.size() + ")", "discovery:restore", new Runnable() {
            @Override public void run() { save(options.restoreDismissed()); }
        });
        button("Clear liked titles (" + options.likedTitles.size() + ")", "discovery:clear-likes", new Runnable() {
            @Override public void run() { save(options.clearLikes()); }
        });
        body.addView(Pages.text(this, "Liked titles appear first, followed by titles sharing their genres in this sample. Hidden titles and your filters still apply. Likes stay on this device.", 16));
        final CardOptions hidden;
        try { hidden = CardPreferences.load(this); }
        catch (RuntimeException unreadable) {
            body.addView(Pages.text(this, "Hidden title settings could not be read. Resolve card settings before showing suggestions.", 17));
            return;
        }
        List<DiscoveryFilter.Suggestion> suggestions = DiscoveryFilter.select(catalog, options, new DiscoveryFilter.HiddenTitles() {
            @Override public boolean hidden(DiscoveryItem item) { return hidden.isHidden(item.source, item.key, PlexDiscoveryMetadata.seriesKey(item)); }
        });
        body.addView(Pages.text(this, suggestions.size() + " matching titles in the loaded sample", 22));
        for (DiscoveryFilter.Suggestion suggestion : suggestions) {
            final DiscoveryItem item = suggestion.item;
            String runtime = item.durationMillis == null ? "Runtime unknown" : (item.durationMillis / 60000) + " min";
            body.addView(Pages.text(this, item.title + "\n" + item.ratingText() + "; " + item.countText() + "; " + runtime
                    + "\n" + suggestion.explanation, 18));
            if (item.detailsUri != null) button("Details in Plex: " + item.title, "discovery:open:" + item.key, new Runnable() {
                @Override public void run() {
                    String link = new Intent(Intent.ACTION_VIEW, Uri.parse(item.detailsUri)).setPackage(item.source).toUri(Intent.URI_INTENT_SCHEME);
                    TvRecommendations.Program program = new TvRecommendations.Program(item.key, item.source, item.title, null, link, null, null, link, null);
                    RecommendationActions.Outcome outcome = RecommendationActions.launchDetails(DiscoveryActivity.this, program);
                    if (outcome != RecommendationActions.Outcome.OPENED && outcome != RecommendationActions.Outcome.PENDING)
                        Toast.makeText(DiscoveryActivity.this, "Plex cannot open these details on this device.", Toast.LENGTH_SHORT).show();
                }
            });
            button((options.likedTitles.contains(item.key) ? "Remove like: " : "Like: ") + item.title,
                    "discovery:like:" + item.key, new Runnable() {
                @Override public void run() { save(options.toggleLike(item.key)); }
            });
            button("Not interested: " + item.title, "discovery:dismiss:" + item.key, new Runnable() {
                @Override public void run() { save(options.dismiss(item.key)); }
            });
        }
        if (!catalog.isEmpty() && suggestions.isEmpty()) body.addView(Pages.text(this,
                "No loaded titles meet these filters. Active rating, count and runtime limits exclude unknown values. Change filters or refresh your authorized library sample.", 17));
        View restore = focusTag == null ? null : body.findViewWithTag(focusTag);
        if (restore != null) restore.requestFocus();
        else if (focusTag != null) body.findViewWithTag("discovery:refresh").requestFocus();
    }
    private void refresh() {
        cancel();
        final int reading = ++generation;
        final CancellationSignal signal = cancellation = new CancellationSignal();
        status = "Reading authorized Plex movie and show metadata..."; render();
        worker.execute(new Runnable() {
            @Override public void run() {
                final PlexDiscoverySource.Result result;
                try { result = PlexDiscoverySource.read(DiscoveryActivity.this, signal); }
                catch (OperationCanceledException stopped) { return; }
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (isDestroyed() || isFinishing() || generation != reading || signal.isCanceled()) return;
                        catalog = result.items; status = result.message; render();
                    }
                });
            }
        });
    }
    private void save(DiscoveryOptions selected) {
        try { DiscoveryPreferences.save(this, selected); options = selected; render(); }
        catch (RuntimeException failed) { Toast.makeText(this, "Discovery choices could not be saved.", Toast.LENGTH_SHORT).show(); }
    }
    private void genres(final boolean excluded) {
        Set<String> values = new java.util.TreeSet<>();
        for (DiscoveryItem item : catalog) for (String genre : item.genres) values.add(genre.toLowerCase(java.util.Locale.ROOT));
        values.addAll(options.includedGenres); values.addAll(options.excludedGenres);
        if (values.isEmpty()) { Toast.makeText(this, "Refresh a library sample with genre metadata first.", Toast.LENGTH_SHORT).show(); return; }
        final List<String> names = new ArrayList<>(values);
        selectSet(excluded ? "Excluded genres" : "Any included genre; none means all", names, names,
                excluded ? options.excludedGenres : options.includedGenres, new Selection() {
                    @Override public void selected(Set<String> selected) { save(excluded ? options.withGenres(options.includedGenres, selected) : options.withGenres(selected, options.excludedGenres)); }
                });
    }
    private void libraries() {
        Map<String, String> values = new LinkedHashMap<>();
        for (DiscoveryItem item : catalog) values.put(item.libraryKey, item.libraryTitle);
        for (String saved : options.libraries) if (!values.containsKey(saved)) values.put(saved, "Previously selected library");
        if (values.isEmpty()) { Toast.makeText(this, "Refresh an authorized library sample first.", Toast.LENGTH_SHORT).show(); return; }
        selectSet("Plex libraries; none means all", new ArrayList<>(values.keySet()), new ArrayList<>(values.values()), options.libraries, new Selection() {
            @Override public void selected(Set<String> selected) { save(options.withLibraries(selected)); }
        });
    }
    private interface Choice { void selected(int index); }
    private void choices(String title, String[] labels, final Choice choice) {
        new AlertDialog.Builder(this).setTitle(title).setItems(labels, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int index) { choice.selected(index); }
        }).setNegativeButton("Cancel", null).show();
    }
    private interface Selection { void selected(Set<String> values); }
    private void selectSet(String title, final List<String> keys, List<String> labels, Set<String> current, final Selection selection) {
        final boolean[] selected = new boolean[keys.size()];
        for (int i = 0; i < selected.length; i++) selected[i] = current.contains(keys.get(i));
        new AlertDialog.Builder(this).setTitle(title).setMultiChoiceItems(labels.toArray(new String[0]), selected,
                new DialogInterface.OnMultiChoiceClickListener() {
                    @Override public void onClick(DialogInterface dialog, int index, boolean checked) { selected[index] = checked; }
                }).setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        Set<String> values = new LinkedHashSet<>();
                        for (int i = 0; i < selected.length; i++) if (selected[i]) values.add(keys.get(i));
                        selection.selected(values);
                    }
                }).setNegativeButton("Cancel", null).show();
    }
    private void button(String label, String tag, final Runnable action) {
        android.widget.Button button = Pages.button(this, label, new View.OnClickListener() {
            @Override public void onClick(View view) { action.run(); }
        });
        button.setTag(tag); body.addView(button);
    }
    private void cancel() { generation++; if (cancellation != null) cancellation.cancel(); }
    @Override protected void onResume() {
        if (startupBlocked) { super.onResume(); return; } super.onResume(); render(); }
    @Override protected void onPause() {
        if (startupBlocked) { super.onPause(); return; }
        cancel(); catalog = Collections.emptyList();
        status = "Refresh to read a bounded sample from your authorized Plex library.";
        super.onPause();
    }
    @Override protected void onDestroy() {
        if (startupBlocked) { super.onDestroy(); return; } cancel(); worker.shutdownNow(); super.onDestroy(); }
}
