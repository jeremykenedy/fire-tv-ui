package com.jeremykenedy.firetv.ui;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public final class PlexFeedDataTest {
    public static void main(String[] args) throws Exception {
        List<String> requests = new ArrayList<>();
        List<PlexFeedData.Feed> defaults =
                PlexFeedData.read(
                        new PlexFeedData.Reader() {
                            @Override
                            public JSONObject read(String path, int count) throws Exception {
                                requests.add(path);
                                check(
                                        count == 8,
                                        "Default three feeds must divide the 24-card budget"
                                                + " equally.");
                                return new JSONObject("{\"MediaContainer\":{\"Metadata\":[]}}");
                            }
                        },
                        PlexFeedSettings.defaults());
        check(
                defaults.size() == 3
                        && requests.get(1).contains("type=1")
                        && requests.get(2).contains("type=4"),
                "New movies and episodes require separate real media queries.");
        check(
                requests.get(0).equals("/hubs/continueWatching?count=8"),
                "Continue Watching must not pretend to support another account via an undocumented"
                        + " parameter.");
        JSONObject sessions =
                new JSONObject(
                        "{\"MediaContainer\":{\"Metadata\":[{\"ratingKey\":\"1\",\"type\":\"movie\",\"title\":\"Other\",\"User\":{\"id\":\"8\",\"title\":\"Other"
                            + " user\"}},{\"ratingKey\":\"2\",\"type\":\"movie\",\"title\":\"Selected\",\"User\":{\"id\":\"7\",\"title\":\"Sample"
                            + " user\"}},"
                            + "{\"ratingKey\":\"3\",\"type\":\"episode\",\"title\":\"History\",\"accountID\":9},"
                            + "{\"ratingKey\":\"4\",\"type\":\"movie\",\"title\":\"Unidentified\"}]}}");
        PlexFeedSettings selected =
                new PlexFeedSettings(
                        new LinkedHashSet<>(Arrays.asList("playing", "history")),
                        new LinkedHashSet<>(Arrays.asList("7", "9")),
                        4);
        List<PlexLibrary.Item> items = PlexLibrary.items(PlexFeedData.activity(sessions, selected));
        check(
                items.size() == 2
                        && items.get(0).id.equals("2")
                        && items.get(0).title.contains("Sample user"),
                "Multiple-user activity filtering must exclude unrelated users before applying card"
                        + " limits.");
        JSONObject shared =
                new JSONObject(
                        "{\"MediaContainer\":{\"Metadata\":[{\"ratingKey\":\"2\",\"type\":\"movie\",\"title\":\"Same"
                            + " movie\",\"User\":{\"id\":\"7\",\"title\":\"One\"}},{\"ratingKey\":\"2\",\"type\":\"movie\",\"title\":\"Same"
                            + " movie\",\"User\":{\"id\":\"9\",\"title\":\"Two\"}}]}}");
        List<PlexLibrary.Item> sharedItems =
                PlexLibrary.items(PlexFeedData.activity(shared, selected));
        check(
                sharedItems.size() == 1 && sharedItems.get(0).title.contains("One, Two"),
                "Multiple viewers of the same item must not lose the second selected user during"
                    + " deduplication.");
        check(
                PlexFeedData.users(sessions).get("7").equals("Sample user")
                        && PlexFeedData.users(sessions).containsKey("9")
                        && PlexFeedData.users(sessions).size() == 3,
                "User choices must come from actual server IDs and available names, excluding"
                        + " unidentified rows.");
        check(
                PlexFeedData.read(
                                new PlexFeedData.Reader() {
                                    @Override
                                    public JSONObject read(String path, int count) {
                                        throw new AssertionError(
                                                "Disabled feeds must not fetch personal data.");
                                    }
                                },
                                new PlexFeedSettings(
                                        Collections.<String>emptySet(),
                                        Collections.<String>emptySet(),
                                        8))
                        .isEmpty(),
                "Disabling all feeds must make no server calls.");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
