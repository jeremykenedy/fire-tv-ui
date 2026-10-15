package com.jeremykenedy.firetv.ui;

public final class InputValuesTest {
    private static void expect(int expected, int type, String label, int port) {
        int actual = InputValues.amazonType(type, label, port);
        if (actual != expected) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) {
        for (int port = 1; port <= 4; port++) {
            int expected = port == 4 ? 10 : port + 1;
            expect(expected, 1007, "Renamed console", port);
            expect(expected, 1007, "HDMI " + port, -1);
        }
        expect(3, 1007, " hdmi2 (ARC) ", -1);
        expect(2, 1007, "HDMI 4", 1);
        expect(-1, 1007, "HDMI 10", -1);
        expect(-1, 1007, "HDMI 0", -1);
        expect(-1, 1007, null, -1);
        expect(-1, 1007, "Console", 5);
        expect(-1, 0, "HDMI 1", 1);
        expect(11, 1001, "AV", -1);
        expect(12, 1004, "Component", -1);
        System.out.println("17 physical input mapping checks passed.");
    }
}
