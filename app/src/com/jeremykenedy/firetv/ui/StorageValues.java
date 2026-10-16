package com.jeremykenedy.firetv.ui;

/** Checks reported usage against the package files actually installed on disk. */
public final class StorageValues {
    private StorageValues() {}

    public static long usedBytes(long apkBytes, long appBytes, long dataBytes, long cacheBytes) {
        boolean valid = apkBytes > 0
                && appBytes >= apkBytes
                && dataBytes >= 0
                && cacheBytes >= 0
                && cacheBytes <= dataBytes
                && appBytes <= Long.MAX_VALUE - dataBytes;
        return valid ? appBytes + dataBytes : -1;
    }
}
