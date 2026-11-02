package com.jeremykenedy.firetv.ui.nativeapp.providers.plex;

import android.content.Context;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;

/** Verifies one saved, source-scoped Plex identity without exporting connection credentials. */
public final class PlexWatchlistLookup {
    public static final class Result {
        public final boolean found;
        public final String detailsUri;
        public final long checkedAtMillis;
        private final long generation;
        private Result(boolean found, String detailsUri) {
            this.found = found; this.detailsUri = detailsUri; this.checkedAtMillis = System.currentTimeMillis();
            this.generation = PlexSession.generation();
        }
    }
    private PlexWatchlistLookup() {}
    public static boolean current(Result result) { return result != null && result.found && PlexSession.current(result.generation); }
    private static LegacyPlexCardLookup.Reference reference(String key) {
        if (key == null || !key.matches("plex:[a-f0-9]{64}:[0-9]{1,18}")) return null;
        return LegacyPlexCardLookup.parse("firetvui://plex/item/" + key.substring(5, 69) + "/" + key.substring(70));
    }
    static Result evaluate(String key, LegacyPlexCardLookup.Reader reader) {
        try {
            LegacyPlexCardLookup.Result result = LegacyPlexCardLookup.read(reference(key), reader);
            return result == null ? new Result(false, null) : new Result(true, result.link);
        } catch (Exception error) { return new Result(false, null); }
    }
    public static Result read(Context context, String key, final CancellationSignal signal) {
        if (signal != null) signal.throwIfCanceled();
        final LegacyPlexCardLookup.Reference ref = reference(key);
        final long generation = PlexSession.generation();
        if (ref == null) return new Result(false, null);
        try {
            if (!PlexRepository.enabled(context)) return new Result(false, null);
            PlexConnection saved = PlexConnection.load(context);
            final PlexConnection connection = saved == null ? null : saved.forSource(ref.source);
            if (connection == null || !PlexSession.current(generation)) return new Result(false, null);
            final PlexApi api = new PlexApi(context, generation, signal);
            Result result = evaluate(key, new LegacyPlexCardLookup.Reader() {
                @Override public String sourceKey() throws Exception { return connection.sourceKey(); }
                @Override public String expectedMachine() { return connection.machine; }
                @Override public org.json.JSONObject get(String path) throws Exception { return api.server(connection, path); }
            });
            if (signal != null) signal.throwIfCanceled();
            return PlexSession.current(generation) ? result : new Result(false, null);
        } catch (OperationCanceledException cancelled) { throw cancelled; }
        catch (Exception unavailable) { return new Result(false, null); }
    }
}
