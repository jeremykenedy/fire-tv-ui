package com.jeremykenedy.firetv.ui.nativeapp.widget.nowplaying;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import com.jeremykenedy.firetv.ui.nativeapp.providers.NotificationRecommendations;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reads only media sessions published to this explicitly authorized notification listener. */
public final class NowPlayingSessions {
    public enum State { READY, EMPTY, SETUP_REQUIRED, UNAVAILABLE }
    public enum Outcome { REQUESTED, UNAVAILABLE, FAILED }
    private static final int MAX_SESSIONS = 8;

    public static final class Session {
        final MediaController controller;
        public final String source;
        public final String app;
        public final String title;
        public final String artist;
        public final String playback;
        public final List<SessionControls.Action> actions;
        Session(Context context, MediaController controller) {
            this.controller = controller;
            source = controller.getPackageName();
            String label = source;
            try { label = context.getPackageManager().getApplicationLabel(
                    context.getPackageManager().getApplicationInfo(source, 0)).toString(); }
            catch (PackageManager.NameNotFoundException | RuntimeException unavailable) { }
            app = SessionControls.text(label, source);
            MediaMetadata metadata = controller.getMetadata();
            title = metadata == null ? "Title unavailable" : SessionControls.text(metadata.getText(MediaMetadata.METADATA_KEY_TITLE), "Title unavailable");
            artist = metadata == null ? "" : SessionControls.text(metadata.getText(MediaMetadata.METADATA_KEY_ARTIST), "");
            PlaybackState state = controller.getPlaybackState();
            playback = SessionControls.stateLabel(state == null ? PlaybackState.STATE_NONE : state.getState());
            actions = state == null ? Collections.<SessionControls.Action>emptyList() : SessionControls.supported(state.getActions(), state.getState());
        }
    }

    public static final class Snapshot {
        public final State state;
        public final List<Session> sessions;
        Snapshot(State state, List<Session> sessions) {
            this.state = state;
            this.sessions = Collections.unmodifiableList(new ArrayList<>(sessions));
        }
        public String message() {
            switch (state) {
                case SETUP_REQUIRED: return "Allow notification access in Android settings to read published media sessions.";
                case UNAVAILABLE: return "Media sessions are unavailable on this device or access could not be read.";
                case EMPTY: return "No apps currently publish an active media session.";
                default: return "Showing " + sessions.size() + (sessions.size() == 1 ? " published media session. " : " published media sessions. ")
                        + "Up to eight sessions are shown. Only actions advertised by each app are offered.";
            }
        }
    }

    private NowPlayingSessions() {}
    private static ComponentName listener(Context context) { return new ComponentName(context, NotificationRecommendations.class); }

    public static Snapshot read(Context context) {
        List<Session> sessions = new ArrayList<>();
        try {
            MediaSessionManager manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (manager == null) return new Snapshot(State.UNAVAILABLE, sessions);
            if (!NotificationRecommendations.accessGranted(context)) return new Snapshot(State.SETUP_REQUIRED, sessions);
            List<MediaController> controllers = manager.getActiveSessions(listener(context));
            if (controllers == null) return new Snapshot(State.UNAVAILABLE, sessions);
            for (MediaController controller : controllers) {
                if (controller == null) continue;
                if (sessions.size() >= MAX_SESSIONS) break;
                sessions.add(new Session(context, controller));
            }
            return new Snapshot(sessions.isEmpty() ? State.EMPTY : State.READY, sessions);
        } catch (SecurityException denied) {
            return new Snapshot(NotificationRecommendations.accessGranted(context) ? State.UNAVAILABLE : State.SETUP_REQUIRED,
                    Collections.<Session>emptyList());
        } catch (RuntimeException unavailable) { return new Snapshot(State.UNAVAILABLE, Collections.<Session>emptyList()); }
    }

    public static Outcome request(Context context, Session selected, SessionControls.Action action) {
        if (selected == null || action == null) return Outcome.UNAVAILABLE;
        Snapshot current = read(context);
        for (Session session : current.sessions) {
            if (!session.source.equals(selected.source) || !session.controller.getSessionToken().equals(selected.controller.getSessionToken())) continue;
            if (!session.actions.contains(action)) return Outcome.UNAVAILABLE;
            try {
                MediaController.TransportControls controls = session.controller.getTransportControls();
                switch (action) {
                    case PLAY: controls.play(); break;
                    case PAUSE: controls.pause(); break;
                    case PREVIOUS: controls.skipToPrevious(); break;
                    case NEXT: controls.skipToNext(); break;
                    case STOP: controls.stop(); break;
                }
                return Outcome.REQUESTED;
            } catch (RuntimeException failed) { return Outcome.FAILED; }
        }
        return Outcome.UNAVAILABLE;
    }

    /** Observe session-list changes even while the Home slot is in its empty state. */
    public static Observer observe(Context context, Runnable changed) { return new Observer(context, changed); }

    public static final class Observer implements AutoCloseable {
        private final Context context;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable changed;
        private MediaSessionManager manager;
        private boolean started;
        private final MediaSessionManager.OnActiveSessionsChangedListener listener = new MediaSessionManager.OnActiveSessionsChangedListener() {
            @Override public void onActiveSessionsChanged(List<MediaController> controllers) { if (started) changed.run(); }
        };
        Observer(Context context, Runnable changed) { this.context = context.getApplicationContext(); this.changed = changed; }
        public boolean start() {
            if (started) {
                if (NotificationRecommendations.accessGranted(context)) return true;
                stop();
                return false;
            }
            if (!NotificationRecommendations.accessGranted(context)) return false;
            try {
                manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
                if (manager == null) return false;
                manager.addOnActiveSessionsChangedListener(listener, NowPlayingSessions.listener(context), handler);
                started = true;
                return true;
            } catch (RuntimeException unavailable) { manager = null; return false; }
        }
        public void stop() {
            started = false;
            if (manager != null) {
                try { manager.removeOnActiveSessionsChangedListener(listener); }
                catch (RuntimeException unavailable) { }
            }
            manager = null;
            handler.removeCallbacksAndMessages(null);
        }
        @Override public void close() { stop(); }
    }
}
