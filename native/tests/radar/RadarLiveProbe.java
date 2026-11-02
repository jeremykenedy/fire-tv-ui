package com.jeremykenedy.firetv.ui.nativeapp.widget.radar;

/** Optional one-image official-source transport check; excluded from the offline host suite. */
public final class RadarLiveProbe {
    public static void main(String[] args) throws Exception {
        RadarSource source = RadarSource.find("station:KRTX");
        long now = System.currentTimeMillis();
        RadarCache.Entry image = new RadarHttp().get(source, null, now, new RadarRequest());
        RadarSnapshot snapshot = RadarSnapshot.show(source, image, now, false);
        System.out.println(source.imageUrl());
        System.out.println("Encoded bytes=" + image.bytes.length + "; file modified=" + image.modifiedAt
                + "; cache expiry=" + image.freshUntil + "; state=" + snapshot.state);
    }
}
