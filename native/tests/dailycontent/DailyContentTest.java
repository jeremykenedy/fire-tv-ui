package com.jeremykenedy.firetv.ui.nativeapp.widget.dailycontent;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

public final class DailyContentTest {
    public static void main(String[] args) {
        long midnight = Instant.parse("2026-11-03T00:30:00Z").toEpochMilli();
        check(DailyContent.date(midnight, ZoneId.of("America/Los_Angeles")).equals(LocalDate.of(2026, 11, 2)), "local date rather than UTC");
        check(DailyContent.date(midnight, ZoneId.of("Asia/Tokyo")).equals(LocalDate.of(2026, 11, 3)), "timezone changes date");
        LocalDate leap = LocalDate.of(2028, 2, 29);
        for (DailyContent.Kind kind : DailyContent.Kind.values()) {
            DailyContent first = DailyContent.forDate(kind, leap, 0);
            DailyContent same = DailyContent.forDate(kind, leap, 0);
            check(first.headline.equals(same.headline) && first.text.equals(same.text), "stable within date");
            check(!first.text.isEmpty() && !first.attribution.isEmpty(), "real content and rights");
            check(!DailyContent.forDate(kind, leap.plusDays(1), 0).text.equals(first.text), "next date changes edition");
            Set<String> entries = new HashSet<>();
            for (int i = 0; i < 14; i++) entries.add(DailyContent.forDate(kind, leap.plusDays(i), 0).text);
            check(entries.size() >= 7, "at least seven substantive entries");
            DailyContent.forDate(kind, LocalDate.of(1900, 1, 1), 0);
        }
        DailyContent trivia = DailyContent.forDate(DailyContent.Kind.TRIVIA, leap, 0);
        check(!trivia.answer.isEmpty() && !trivia.text.contains(trivia.answer), "trivia has separate answer");
        check(DailyContent.forDate(DailyContent.Kind.ASTRONOMY, leap, 0).attribution.contains(DailyContent.NASA), "NASA provenance");
        check(DailyContent.forDate(DailyContent.Kind.HOROSCOPE, leap, 11).headline.equals("Pisces"), "chosen sign");
        check(!DailyContent.forDate(DailyContent.Kind.HOROSCOPE, leap, 0).text.equals(DailyContent.forDate(DailyContent.Kind.HOROSCOPE, leap, 1).text), "sign changes entertainment reading");
        check(DailyContent.forDate(DailyContent.Kind.HOROSCOPE, leap, 0).attribution.contains("Entertainment only"), "disclaimer");
        try { DailyContent.forDate(DailyContent.Kind.HOROSCOPE, leap, 12); throw new AssertionError("invalid sign accepted"); }
        catch (IllegalArgumentException expected) { }
        long beforeDst = Instant.parse("2026-11-01T08:30:00Z").toEpochMilli();
        long afterDst = Instant.parse("2026-11-01T09:30:00Z").toEpochMilli();
        check(DailyContent.date(beforeDst, ZoneId.of("America/Los_Angeles")).equals(DailyContent.date(afterDst, ZoneId.of("America/Los_Angeles"))), "DST repeated hour keeps edition");
        System.out.println("Daily content: seven collections, local midnight, leap day, timezone, DST, source and answer checks passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
