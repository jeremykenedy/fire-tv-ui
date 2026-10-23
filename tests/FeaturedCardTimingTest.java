package com.jeremykenedy.firetv.ui;

public final class FeaturedCardTimingTest {
    public static void main(String[] args) {
        android.content.SharedPreferences values = new android.content.SharedPreferences();
        check(
                FeaturedCardTiming.delay(values) == 8000,
                "Existing rotation must keep its eight-second default.");
        values.value = 5;
        check(
                FeaturedCardTiming.delay(values) == 5000,
                "The engine must read the selected five-second interval.");
        values.value = 30;
        check(
                FeaturedCardTiming.delay(values) == 30000,
                "Subsequent rotation loops must read changed preferences.");
        values.value = 0;
        check(
                FeaturedCardTiming.delay(values) == Long.MAX_VALUE,
                "Manual mode must suspend automatic advancement instead of spinning a zero-delay"
                    + " loop.");
        values.value = -1;
        check(
                FeaturedCardTiming.delay(values) == 8000,
                "Invalid legacy intervals must use the safe existing default.");
        check(
                FeaturedCardTiming.label(0).equals("Manual only"),
                "Manual mode must have honest product wording.");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
