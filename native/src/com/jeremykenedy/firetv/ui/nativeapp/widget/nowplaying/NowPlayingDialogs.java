package com.jeremykenedy.firetv.ui.nativeapp.widget.nowplaying;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.providers.NotificationRecommendations;

final class NowPlayingDialogs {
    private NowPlayingDialogs() {}
    static void show(final Activity activity) {
        final NowPlayingSessions.Snapshot snapshot = NowPlayingSessions.read(activity);
        if (snapshot.state != NowPlayingSessions.State.READY) {
            AlertDialog.Builder dialog = new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle("Now Playing").setMessage(snapshot.message())
                    .setNegativeButton("Close", null);
            if (snapshot.state == NowPlayingSessions.State.SETUP_REQUIRED) {
                dialog.setMessage(snapshot.message() + " This shared Android access does not turn on recommendation notification cards.");
                dialog.setPositiveButton("Open notification access settings", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface ignored, int which) {
                        try { activity.startActivity(NotificationRecommendations.accessSettings()); }
                        catch (RuntimeException unavailable) { Toast.makeText(activity, "Notification access settings are unavailable on this device.", Toast.LENGTH_LONG).show(); }
                    }
                });
            } else dialog.setPositiveButton("Refresh", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface ignored, int which) { show(activity); }
            });
            dialog.show();
            return;
        }
        String[] labels = new String[snapshot.sessions.size()];
        for (int index = 0; index < labels.length; index++) {
            NowPlayingSessions.Session session = snapshot.sessions.get(index);
            labels[index] = session.title + "\n" + session.app + " · " + session.playback;
        }
        new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle("Published media sessions").setItems(labels, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface ignored, int which) { controls(activity, snapshot.sessions.get(which)); }
        }).setNegativeButton("Close", null).show();
    }

    private static void controls(final Activity activity, final NowPlayingSessions.Session session) {
        if (session.actions.isEmpty()) {
            new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle(session.title).setMessage(session.app + "\n" + session.playback
                    + "\nThis app has not advertised supported playback controls.").setPositiveButton("Done", null).show();
            return;
        }
        String[] labels = new String[session.actions.size()];
        for (int index = 0; index < labels.length; index++) labels[index] = session.actions.get(index).label;
        new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle(session.title).setItems(labels, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface ignored, int which) {
                SessionControls.Action action = session.actions.get(which);
                NowPlayingSessions.Outcome outcome = NowPlayingSessions.request(activity, session, action);
                String message = outcome == NowPlayingSessions.Outcome.REQUESTED ? action.label + " requested from " + session.app + "."
                        : outcome == NowPlayingSessions.Outcome.UNAVAILABLE ? "This session or playback action is no longer available." : "The media control request failed.";
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
            }
        }).setNegativeButton("Cancel", null).show();
    }
}
