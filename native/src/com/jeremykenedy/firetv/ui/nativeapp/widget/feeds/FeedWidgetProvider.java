package com.jeremykenedy.firetv.ui.nativeapp.widget.feeds;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;

public final class FeedWidgetProvider implements WidgetProvider {
    public static final LayoutItem ITEM = new LayoutItem("custom-feed", LayoutItem.Kind.WIDGET);
    @Override public LayoutItem item() { return ITEM; }
    @Override public String title() { return "Custom feed"; }
    @Override public State state(Context context) {
        try {
            FeedSource source = new FeedStore(context).read();
            if (source == null) return State.SETUP_REQUIRED;
            FeedSnapshot snapshot = FeedMemory.read(source);
            if (snapshot.failed || snapshot.stale(System.currentTimeMillis())) return State.UNAVAILABLE;
            return snapshot.visible(new FeedStore(context).hidden(source)).items.isEmpty() ? State.EMPTY : State.READY;
        } catch (Exception unavailable) { return State.UNAVAILABLE; }
    }
    @Override public String message(Context context) {
        try {
            FeedSource source = new FeedStore(context).read();
            return source == null ? "Select one HTTPS RSS 2.0 or JSON Feed source for headlines. Hide individual headlines or restore them in the feed panel. This widget starts hidden."
                    : source.display() + ". " + FeedMemory.read(source).message(System.currentTimeMillis());
        } catch (Exception unavailable) { return "Saved source unavailable. Review Custom feed settings."; }
    }
    @Override public int widthDp() { return 420; }
    @Override public int heightDp() { return 360; }
    @Override public View createView(Context context) { return new FeedView(context); }
    @Override public void configure(Activity activity) { FeedPanel.show(activity); }
    @Override public void onStart(View view) { if (view instanceof FeedView) ((FeedView) view).start(); }
    @Override public void onStop(View view) { if (view instanceof FeedView) ((FeedView) view).stop(); }
}
