package com.jeremykenedy.firetv.ui.nativeapp.providers;

/** Frozen historical digests, independent of the native key builder. */
public final class LegacyIdentityTest {
    public static void main(String[] args) {
        String source = "com.example.player";
        check("a243f9b962e46d519bb3d072d0f8960db0810eaa25c1a00f67c71f9f766d2763",
                LegacyRecommendationIdentities.item(source, false, "title:7", "ignored"));
        check("68c079bdb2b63395fbad8985bfa9b0e118cc57bb0119eb7b3e106af0c1166c91",
                LegacyRecommendationIdentities.item(source, true, "title:7", "ignored"));
        check("c3bd8c6b4639420b344cca721e8172a69a2c82a3d20d19d7c72a4cb3f7a0942f",
                LegacyRecommendationIdentities.item(source, false, "", "movie/7"));
        check("c5305e1d4fa922ba2afef11f0b5ae674bda19629dd8dac15f3a691ffdef1994d",
                LegacyRecommendationIdentities.feed(source, false, "library:3", "ignored"));
        check("2c5b63e7be3f4661ee0d75db2a39a6d4c3d2b3d0cbbbc9f45b8606f81ebb65a0",
                LegacyRecommendationIdentities.feed(source, false, "", "Movies"));
        check("46474bfd9b44c53566c1ab8aa764cbe1e076992297eac5f48d032a37a4647c22",
                LegacyRecommendationIdentities.feed(source, true, "ignored", "ignored"));
        if (LegacyRecommendationIdentities.item(source, false, "", "") != null)
            throw new AssertionError("The stable-ID overload requires an explicit identity.");
        if (LegacyRecommendationIdentities.item(source, false, " title:7 ", "").equals(
                LegacyRecommendationIdentities.item(source, false, "title:7", "")))
            throw new AssertionError("Historical identities must retain exact raw publisher values.");
        check(RecommendationKeys.hash("preview\n" + source + "\nrow:9"),
                LegacyRecommendationIdentities.item(source, false, "", "", 9));
        check(RecommendationKeys.hash("watch-next\n" + source + "\nrow:9"),
                LegacyRecommendationIdentities.item(source, true, "", "", 9));
        check(LegacyRecommendationIdentities.item(source, false, "title:7", "ignored"),
                LegacyRecommendationIdentities.item(source, false, "title:7", "ignored", 999));
        if (LegacyRecommendationIdentities.item(source, false, "", "", 0) != null)
            throw new AssertionError("Missing IDs require a real positive provider row.");
        System.out.println("Frozen legacy TV item/feed identities and unknown identity rejection passed");
    }
    private static void check(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("Historical identity digest changed.");
    }
}
