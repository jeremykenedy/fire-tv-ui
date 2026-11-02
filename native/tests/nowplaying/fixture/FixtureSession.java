package com.jeremykenedy.firetv.nowplayingfixture;

import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;

final class FixtureSession {
    private static MediaSession session;
    private static String title = "Native media fixture";
    private FixtureSession() {}
    static void publish(Context context) {
        release();
        title = "Native media fixture";
        session = new MediaSession(context.getApplicationContext(), "native-nowplaying-proof");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPause() { update(PlaybackState.STATE_PAUSED); }
            @Override public void onPlay() { update(PlaybackState.STATE_PLAYING); }
            @Override public void onSkipToNext() { title = "Native next fixture"; update(PlaybackState.STATE_PAUSED); }
            @Override public void onStop() { title = "Unsupported stop was dispatched"; update(PlaybackState.STATE_ERROR); }
        }, new Handler(Looper.getMainLooper()));
        session.setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS | MediaSession.FLAG_HANDLES_MEDIA_BUTTONS);
        update(PlaybackState.STATE_PLAYING);
        session.setActive(true);
    }
    static void release() { if (session != null) { session.setActive(false); session.release(); session = null; } }
    private static void update(int state) {
        if (session == null) return;
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Fixture publisher").build());
        session.setPlaybackState(new PlaybackState.Builder().setState(state, 0, state == PlaybackState.STATE_PLAYING ? 1 : 0)
                .setActions((state == PlaybackState.STATE_PLAYING ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY)
                        | PlaybackState.ACTION_SKIP_TO_NEXT).build());
    }
}
