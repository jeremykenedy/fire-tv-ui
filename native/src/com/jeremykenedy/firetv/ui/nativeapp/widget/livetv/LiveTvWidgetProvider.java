package com.jeremykenedy.firetv.ui.nativeapp.widget.livetv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.tv.TvContract;
import android.view.View;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;

public final class LiveTvWidgetProvider implements WidgetProvider {
    public static final LayoutItem ITEM = new LayoutItem("live-tv-favorites", LayoutItem.Kind.WIDGET);
    @Override public LayoutItem item() { return ITEM; }
    @Override public String title() { return "Live TV favorites now/next"; }
    @Override public State state(Context context) {
        try {
            if (context.checkSelfPermission(LiveTvRepository.PERMISSION) != PackageManager.PERMISSION_GRANTED) return State.SETUP_REQUIRED;
            if (context.getPackageManager().resolveContentProvider(TvContract.AUTHORITY, 0) == null) return State.UNAVAILABLE;
            return LiveTvPreferences.read(context).favorites.isEmpty() ? State.EMPTY : State.READY;
        } catch (RuntimeException unavailable) { return State.UNAVAILABLE; }
    }
    @Override public String message(Context context) {
        switch (state(context)) {
            case SETUP_REQUIRED: return "Allow ordinary TV listings access, then select favorite live channels.";
            case EMPTY: return "Choose favorite channels from a configured TV source with a usable EPG.";
            case UNAVAILABLE: return "TV listings or saved favorites are unavailable. Saved data was kept.";
            default: return "Guide states come from each source. A source link does not guarantee tuning or playback.";
        }
    }
    @Override public int widthDp() { return 460; }
    @Override public int heightDp() { return 420; }
    @Override public View createView(Context context) { return new LiveTvView(context); }
    @Override public void configure(Activity activity) { activity.startActivity(new Intent(activity, LiveTvSettingsActivity.class)); }
    @Override public void onStart(View view) { if (view instanceof LiveTvView) ((LiveTvView) view).start(); }
    @Override public void onStop(View view) { if (view instanceof LiveTvView) ((LiveTvView) view).stop(); }
}
