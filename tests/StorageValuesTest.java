package com.jeremykenedy.firetv.ui;

public final class StorageValuesTest {
    public static void main(String[] args) {
        check(-1, 0, 69011424, 100, 10);
        check(-1, -1, 69011424, 100, 10);
        check(-1, 69011424, 4608, 77824, 1024);
        check(-1, 100, -1, 0, 0);
        check(-1, 100, 100, -1, 0);
        check(-1, 100, 100, 0, -1);
        check(-1, 100, 100, 10, 11);
        check(-1, 100, Long.MAX_VALUE, 1, 0);
        check(69011424, 69011424, 69011424, 0, 0);
        check(Long.MAX_VALUE, 100, Long.MAX_VALUE - 10, 10, 10);
        check(500, 100, 200, 300, 100);
        System.out.println("Storage statistics floor and accounting checks passed.");
    }

    private static void check(long expected, long apk, long app, long data, long cache) {
        if (StorageValues.usedBytes(apk, app, data, cache) != expected) {
            throw new AssertionError("Unexpected storage validity: " + apk + "," + app
                    + "," + data + "," + cache);
        }
    }
}
