package com.jeremykenedy.firetv.ui.nativeapp.widget.status;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;

public final class TvAudioStatusWidgetProvider implements WidgetProvider {
    public static final LayoutItem ITEM = new LayoutItem("tv-audio-status", LayoutItem.Kind.WIDGET);
    private static final StatusWidgetView.Reader READER = new StatusWidgetView.Reader() {
        @Override public StatusReport read(Context context) { return TvAudioStatus.read(context); }
    };
    @Override public LayoutItem item() { return ITEM; }
    @Override public String title() { return "TV and audio status"; }
    @Override public State state(Context context) {
        StatusReport.Availability available = TvAudioStatus.read(context).availability;
        if (available == StatusReport.Availability.EMPTY) return State.EMPTY;
        return available == StatusReport.Availability.UNAVAILABLE || available == StatusReport.Availability.PERMISSION_DENIED
                ? State.UNAVAILABLE : State.READY;
    }
    @Override public String message(Context context) {
        StatusReport report = TvAudioStatus.read(context);
        if (report.availability == StatusReport.Availability.PERMISSION_DENIED) return "Android denied access to TV/audio status.";
        if (report.availability == StatusReport.Availability.UNAVAILABLE) return "TV/audio services are unavailable on this device.";
        if (report.availability == StatusReport.Availability.PARTIAL) return "Some TV/audio details are unavailable or access was denied. Open this widget's settings for the observed fields.";
        return "Display, media volume and available audio outputs.";
    }
    @Override public boolean canRender(State state) { return true; }
    @Override public int widthDp() { return 480; }
    @Override public int heightDp() { return 360; }
    @Override public View createView(Context context) {
        StatusWidgetView view = new StatusWidgetView(context, title(), READER);
        return view;
    }
    @Override public void configure(Activity activity) { StatusDetails.show(activity, title(), READER); }
    @Override public void onStart(View view) { ((StatusWidgetView) view).start(); }
    @Override public void onStop(View view) { ((StatusWidgetView) view).stop(); }
}
