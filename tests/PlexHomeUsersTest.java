package com.jeremykenedy.firetv.ui;

import org.json.JSONObject;

public final class PlexHomeUsersTest {
    public static void main(String[] args) throws Exception {
        java.util.List<PlexHomeUsers.User> users =
                PlexHomeUsers.read(
                        new JSONObject(
                                "{\"users\":[{\"uuid\":\"calvin-uuid\",\"title\":\"Calvin\",\"protected\":false},"
                                    + "{\"id\":17,\"title\":\"Owner\",\"protected\":1,\"admin\":true}]}"));
        check(users.size() == 2, "Both authorized profiles are listed independently.");
        check(
                !PlexHomeUsers.require(users, "calvin-uuid").protectedByPin,
                "An unlocked managed profile does not inherit the owner's PIN requirement.");
        check(
                PlexHomeUsers.require(users, "17").protectedByPin,
                "The owner's PIN protection is preserved.");
        boolean unavailable = false;
        try {
            PlexHomeUsers.require(users, "another-household");
        } catch (java.io.IOException expected) {
            unavailable = true;
        }
        check(unavailable, "A profile outside the authorized roster cannot be selected.");
        check(
                !PlexHomeUsers.validId("../owner/switch?pin=1234"),
                "Profile path injection is rejected.");
        boolean conflict = false;
        try {
            PlexHomeUsers.read(
                    new JSONObject(
                            "{\"users\":[{\"id\":1,\"title\":\"One\"},"
                                    + "{\"id\":1,\"title\":\"Different\",\"protected\":true}]}"));
        } catch (java.io.IOException expected) {
            conflict = true;
        }
        check(conflict, "Conflicting roster identities fail rather than select another user.");
        check(
                PlexHomeUsers.read(
                                new JSONObject(
                                        "{\"MediaContainer\":{\"User\":[{\"id\":2,"
                                                + "\"title\":\"Managed\",\"protected\":\"1\"}]}}"))
                        .get(0)
                        .protectedByPin,
                "The native account response's numeric protection flag stays protected.");
        check(
                PlexHomeUsers.read(new org.json.JSONArray("[{\"id\":3,\"title\":\"Household\"}]"))
                                .size()
                        == 1,
                "Array responses retain the same authorized roster boundary.");
        check(
                PlexHomeUsers.matchesIdentity(
                        users.get(0), new JSONObject("{\"uuid\":\"calvin-uuid\"}")),
                "The selected profile must match the account returned by its switch credential.");
        check(
                !PlexHomeUsers.matchesIdentity(
                        users.get(0), new JSONObject("{\"id\":17,\"title\":\"Calvin\"}")),
                "A matching display name cannot authorize the owner's identity as Calvin.");
        check(
                !PlexHomeUsers.matchesIdentity(users.get(0), new JSONObject()),
                "Missing account identity cannot be accepted.");
        System.out.println("11 Plex Home profile checks passed.");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
