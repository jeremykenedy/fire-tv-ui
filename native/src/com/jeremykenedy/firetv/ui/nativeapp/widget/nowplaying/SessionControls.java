package com.jeremykenedy.firetv.ui.nativeapp.widget.nowplaying;

import android.media.session.PlaybackState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SessionControls {
    public enum Action {
        PLAY("Play"), PAUSE("Pause"), PREVIOUS("Previous"), NEXT("Next"), STOP("Stop");
        public final String label;
        Action(String label) { this.label = label; }
    }
    private SessionControls() {}

    public static List<Action> supported(long actions, int state) {
        List<Action> result = new ArrayList<>();
        boolean active = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
                || state == PlaybackState.STATE_CONNECTING;
        boolean toggle = (actions & PlaybackState.ACTION_PLAY_PAUSE) != 0;
        if ((actions & PlaybackState.ACTION_PLAY) != 0 || (toggle && !active)) result.add(Action.PLAY);
        if ((actions & PlaybackState.ACTION_PAUSE) != 0 || (toggle && active)) result.add(Action.PAUSE);
        if ((actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0) result.add(Action.PREVIOUS);
        if ((actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0) result.add(Action.NEXT);
        if ((actions & PlaybackState.ACTION_STOP) != 0) result.add(Action.STOP);
        return Collections.unmodifiableList(result);
    }

    public static String stateLabel(int state) {
        switch (state) {
            case PlaybackState.STATE_PLAYING: return "Playing";
            case PlaybackState.STATE_PAUSED: return "Paused";
            case PlaybackState.STATE_BUFFERING: return "Buffering";
            case PlaybackState.STATE_CONNECTING: return "Connecting";
            case PlaybackState.STATE_STOPPED: return "Stopped";
            case PlaybackState.STATE_ERROR: return "Playback error";
            case PlaybackState.STATE_FAST_FORWARDING: return "Fast forwarding";
            case PlaybackState.STATE_REWINDING: return "Rewinding";
            case PlaybackState.STATE_SKIPPING_TO_NEXT: return "Skipping to next";
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS: return "Skipping to previous";
            case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM: return "Changing queue item";
            default: return "Playback status unavailable";
        }
    }

    public static String text(CharSequence value, String fallback) {
        if (value == null || value.length() > 512) return fallback;
        String text = value.toString().replace('\n', ' ').replace('\r', ' ').trim();
        return text.isEmpty() ? fallback : text;
    }
}
