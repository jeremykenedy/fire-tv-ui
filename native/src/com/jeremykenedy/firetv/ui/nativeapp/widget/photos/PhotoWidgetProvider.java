package com.jeremykenedy.firetv.ui.nativeapp.widget.photos;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.view.View;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;

public final class PhotoWidgetProvider implements WidgetProvider {
    public static final LayoutItem ITEM = new LayoutItem("photo-frame", LayoutItem.Kind.WIDGET);
    @Override public LayoutItem item() { return ITEM; }
    @Override public String title() { return "Photo frame"; }
    @Override public State state(Context context) {
        try {
            PhotoOptions options = PhotoPreferences.load(context);
            if (!options.configured) return State.SETUP_REQUIRED;
            if (options.photos.isEmpty()) return State.EMPTY;
            for (PhotoOptions.Photo photo : options.photos) if (hasGrant(context, Uri.parse(photo.uri))) return State.READY;
            return State.UNAVAILABLE;
        } catch (IllegalStateException error) { return State.UNAVAILABLE; }
    }
    @Override public String message(Context context) {
        State state = state(context);
        return state == State.SETUP_REQUIRED ? "Choose personal photos. This widget starts hidden."
                : state == State.EMPTY ? "No selected photos. Open to add images."
                : state == State.UNAVAILABLE ? "Photo access is unavailable. Open to reconnect. Saved selections are kept."
                : "Selected documents are checked when displayed. Private photo access stays on this TV.";
    }
    @Override public int widthDp() { return 400; }
    @Override public int heightDp() { return 225; }
    @Override public View createView(Context context) { return new PhotoFrameView(context); }
    @Override public void configure(Activity activity) { activity.startActivity(new Intent(activity, PhotoSettingsActivity.class)); }
    @Override public void onStart(View view) { if (view instanceof PhotoFrameView) ((PhotoFrameView) view).start(); }
    @Override public void onStop(View view) { if (view instanceof PhotoFrameView) ((PhotoFrameView) view).stop(); }
    static boolean hasGrant(Context context, Uri uri) {
        for (UriPermission grant : context.getContentResolver().getPersistedUriPermissions())
            if (grant.isReadPermission() && uri.equals(grant.getUri())) return true;
        return false;
    }
}
