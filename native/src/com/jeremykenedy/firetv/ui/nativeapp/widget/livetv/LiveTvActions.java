package com.jeremykenedy.firetv.ui.nativeapp.widget.livetv;

import android.content.Context;
import com.jeremykenedy.firetv.ui.nativeapp.providers.RecommendationActions;
import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;

/** A channel source link is not represented as guaranteed tuning or playback. */
public final class LiveTvActions {
    private static TvRecommendations.Program target(LiveGuide.Channel channel) {
        return new TvRecommendations.Program("live-channel:" + channel.key(), channel.publisher, channel.name, null, channel.sourceLink);
    }
    public static boolean available(Context context, LiveGuide.Channel channel) {
        return RecommendationActions.inspect(context, target(channel)).title;
    }
    public static RecommendationActions.Outcome open(Context context, LiveGuide.Channel channel) {
        return RecommendationActions.launchTitle(context, target(channel));
    }
    private LiveTvActions() {}
}
