package com.jeremykenedy.firetv.ui.nativeapp.providers.plex;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Restores the historical, non-exported Plex TV-card handoff. */
public final class LegacyPlexCardActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private CancellationSignal cancellation;
    private LegacyPlexCardLookup.Reference reference;
    private TextView details;
    private Button open;
    private long generation;
    private boolean busy, blocked, resumed;
    private int requestSerial;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { blocked = true; return; }
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "Your Plex library item", 28));
        details = Pages.text(this, "Reading this item from your Plex server...", 18); page.addView(details);
        open = Pages.button(this, "Open item in Plex", new View.OnClickListener() {
            @Override public void onClick(View view) { read(true); }
        });
        open.setVisibility(View.GONE); page.addView(open);
        Intent intent = getIntent();
        reference = intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null
                ? LegacyPlexCardLookup.parse(intent.getData().toString()) : null;
        if (reference == null) { details.setText("This Plex card is invalid. Refresh your library cards."); return; }
        generation = PlexSession.generation();
    }
    @Override protected void onResume() {
        super.onResume(); resumed = true;
        if (!blocked && reference != null) {
            if (!PlexSession.current(generation)) connectionChanged(); else read(false);
        }
    }
    @Override protected void onPause() {
        resumed = false; requestSerial++; busy = false;
        if (cancellation != null) cancellation.cancel();
        super.onPause();
    }
    private void read(final boolean launch) {
        if (busy || !resumed || isFinishing() || isDestroyed()) return;
        if (!PlexSession.current(generation)) { connectionChanged(); return; }
        busy = true; open.setVisibility(View.GONE);
        final int request = ++requestSerial;
        final CancellationSignal signal = new CancellationSignal(); cancellation = signal;
        worker.execute(new Runnable() {
            @Override public void run() {
                try {
                    PlexConnection saved = PlexConnection.load(LegacyPlexCardActivity.this);
                    final PlexConnection connection = saved == null ? null : saved.forSource(reference.source);
                    if (connection == null) { showError(request, "This card belongs to a previous Plex connection. Refresh your library cards."); return; }
                    final PlexApi api = new PlexApi(LegacyPlexCardActivity.this, generation, signal);
                    final LegacyPlexCardLookup.Result result = LegacyPlexCardLookup.read(reference, new LegacyPlexCardLookup.Reader() {
                        @Override public String sourceKey() throws Exception { return connection.sourceKey(); }
                        @Override public String expectedMachine() { return connection.machine; }
                        @Override public org.json.JSONObject get(String path) throws Exception { return api.server(connection, path); }
                    });
                    if (result == null) { showError(request, "This Plex item or server could not be verified. Refresh your library cards."); return; }
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (!visibleRequest(request)) return;
                            if (!PlexSession.current(generation)) { connectionChanged(); return; }
                            busy = false; details.setText(result.title + "\n\n" + result.summary + result.progress);
                            open.setVisibility(View.VISIBLE);
                            if (launch) open(result.link);
                        }
                    });
                } catch (Exception unavailable) { showError(request, "This Plex item could not be read. Check your server or refresh your Plex connection."); }
            }
        });
    }
    private void open(String link) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage("com.plexapp.android");
        com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations.Program program =
                new com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations.Program(
                        "plex:legacy", "com.plexapp.android", "Plex", null, intent.toUri(Intent.URI_INTENT_SCHEME));
        com.jeremykenedy.firetv.ui.nativeapp.providers.RecommendationActions.Outcome outcome =
                com.jeremykenedy.firetv.ui.nativeapp.providers.RecommendationActions.launchTitle(this, program,
                        new java.util.concurrent.Callable<Boolean>() {
                            @Override public Boolean call() { return active(); }
                        });
        if (outcome == com.jeremykenedy.firetv.ui.nativeapp.providers.RecommendationActions.Outcome.UNAVAILABLE
                || outcome == com.jeremykenedy.firetv.ui.nativeapp.providers.RecommendationActions.Outcome.FAILED)
            details.append("\n\nPlex could not open this item. Your library connection is saved.");
    }
    private void showError(final int request, final String message) {
        runOnUiThread(new Runnable() { @Override public void run() {
            if (!visibleRequest(request)) return;
            if (!PlexSession.current(generation)) { connectionChanged(); return; }
            busy = false; details.setText(message);
        } });
    }
    private void connectionChanged() {
        busy = false; open.setVisibility(View.GONE);
        details.setText("The Plex connection changed. Return to Home and open this card again.");
    }
    private boolean visibleRequest(int request) {
        return !blocked && resumed && !isFinishing() && !isDestroyed() && request == requestSerial;
    }
    private boolean active() { return !blocked && resumed && !isFinishing() && !isDestroyed() && PlexSession.current(generation); }
    @Override protected void onDestroy() { if (cancellation != null) cancellation.cancel(); worker.shutdownNow(); super.onDestroy(); }
}
