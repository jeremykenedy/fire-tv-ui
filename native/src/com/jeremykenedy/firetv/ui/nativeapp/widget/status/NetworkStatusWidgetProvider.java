package com.jeremykenedy.firetv.ui.nativeapp.widget.status;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;

public final class NetworkStatusWidgetProvider implements WidgetProvider {
    public static final LayoutItem ITEM = new LayoutItem("network-status", LayoutItem.Kind.WIDGET);
    private static final StatusWidgetView.Reader READER = new StatusWidgetView.Reader() {
        @Override public StatusReport read(Context context) { return NetworkStatus.read(context); }
    };
    @Override public LayoutItem item() { return ITEM; }
    @Override public String title() { return "Network status"; }
    @Override public State state(Context context) {
        StatusReport.Availability available = NetworkStatus.read(context).availability;
        if (available == StatusReport.Availability.EMPTY) return State.EMPTY;
        return available == StatusReport.Availability.UNAVAILABLE || available == StatusReport.Availability.PERMISSION_DENIED
                ? State.UNAVAILABLE : State.READY;
    }
    @Override public String message(Context context) {
        StatusReport report = NetworkStatus.read(context);
        if (report.availability == StatusReport.Availability.PERMISSION_DENIED) return "Android denied access to network status. Inspect the TV's network settings.";
        if (report.availability == StatusReport.Availability.UNAVAILABLE) return "Network status could not be read on this device.";
        if (report.availability == StatusReport.Availability.EMPTY) return "Android reports no visible connected network. This does not prove a router or internet outage.";
        return "Read-only physical and VPN network details; VPN addresses are labeled as tunnels. Internet validation is reported by Android.";
    }
    @Override public boolean canRender(State state) { return true; }
    @Override public int widthDp() { return 480; }
    @Override public int heightDp() { return 360; }
    @Override public View createView(Context context) { return new StatusWidgetView(context, title(), READER); }
    @Override public void configure(Activity activity) { StatusDetails.show(activity, title(), READER); }
    @Override public void onStart(View view) { ((StatusWidgetView) view).start(); }
    @Override public void onStop(View view) { ((StatusWidgetView) view).stop(); }
}
