package com.jeremykenedy.firetv.ui.nativeapp.widget.radar;

public final class RadarSnapshot {
    public enum State { READY, STALE, TIMESTAMP_UNKNOWN, OFFLINE }
    public final State state;
    public final RadarSource source;
    public final long fetchedAt, modifiedAt, refreshAt;
    public final String message;
    final RadarCache.Entry image;
    final boolean failed;

    private RadarSnapshot(State state, RadarSource source, RadarCache.Entry image, String message, long refreshAt, boolean failed) {
        this.state = state; this.source = source; this.image = image; this.message = message;
        this.fetchedAt = image == null ? 0 : image.fetchedAt; this.modifiedAt = image == null ? 0 : image.modifiedAt;
        this.refreshAt = refreshAt; this.failed = failed;
    }

    static RadarSnapshot show(RadarSource source, RadarCache.Entry entry, long now, boolean failed) {
        long refresh = failed || entry == null ? now + RadarCache.RETRY_MILLIS
                : Math.max(now + RadarCache.RETRY_MILLIS, entry.freshUntil);
        if (entry == null || !RadarCache.within(entry.fetchedAt, now, RadarCache.MAX_AGE_MILLIS)
                || (entry.modifiedAt > 0 && entry.modifiedAt <= now && now - entry.modifiedAt >= RadarCache.MAX_AGE_MILLIS)) {
            return new RadarSnapshot(State.OFFLINE, source, null, "Radar image unavailable. The source or network may be offline.", refresh, failed);
        }
        if (failed || (now > entry.fetchedAt && now >= entry.freshUntil)
                || (entry.modifiedAt > 0 && entry.modifiedAt <= now && now - entry.modifiedAt > 15 * 60000L)) {
            return new RadarSnapshot(State.STALE, source, entry, "Stale saved image. Check its printed product time.", refresh, failed);
        }
        if (entry.modifiedAt <= 0 || entry.modifiedAt > now + 120000) {
            return new RadarSnapshot(State.TIMESTAMP_UNKNOWN, source, entry, "File age unknown. Check the product time printed in the image.", refresh, failed);
        }
        return new RadarSnapshot(State.READY, source, entry, "Latest downloaded still. Check the product time printed in the image.", refresh, failed);
    }
}
