package com.jeremykenedy.firetv.ui;

import java.math.BigInteger;

/** Compares plain published numeric versions without guessing mirror tag meanings. */
final class UpdateVersions {
    private UpdateVersions() {
    }

    static boolean cacheFresh(long checkedAt, long now) {
        return checkedAt > 0 && checkedAt <= now && now - checkedAt < 6 * 60 * 60 * 1000L;
    }

    static Integer compare(String published, String installed) {
        String first = numeric(published);
        String second = numeric(installed);
        if (first == null || second == null) {
            return null;
        }
        String[] left = first.split("\\.");
        String[] right = second.split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            BigInteger a = new BigInteger(i < left.length ? left[i] : "0");
            BigInteger b = new BigInteger(i < right.length ? right[i] : "0");
            int order = a.compareTo(b);
            if (order != 0) {
                return order;
            }
        }
        return 0;
    }

    private static String numeric(String value) {
        if (value == null || value.length() > 64 || !value.matches("v?[0-9]+(\\.[0-9]+){0,3}")) {
            return null;
        }
        return value.startsWith("v") ? value.substring(1) : value;
    }
}
