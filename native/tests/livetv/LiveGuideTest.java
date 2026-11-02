package com.jeremykenedy.firetv.ui.nativeapp.widget.livetv;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;

public final class LiveGuideTest {
    private static final LiveGuide.Channel CHANNEL = new LiveGuide.Channel(1, "example.source", "Station", "");
    public static void main(String[] args) {
        LiveGuide.Program first = new LiveGuide.Program("First", 1000, 2000);
        LiveGuide.Program next = new LiveGuide.Program("Next", 2000, 3000);
        LiveGuide.Listing before = LiveGuide.select(CHANNEL, Arrays.asList(next, first), 1500);
        check(before.now == first && before.next == next && before.state == LiveGuide.State.READY, "Unsorted guide produces actual now and next");
        LiveGuide.Listing boundary = LiveGuide.select(CHANNEL, Arrays.asList(first, next), 2000);
        check(boundary.now == next && boundary.next == null, "Ended program never remains current at boundary");
        check(LiveGuide.select(CHANNEL, Arrays.asList(first, next), 3000).state == LiveGuide.State.STALE, "Ended-only EPG is stale");
        check(LiveGuide.select(CHANNEL, Collections.<LiveGuide.Program>emptyList(), 3000).state == LiveGuide.State.MISSING, "Absent EPG is missing");
        check(LiveGuide.select(CHANNEL, Arrays.asList(next), 1500).state == LiveGuide.State.GAP, "Future EPG without current program is an explicit gap");
        LiveGuide.Channel other = new LiveGuide.Channel(1, "other.source", "Station", "");
        check(!CHANNEL.key().equals(other.key()), "Favorite identity is publisher scoped");
        LiveGuide.Channel published = new LiveGuide.Channel(2, "example.source", "Station", "", "source/input", "station-one");
        LiveGuide.Channel refreshed = new LiveGuide.Channel(99, "example.source", "Station", "", "source/input", "station-one");
        check(published.key().equals(refreshed.key()), "Publisher stable channel identifier survives row replacement");
        LiveGuide.favorites(Arrays.asList(published.key()));
        rejects(new Runnable() { public void run() { LiveGuide.favorites(Arrays.asList(CHANNEL.key(), CHANNEL.key())); }}, "Duplicate favorites rejected");
        rejects(new Runnable() { public void run() { LiveGuide.zone("Not/AZone"); }}, "Invalid timezone does not silently fall back");
        DateTimeFormatter format = DateTimeFormatter.ofPattern("HH:mm XXX").withZone(ZoneId.of(LiveGuide.zone("America/Los_Angeles")));
        check("01:30 -07:00".equals(format.format(Instant.parse("2026-11-01T08:30:00Z"))), "Guide time before DST fall-back");
        check("01:30 -08:00".equals(format.format(Instant.parse("2026-11-01T09:30:00Z"))), "Guide time after DST fall-back");
        System.out.println("Live guide now/next, stale/missing, publisher identity and timezone checks passed");
    }
    private static void rejects(Runnable action, String message) { try { action.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError(message); }
    private static void check(boolean result, String message) { if (!result) throw new AssertionError(message); }
}
