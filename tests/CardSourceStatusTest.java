package com.jeremykenedy.firetv.ui;

public final class CardSourceStatusTest {
    public static void main(String[] args) {
        String empty = CardSourceStatus.summary(false, 0, 0, false);
        check(empty.contains("enabled") && empty.contains("have not published"), "Empty feed must explain missing published content.");
        String unavailable = CardSourceStatus.summary(false, 0, 0, true);
        check(unavailable.contains("could not be read") && !unavailable.contains("have not published"), "Read failure must never be reported as an empty feed.");
        check(CardSourceStatus.summary(true, 5, 2, false).contains("hidden"), "Visibility must explain why available cards stay hidden.");
        String published = CardSourceStatus.summary(false, 5, 2, false);
        check(published.contains("5 preview") && published.contains("2 System Watch Next"), "Both sources must be counted independently.");
        check(CardSourceStatus.summary(false, 0, 2, false).contains("0 preview"), "Watch Next alone is real available content.");
        check(CardSourceStatus.summary(true, 0, 0, true).contains("could not be read"), "Hidden cards must not conceal a source failure.");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
