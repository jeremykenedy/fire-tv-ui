package com.jeremykenedy.firetv.ui.nativeapp.providers.plex;

import android.content.Context;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import com.jeremykenedy.firetv.ui.nativeapp.providers.discovery.DiscoveryItem;
import com.jeremykenedy.firetv.ui.nativeapp.providers.discovery.PlexDiscoveryMetadata;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Explicit bounded library discovery; never authorizes an account or writes credentials. */
public final class PlexDiscoverySource {
    public enum Status { DISABLED, NOT_CONNECTED, READY, EMPTY, UNAVAILABLE }
    public static final class Result {
        public final Status status;
        public final List<DiscoveryItem> items;
        public final String message;
        Result(Status status, List<DiscoveryItem> items, String message) {
            this.status = status; this.items = Collections.unmodifiableList(new ArrayList<>(items)); this.message = message;
        }
    }
    private PlexDiscoverySource() {}
    public static Result read(Context context, CancellationSignal cancellation) {
        List<DiscoveryItem> items = new ArrayList<>();
        if (!PlexRepository.enabled(context)) return new Result(Status.DISABLED, items, "Enable your authorized Plex connection in Plex setup first.");
        try {
            check(cancellation);
            PlexConnection connection = PlexConnection.load(context);
            if (connection == null) return new Result(Status.NOT_CONNECTED, items, "Connect Plex on this device before reading a library.");
            PlexApi api = new PlexApi(context, PlexSession.generation(), cancellation);
            if (!connection.machine.equals(api.server(connection, "/").getJSONObject("MediaContainer").optString("machineIdentifier", "")))
                throw new java.io.IOException("Plex server identity changed.");
            for (int type : new int[]{1, 2}) {
                check(cancellation);
                items.addAll(PlexDiscoveryMetadata.parse(connection.sourceKey(), connection.machine,
                        api.server(connection, "/library/all?type=" + type + "&sort=addedAt:desc", 100)));
            }
            return new Result(items.isEmpty() ? Status.EMPTY : Status.READY, items,
                    "Read up to 100 recent movies and 100 recent shows from the connected account's authorized Plex libraries. This is a bounded library sample. Ratings, counts and runtime remain unknown when absent. Reviews and other services are not supplied by this source.");
        } catch (OperationCanceledException canceled) { throw canceled; }
        catch (PlexApi.TransportUnavailable policy) { return new Result(Status.UNAVAILABLE, Collections.<DiscoveryItem>emptyList(), policy.getMessage()); }
        catch (Exception unavailable) { return new Result(Status.UNAVAILABLE, Collections.<DiscoveryItem>emptyList(), "Plex library discovery could not finish. Your connection was kept. Check Plex setup and refresh."); }
    }
    private static void check(CancellationSignal cancellation) {
        if (cancellation != null) cancellation.throwIfCanceled();
        if (Thread.currentThread().isInterrupted()) throw new OperationCanceledException();
    }
}
