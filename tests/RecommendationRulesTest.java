package com.jeremykenedy.firetv.ui;

public final class RecommendationRulesTest {
    public static void main(String[] args) {
        for (String source : RecommendationRules.PACKAGES) {
            check(RecommendationRules.eligible(source, "recommendation", source, "A program"));
            check(!RecommendationRules.eligible(source, "msg", source, "A private message"));
            check(!RecommendationRules.eligible(source, "recommendation", "another.app", "A program"));
            check(!RecommendationRules.eligible(source, "recommendation", source, " "));
        }
        check(!RecommendationRules.eligible("another.app", "recommendation", "another.app", "A program"));
        check(!RecommendationRules.eligible(null, "recommendation", null, "A program"));
        String first = RecommendationRules.identity("com.netflix.ninja", "key", "A program");
        check(RecommendationRules.validIdentity(first));
        check(first.equals(RecommendationRules.identity("com.netflix.ninja", "key", "A program")));
        check(!first.equals(RecommendationRules.identity("com.tubitv.ott", "key", "A program")));
        check(!first.equals(RecommendationRules.identity("com.netflix.ninja", "key", "A different program")));
        check(!RecommendationRules.validIdentity("../credential"));
        System.out.println("Recommendation eligibility and stable card identity checks passed");
    }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
