package com.jeremykenedy.firetv.ui.nativeapp.widget.status;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.ScrollView;

/** The settings action opens an actual read-only snapshot; Back returns to the editor. */
final class StatusDetails {
    private StatusDetails() {}
    static void show(Activity activity, String title, StatusWidgetView.Reader reader) {
        ScrollView scroll = new ScrollView(activity);
        final StatusWidgetView snapshot = new StatusWidgetView(activity, title, reader);
        snapshot.showFullDetails();
        scroll.addView(snapshot);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(title).setView(scroll)
                .setPositiveButton("Done", null).create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface closed) { snapshot.stop(); }
        });
        dialog.show();
        snapshot.start();
    }
}
