package com.jeremykenedy.firetv.ui.nativeapp.widget.feeds;

/** At most one selected source is cached in memory. Headlines and private URLs are not persisted. */
final class FeedMemory {
    private static FeedSource source;
    private static FeedSnapshot snapshot = FeedSnapshot.empty();
    private FeedMemory() {}
    static synchronized FeedSnapshot read(FeedSource selected) {
        return selected.equals(source) ? snapshot : FeedSnapshot.empty();
    }
    static synchronized void clear() { source = null; snapshot = FeedSnapshot.empty(); }
    static synchronized void write(FeedSource selected, FeedSnapshot next) { source = selected; snapshot = next; }
}
