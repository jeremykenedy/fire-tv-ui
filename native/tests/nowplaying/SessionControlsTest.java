package com.jeremykenedy.firetv.ui.nativeapp.widget.nowplaying;

import android.media.session.PlaybackState;
import java.util.Arrays;
import java.util.Collections;

public final class SessionControlsTest {
    public static void main(String[] args) {
        check(SessionControls.supported(0, PlaybackState.STATE_PLAYING).isEmpty(), "Playback state alone must not invent controls.");
        check(SessionControls.supported(PlaybackState.ACTION_PLAY_PAUSE, PlaybackState.STATE_PAUSED)
                .equals(Collections.singletonList(SessionControls.Action.PLAY)), "A paused toggle-only session offers Play.");
        for (int state : new int[]{PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING})
            check(SessionControls.supported(PlaybackState.ACTION_PLAY_PAUSE, state)
                    .equals(Collections.singletonList(SessionControls.Action.PAUSE)), "An active toggle-only session offers Pause.");
        check(SessionControls.supported(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SKIP_TO_NEXT,
                PlaybackState.STATE_STOPPED).equals(Arrays.asList(SessionControls.Action.PLAY, SessionControls.Action.NEXT, SessionControls.Action.STOP)),
                "Only explicitly advertised supported controls appear.");
        check(SessionControls.supported(PlaybackState.ACTION_SEEK_TO, PlaybackState.STATE_PLAYING).isEmpty(),
                "Unsupported seek is not represented as another action.");
        check(SessionControls.stateLabel(PlaybackState.STATE_STOPPED).equals("Stopped"), "An active session need not be playing.");
        check(SessionControls.stateLabel(-999).equals("Playback status unavailable"), "Unknown states never claim playback.");
        check(SessionControls.text("  Name\nArtist\r ", "missing").equals("Name Artist"), "Published multiline text stays bounded to one line.");
        check(SessionControls.text(null, "missing").equals("missing") && SessionControls.text("  ", "missing").equals("missing"),
                "Missing metadata is explicit.");
        check(SessionControls.text(String.join("", Collections.nCopies(513, "x")), "missing").equals("missing"),
                "Oversized metadata is rejected.");
        System.out.println("Advertised controls, toggle states, truthful playback labels and bounded metadata passed.");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
