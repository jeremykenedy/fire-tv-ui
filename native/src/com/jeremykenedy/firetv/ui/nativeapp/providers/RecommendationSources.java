package com.jeremykenedy.firetv.ui.nativeapp.providers;

import android.content.Context;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import com.jeremykenedy.firetv.ui.nativeapp.providers.plex.PlexRepository;
import java.util.ArrayList;
import java.util.List;
import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations.SourceStatus;

/** Actual provider snapshots only. Optional integrations are disabled until explicitly configured. */
public final class RecommendationSources {
    private RecommendationSources() {}
    public static TvRecommendations.Result read(Context context) { return read(context, null); }

    public static TvRecommendations.Result read(Context context, CancellationSignal cancellation) {
        TvRecommendations.Result tv = TvRecommendations.read(context, cancellation);
        List<TvRecommendations.Feed> feeds = new ArrayList<>(tv.feeds);
        List<SourceStatus> statuses = new ArrayList<>();
        statuses.add(new SourceStatus("tv", "Shared TV recommendations", tvState(tv.status), tv.message()));
        if (cancellation != null) cancellation.throwIfCanceled();
        try {
            if (PlexRepository.enabled(context)) {
                PlexRepository.Result plex = PlexRepository.read(context, cancellation);
                feeds.addAll(plex.feeds);
                SourceStatus.State state;
                switch (plex.status) {
                    case READY: state = SourceStatus.State.READY; break;
                    case PARTIAL: state = SourceStatus.State.PARTIAL; break;
                    case EMPTY: state = SourceStatus.State.EMPTY; break;
                    case DISABLED: state = SourceStatus.State.DISABLED; break;
                    case NOT_CONNECTED: state = SourceStatus.State.SETUP_REQUIRED; break;
                    default: state = SourceStatus.State.UNAVAILABLE;
                }
                statuses.add(new SourceStatus("plex", "Plex library", state, plex.message()));
            } else statuses.add(new SourceStatus("plex", "Plex library", SourceStatus.State.DISABLED,
                    "Plex cards are off. Open Plex setup to enable authorized library reads."));
        } catch (OperationCanceledException canceled) { throw canceled; }
        catch (RuntimeException unavailable) {
            statuses.add(new SourceStatus("plex", "Plex library", SourceStatus.State.UNAVAILABLE,
                    "Plex settings could not be read. Other sources remain available."));
        }
        if (cancellation != null) cancellation.throwIfCanceled();
        try {
            NotificationRecommendations.Result notifications = NotificationRecommendations.read(context, cancellation);
            feeds.addAll(notifications.feeds);
            statuses.add(notifications.status);
        } catch (OperationCanceledException canceled) { throw canceled; }
        catch (RuntimeException unavailable) {
            statuses.add(new SourceStatus("notifications", "Recommendation notifications", SourceStatus.State.UNAVAILABLE,
                    "Notification recommendations could not be read. Other sources remain available."));
        }
        return new TvRecommendations.Result(tv.status, feeds, statuses);
    }

    private static SourceStatus.State tvState(TvRecommendations.Status state) {
        switch (state) {
            case READY: return SourceStatus.State.READY;
            case EMPTY: return SourceStatus.State.EMPTY;
            case PERMISSION_REQUIRED: return SourceStatus.State.SETUP_REQUIRED;
            case PARTIAL: return SourceStatus.State.PARTIAL;
            default: return SourceStatus.State.UNAVAILABLE;
        }
    }
}
