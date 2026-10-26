package com.jeremykenedy.firetv.ui;

public final class RecommendationArtworkRulesTest {
    public static void main(String[] args) {
        check(RecommendationArtworkRules.allowedHttps("com.tubitv.ott", "https://canvas-lb.tubitv.com/art?id=1"));
        check(!RecommendationArtworkRules.allowedHttps("com.netflix.ninja", "https://canvas-lb.tubitv.com/art"));
        check(!RecommendationArtworkRules.allowedHttps("com.tubitv.ott", "http://canvas-lb.tubitv.com/art"));
        check(!RecommendationArtworkRules.allowedHttps("com.tubitv.ott", "https://canvas-lb.tubitv.com.evil.test/art"));
        check(!RecommendationArtworkRules.allowedHttps("com.tubitv.ott", "https://user:password@canvas-lb.tubitv.com/art"));
        check(!RecommendationArtworkRules.allowedHttps("com.tubitv.ott", "https://canvas-lb.tubitv.com:8080/art"));
        check(!RecommendationArtworkRules.allowedHttps("com.tubitv.ott", "file:///private/art"));
        check(!RecommendationArtworkRules.allowedHttps("com.tubitv.ott", null));
        check(RecommendationArtworkRules.ownedContent("com.netflix.ninja", "com.netflix.ninja"));
        check(!RecommendationArtworkRules.ownedContent("com.netflix.ninja", "unrelated.provider"));
        check(!RecommendationArtworkRules.ownedContent(null, null));
        System.out.println("11 recommendation artwork boundary checks passed");
    }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
