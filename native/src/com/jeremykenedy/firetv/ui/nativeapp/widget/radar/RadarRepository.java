package com.jeremykenedy.firetv.ui.nativeapp.widget.radar;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;

final class RadarRepository {
    private final RadarCache cache;
    private final RadarHttp http;
    private final LinkedHashMap<String, Attempt> attempts = new LinkedHashMap<>();
    private static final class Attempt {
        final long at; final boolean failed;
        Attempt(long at, boolean failed) { this.at = at; this.failed = failed; }
    }
    RadarRepository(File folder) { this(folder, new RadarHttp()); }
    RadarRepository(File folder, RadarHttp http) { cache = new RadarCache(folder); this.http = http; }

    synchronized void invalidate(RadarSource source) { cache.remove(source); }

    synchronized RadarSnapshot saved(RadarSource source, long now) {
        return RadarSnapshot.show(source, cache.read(source), now, true);
    }

    synchronized RadarSnapshot read(RadarSource source, long now, RadarRequest request) {
        request.check();
        RadarCache.Entry saved = cache.read(source);
        Attempt previous = attempts.get(source.id);
        if (previous != null && RadarCache.within(previous.at, now, RadarCache.RETRY_MILLIS)) {
            return RadarSnapshot.show(source, saved, now, previous.failed);
        }
        if (saved != null && RadarCache.within(saved.fetchedAt, now, RadarCache.MAX_AGE_MILLIS) && now < saved.freshUntil) return RadarSnapshot.show(source, saved, now, false);
        RadarSnapshot snapshot;
        attempts.put(source.id, new Attempt(now, true));
        try {
            RadarCache.Entry entry = http.get(source, saved, now, request);
            request.check(); cache.write(entry);
            snapshot = RadarSnapshot.show(source, entry, now, false);
        } catch (IOException unavailable) {
            request.check(); snapshot = RadarSnapshot.show(source, saved, now, true);
        }
        attempts.put(source.id, new Attempt(now, snapshot.failed));
        return snapshot;
    }
}
