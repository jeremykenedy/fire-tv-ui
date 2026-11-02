package com.jeremykenedy.firetv.ui.nativeapp.providers.plex;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reads only an explicitly enabled, previously authorized server connection. */
public final class PlexRepository {
    public static final String PACKAGE = "com.plexapp.android";
    private static final String PREFERENCES = "source_plex";
    public enum Status { DISABLED, NOT_CONNECTED, READY, EMPTY, UNAVAILABLE }
    public static final class Result {
        public final Status status;
        public final List<TvRecommendations.Feed> feeds;
        private final String explanation;
        Result(Status status, List<TvRecommendations.Feed> feeds) { this(status, feeds, null); }
        Result(Status status, List<TvRecommendations.Feed> feeds, String explanation) {
            this.status = status;
            this.explanation = explanation;
            this.feeds = Collections.unmodifiableList(new ArrayList<>(feeds));
        }
        public String message() {
            if (explanation != null) return explanation;
            switch (status) {
                case DISABLED: return "Plex cards are off. Open Plex setup to enable authorized library reads.";
                case NOT_CONNECTED: return "Connect Plex on this device to read your library.";
                case READY: return feeds.size() + " Plex feeds available. Title links open details; choose playback in Plex.";
                case EMPTY: return "The selected Plex feeds have no supported titles.";
                default: return "Plex could not be read. Your saved connection was kept. Check setup and refresh.";
            }
        }
    }
    private PlexRepository() {}

    public static boolean enabled(Context context) {
        return context.getSharedPreferences(PREFERENCES, 0).getBoolean("enabled", false);
    }
    public static void setEnabled(Context context, boolean enabled) {
        if (!context.getSharedPreferences(PREFERENCES, 0).edit().putBoolean("enabled", enabled).commit())
            throw new IllegalStateException("Plex card preferences could not be saved.");
    }
    public static Result read(Context context, CancellationSignal cancellation) {
        List<TvRecommendations.Feed> result = new ArrayList<>();
        if (!enabled(context)) return new Result(Status.DISABLED, result);
        check(cancellation);
        try {
            PlexConnection connection = PlexConnection.load(context);
            if (connection == null) return new Result(Status.NOT_CONNECTED, result);
            // Reading does not rotate identity, authorize an account or change the saved connection.
            readConnection(context, connection, null, result, cancellation);
            for (PlexConnection.Profile profile : connection.profiles) {
                check(cancellation);
                readConnection(context, connection.forProfile(profile), profile.name, result, cancellation);
            }
            return new Result(result.isEmpty() ? Status.EMPTY : Status.READY, result);
        } catch (OperationCanceledException canceled) { throw canceled; }
        catch (PlexApi.TransportUnavailable policy) { return new Result(Status.UNAVAILABLE,
                Collections.<TvRecommendations.Feed>emptyList(), policy.getMessage()); }
        catch (IllegalArgumentException address) { return new Result(Status.UNAVAILABLE,
                Collections.<TvRecommendations.Feed>emptyList(),
                "This saved server address needs supported HTTPS or a numeric private LAN address allowed by Android. The saved connection was kept."); }
        catch (Exception unavailable) { return new Result(Status.UNAVAILABLE, Collections.<TvRecommendations.Feed>emptyList()); }
    }
    private static void check(CancellationSignal cancellation) {
        if (cancellation != null) cancellation.throwIfCanceled();
        if (Thread.currentThread().isInterrupted()) throw new OperationCanceledException();
    }
    private static void readConnection(Context context, final PlexConnection connection, String profile,
            List<TvRecommendations.Feed> result, final CancellationSignal cancellation) throws Exception {
        final PlexApi api = new PlexApi(context, PlexSession.generation(), cancellation);
        check(cancellation);
        String actual = api.server(connection, "/").getJSONObject("MediaContainer").optString("machineIdentifier", "");
        if (!connection.machine.equals(actual)) throw new java.io.IOException("Plex server identity changed.");
        PlexFeedSettings settings = profile == null ? connection.feeds : new PlexFeedSettings(
                new java.util.LinkedHashSet<>(java.util.Arrays.asList("continue")),
                Collections.<String>emptySet(), connection.feeds.cards);
        List<PlexFeedData.Feed> feeds = PlexFeedData.read(new PlexFeedData.Reader() {
            @Override public org.json.JSONObject read(String path, int count) throws Exception {
                check(cancellation);
                return api.server(connection, path, count);
            }
        }, settings);
        for (PlexFeedData.Feed feed : feeds) {
            check(cancellation);
            List<TvRecommendations.Program> programs = new ArrayList<>();
            for (PlexLibrary.Item item : feed.items) {
                check(cancellation);
                String details = PlexItemLinks.details(connection.machine, item);
                // A details handoff is the only supported action. No duplicate play/resume action.
                if (details == null) continue;
                String target = new Intent(Intent.ACTION_VIEW, Uri.parse(details)).setPackage(PACKAGE)
                        .toUri(Intent.URI_INTENT_SCHEME);
                String source = connection.sourceKey();
                String series = item.seriesId.isEmpty() ? null : "plex:" + source + ":series:" + item.seriesId;
                Uri artwork = null;
                try { artwork = PlexArtworkProvider.cache(context, api, connection, item); }
                catch (OperationCanceledException canceled) { throw canceled; }
                catch (Exception unavailable) { /* Cards remain available when artwork is unavailable. */ }
                programs.add(new TvRecommendations.Program("plex:" + source + ":" + item.id,
                        PACKAGE, item.title, artwork, target, series,
                        series == null ? null : item.seriesTitle, target, null));
            }
            if (!programs.isEmpty()) result.add(new TvRecommendations.Feed(
                    "plex:" + connection.sourceKey() + ":" + feed.source,
                    profile == null ? feed.title : "Plex " + profile + " Continue Watching", PACKAGE, programs));
        }
    }
}
