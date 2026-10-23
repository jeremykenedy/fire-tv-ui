package com.jeremykenedy.firetv.ui;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.LinkedHashSet;

public final class PlexFeedSettingsTest {
    public static void main(String[] args) throws Exception {
        PlexFeedSettings defaults = PlexFeedSettings.defaults();
        check(
                defaults.enabled.contains("movies")
                        && defaults.enabled.contains("episodes")
                        && !defaults.enabled.contains("history"),
                "New movies and episodes are distinct; shared activity is opt-in.");
        PlexFeedSettings all =
                new PlexFeedSettings(
                        new LinkedHashSet<>(Arrays.asList(PlexFeedSettings.KEYS)),
                        new LinkedHashSet<>(Arrays.asList("7", "9")),
                        12);
        check(
                all.limit() * all.enabled.size() <= 24,
                "All feeds must stay inside the existing artwork/card budget.");
        check(
                all.acceptsUser("7")
                        && all.acceptsUser("9")
                        && !all.acceptsUser("8")
                        && !all.acceptsUser(null),
                "One or multiple selected users exclude unrelated or unidentified sessions.");
        PlexFeedSettings copy = PlexFeedSettings.parse(all.json());
        check(
                copy.users.equals(all.users)
                        && copy.enabled.equals(all.enabled)
                        && copy.cards == 12,
                "Server-specific settings survive encrypted connection serialization.");
        for (String bad : Arrays.asList("", "token=secret", "1/2", "-1")) {
            try {
                new PlexFeedSettings(defaults.enabled, new LinkedHashSet<>(Arrays.asList(bad)), 8);
                throw new AssertionError("Invalid user identity accepted.");
            } catch (IllegalArgumentException expected) {
            }
        }
        JSONObject mixed =
                new JSONObject(
                        "{\"MediaContainer\":{\"Metadata\":["
                            + "{\"ratingKey\":\"1\",\"type\":\"episode\",\"title\":\"Episode\"},"
                            + "{\"ratingKey\":\"2\",\"type\":\"movie\",\"title\":\"Movie\"}]}}");
        check(
                PlexLibrary.items(mixed, "movie", 1).get(0).id.equals("2"),
                "Feed type filters run before limits so a mixed page cannot consume the movie"
                    + " slot.");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
