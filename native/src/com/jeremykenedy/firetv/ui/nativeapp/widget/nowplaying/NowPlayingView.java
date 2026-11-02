package com.jeremykenedy.firetv.ui.nativeapp.widget.nowplaying;

import android.content.Context;
import android.graphics.Color;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public final class NowPlayingView extends TextView {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<MediaController> observed = new ArrayList<>();
    private final NowPlayingSessions.Observer sessions;
    private boolean started;
    private boolean listUpdates;
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) { refreshIfStarted(); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { refreshIfStarted(); }
        @Override public void onSessionDestroyed() { refreshIfStarted(); }
    };

    public NowPlayingView(Context context) {
        super(context);
        setTextColor(Color.WHITE);
        setTextSize(19);
        setMaxLines(12);
        int padding = Math.round(16 * context.getResources().getDisplayMetrics().density);
        setPadding(padding, padding, padding, padding);
        sessions = NowPlayingSessions.observe(context, new Runnable() {
            @Override public void run() { refreshIfStarted(); }
        });
        render(NowPlayingSessions.read(context));
    }

    public void start() {
        if (started) return;
        started = true;
        listUpdates = sessions.start();
        refreshIfStarted();
    }

    public void stop() {
        started = false;
        sessions.stop();
        unobserve();
        handler.removeCallbacksAndMessages(null);
    }

    private void refreshIfStarted() {
        if (!started) return;
        unobserve();
        NowPlayingSessions.Snapshot snapshot = NowPlayingSessions.read(getContext());
        for (NowPlayingSessions.Session session : snapshot.sessions) {
            try { session.controller.registerCallback(callback, handler); observed.add(session.controller); }
            catch (RuntimeException unavailable) { listUpdates = false; }
        }
        render(snapshot);
    }

    private void unobserve() {
        for (MediaController controller : observed) {
            try { controller.unregisterCallback(callback); }
            catch (RuntimeException unavailable) { }
        }
        observed.clear();
    }

    private void render(NowPlayingSessions.Snapshot snapshot) {
        StringBuilder text = new StringBuilder("Now Playing\n");
        if (snapshot.sessions.isEmpty()) text.append(snapshot.message());
        for (int index = 0; index < Math.min(2, snapshot.sessions.size()); index++) {
            NowPlayingSessions.Session session = snapshot.sessions.get(index);
            text.append('\n').append(session.title);
            if (!session.artist.isEmpty()) text.append("\n").append(session.artist);
            text.append("\n").append(session.app).append(" · ").append(session.playback).append('\n');
        }
        if (snapshot.sessions.size() > 2) text.append("\nSelect to view more published sessions.");
        else if (!snapshot.sessions.isEmpty()) text.append("\nSelect for supported media controls.");
        if (started && !listUpdates) text.append("\nLive updates unavailable; select to refresh.");
        setText(text);
    }

    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
}
