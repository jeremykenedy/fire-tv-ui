package com.jeremykenedy.firetv.ui.nativeapp.widget.feeds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The latest successful items may stay visible after a failed refresh, always marked stale. */
public final class FeedSnapshot {
    public static final long REFRESH_MS = 15 * 60 * 1000L;
    public static final long STALE_MS = 30 * 60 * 1000L;
    public final List<FeedParser.Item> items;
    public final long fetchedAt;
    public final boolean failed;
    public FeedSnapshot(List<FeedParser.Item> items, long fetchedAt, boolean failed) {
        this.items = Collections.unmodifiableList(new ArrayList<>(items)); this.fetchedAt = fetchedAt; this.failed = failed;
    }
    public static String hiddenKey(String id) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public FeedSnapshot visible(java.util.Set<String> hidden) {
        List<FeedParser.Item> visible = new ArrayList<>();
        for (FeedParser.Item item : items) if (!hidden.contains(hiddenKey(item.id))) visible.add(item);
        return new FeedSnapshot(visible, fetchedAt, failed);
    }
    public static FeedSnapshot empty() { return new FeedSnapshot(Collections.<FeedParser.Item>emptyList(), 0, false); }
    public FeedSnapshot success(List<FeedParser.Item> next, long now) { return new FeedSnapshot(next, now, false); }
    public FeedSnapshot failure() { return new FeedSnapshot(items, fetchedAt, true); }
    public boolean stale(long now) { return fetchedAt > 0 && (failed || now < fetchedAt || now - fetchedAt >= STALE_MS); }
    public String message(long now) {
        if (fetchedAt == 0) return failed ? "Feed unavailable. Review the selected source or retry." : "Selected source has not been fetched yet.";
        if (stale(now)) return items.isEmpty() ? "Last refresh failed or is stale. No saved headlines." : "Stale headlines. The selected source could not be refreshed recently.";
        return items.isEmpty() ? "The selected source has no supported headline links." : "Headlines from the selected source. Open a headline to review its link.";
    }
}
