package com.jeremykenedy.firetv.ui;

public final class FeaturedCardRulesTest {
    public static void main(String[] args) {
        String first = FeaturedCardRules.key("source.one", false, "item-7", "");
        check(FeaturedCardRules.validKey(first), "Only opaque hashes may be persisted.");
        check(
                first.equals(FeaturedCardRules.key("source.one", false, "item-7", "changed")),
                "Stable provider identity must survive title, artwork and content metadata"
                        + " updates.");
        check(
                !first.equals(FeaturedCardRules.key("source.two", false, "item-7", "")),
                "Hiding one source's item must not hide another source's item.");
        check(
                !first.equals(FeaturedCardRules.key("source.one", false, "item-8", "")),
                "The next item in the same channel must remain eligible.");
        check(
                !first.equals(FeaturedCardRules.key("source.one", true, "item-7", "")),
                "A separate Watch Next publication must keep its own identity.");
        check(
                FeaturedCardRules.key("source.one", false, "", "") == null,
                "Missing stable IDs must not fall back to title, credentials or playback intent.");
        check(
                FeaturedCardRules.validKey(
                        FeaturedCardRules.key("source.one", false, "", "content-1")),
                "Stable content IDs may identify a publication without an internal provider ID.");
        check(
                !FeaturedCardRules.validKey("arbitrary-intent-with-credentials"),
                "Portable hidden settings must reject raw provider data.");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
