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
        for (int custom : new int[] {1, 7, 47, 3600, 86400}) {
            values.value = custom;
            check(FeaturedCardTiming.delay(values) == custom * 1000L,
                    "Custom seconds must reach the live card clock unchanged.");
            check(FeaturedCardTiming.setting(values).intValue() == custom,
                    "The Compose restart key must preserve a custom interval.");
            check(FeaturedCardTiming.label(custom).equals("Every " + custom + " seconds"),
                    "The saved custom value must be visible with its unit.");
        }
        for (int invalid : new int[] {-1, 86401, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            values.value = invalid;
            check(FeaturedCardTiming.delay(values) == 8000,
                    "Invalid stored intervals must retain the existing fallback.");
        }
        check(
                FeaturedCardTiming.label(0).equals("Manual only"),
                "Manual mode must have honest product wording.");
        String a = key('a'), b = key('b');
        values.value = 8;
        check(FeaturedCardTiming.seconds(values, a) == 8, "New feed inherits global");
        FeaturedCardTiming.save(values, a, 5);
        FeaturedCardTiming.save(values, b, 0);
        check(FeaturedCardTiming.seconds(values, a) == 5 && FeaturedCardTiming.seconds(values, b) == 0,
                "Independent intervals and Manual");
        FeaturedCardTiming.save(values, null, 15);
        check(FeaturedCardTiming.seconds(values, a) == 5 && FeaturedCardTiming.seconds(values, b) == 0
                        && FeaturedCardTiming.seconds(values, key('c')) == 15,
                "Global changes preserve overrides and reach other feeds");
        FeaturedCardTiming.save(values, a, null);
        check(FeaturedCardTiming.seconds(values, a) == 15 && FeaturedCardTiming.seconds(values, b) == 0,
                "Use global removes only selected override");
        values.failCommit = true;
        try {
            FeaturedCardTiming.save(values, b, 30);
            throw new AssertionError("Commit failure accepted");
        } catch (IllegalStateException expected) {}
        check(FeaturedCardTiming.seconds(values, b) == 0, "Failed commit preserves previous choice");
        values.failCommit = false;
        java.util.Set<String> before = new java.util.HashSet<>(values.overrides);
        for (Integer invalid : new Integer[]{-1, 86401, Integer.MAX_VALUE}) {
            try {
                FeaturedCardTiming.save(values, a, invalid);
                throw new AssertionError("Invalid custom accepted");
            } catch (IllegalArgumentException expected) {}
            check(before.equals(values.overrides), "Invalid interval cannot change other feeds");
        }
        check(!FeaturedCardTiming.validOverrides(new java.util.HashSet<String>(
                        java.util.Arrays.asList(a + ":1", a + ":2"))),
                "Conflicting feed overrides rejected");
        for (String bad : new String[]{a + ":01", a + ":86401", a + ":-1", a + ":1.5", "name:8", a + ":9999999999"})
            check(!FeaturedCardTiming.validOverrides(java.util.Collections.singleton(bad)),
                    "Reject malformed stored override: " + bad);
        check(FeaturedCardTiming.validOverrides(java.util.Collections.singleton(a + ":86400")),
                "Maximum custom interval remains valid");
        values.overrides.add("bad");
        check(FeaturedCardTiming.seconds(values, a) == 15, "Corrupt overrides fall back to global");
    }

    private static String key(char value) {
        char[] chars = new char[64];
        java.util.Arrays.fill(chars, value);
        return new String(chars);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
