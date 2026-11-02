package com.jeremykenedy.firetv.ui.nativeapp.widget.nowplaying;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;

public final class NowPlayingWidgetProvider implements WidgetProvider {
    public static final LayoutItem ITEM = new LayoutItem("now-playing", LayoutItem.Kind.WIDGET);
    @Override public LayoutItem item() { return ITEM; }
    @Override public String title() { return "Now Playing"; }
    @Override public State state(Context context) {
        switch (NowPlayingSessions.read(context).state) {
            case SETUP_REQUIRED: return State.SETUP_REQUIRED;
            case EMPTY: return State.EMPTY;
            case UNAVAILABLE: return State.UNAVAILABLE;
            default: return State.READY;
        }
    }
    @Override public String message(Context context) { return NowPlayingSessions.read(context).message(); }
    @Override public int widthDp() { return 400; }
    @Override public int heightDp() { return 300; }
    @Override public View createView(Context context) { return new NowPlayingView(context); }
    @Override public AutoCloseable observe(Context context, Runnable changed) {
        NowPlayingSessions.Observer observer = NowPlayingSessions.observe(context, changed);
        if (observer.start()) return observer;
        observer.close();
        return null;
    }
    @Override public void configure(Activity activity) { NowPlayingDialogs.show(activity); }
    @Override public void onStart(View view) { if (view instanceof NowPlayingView) ((NowPlayingView) view).start(); }
    @Override public void onStop(View view) { if (view instanceof NowPlayingView) ((NowPlayingView) view).stop(); }
}
