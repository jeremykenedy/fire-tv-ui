package com.jeremykenedy.firetv.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class FeaturedGroupsTest {
    public static void main(String[] args) {
        List<Object> cards = Arrays.<Object>asList("Movie A", "Movie B", "Show C", "News A", "News B");
        List<String> feeds = Arrays.asList("plex", "plex", "plex-tv", "iptv", "iptv");
        List<String> ids = Arrays.asList("a", "b", "c", "d", "e");
        ArrayList<Object> slots = FeaturedGroups.group(cards, feeds, ids);
        check(slots.equals(Arrays.asList("Movie A", "Show C", "News A")), "One stable slot per logical feed");
        FeaturedGroups.advance();
        check(FeaturedGroups.current(slots.get(0)).equals("Movie B"), "First feed rotates internally");
        check(FeaturedGroups.current(slots.get(1)).equals("Show C"), "Single-item feed stays put");
        check(FeaturedGroups.current(slots.get(2)).equals("News B"), "Other feed rotates independently");
        check(slots.equals(Arrays.asList("Movie A", "Show C", "News A")), "Tick never reorders the strip");
        slots = FeaturedGroups.group(cards, feeds, ids);
        check(FeaturedGroups.current(slots.get(0)).equals("Movie B"), "Refresh retains current identity");
        slots = FeaturedGroups.group(Arrays.<Object>asList("News A", "News B", "Movie A", "Movie B", "Show C"),
                Arrays.asList("iptv", "iptv", "plex", "plex", "plex-tv"), Arrays.asList("d", "e", "a", "b", "c"));
        check(slots.equals(Arrays.asList("Movie A", "Show C", "News A")), "Provider refresh cannot move existing slots");
        FeaturedGroups.move("plex", -1);
        check(FeaturedGroups.current(slots.get(0)).equals("Movie A"), "Manual previous selects only that feed");
        check(FeaturedGroups.current(slots.get(2)).equals("News B"), "Manual action does not alter another feed");
        slots = FeaturedGroups.group(Arrays.<Object>asList("Movie B", "News A", "News B"),
                Arrays.asList("plex", "iptv", "iptv"), Arrays.asList("b", "d", "e"));
        check(FeaturedGroups.current(slots.get(0)).equals("Movie B"), "Removing current item selects surviving item");
        List<Object> many = new ArrayList<>(); List<String> manyFeeds = new ArrayList<>(), manyIds = new ArrayList<>();
        for (int i=0; i<75; i++) { many.add("Title"+i); manyFeeds.add("one-app"); manyIds.add("id"+i); }
        slots = FeaturedGroups.group(many, manyFeeds, manyIds);
        check(slots.size()==1, "Many items cannot flood top-level slots");
        for (int i=0; i<74; i++) FeaturedGroups.advance();
        check(FeaturedGroups.current(slots.get(0)).equals("Title74"), "No old ten-title cap");
        slots = FeaturedGroups.group(new ArrayList<Object>(), new ArrayList<String>(), new ArrayList<String>());
        check(slots.isEmpty(), "Removing last feed clears slots safely");
        FeaturedGroups.advance();
        System.out.println("Stable per-feed card rotation checks passed");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
