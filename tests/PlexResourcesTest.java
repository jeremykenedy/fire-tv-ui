package com.jeremykenedy.firetv.ui;

import org.json.JSONArray;
import org.json.JSONObject;

public final class PlexResourcesTest {
    public static void main(String[] args) throws Exception {
        JSONArray resources =
                new JSONArray()
                        .put(resource("other-server", "server", "other-credential"))
                        .put(resource("selected-server", "client", "client-credential"))
                        .put(resource("selected-server", "server,player", "server-credential"));
        check(
                PlexResources.serverToken(resources, "selected-server").equals("server-credential"),
                "Only the exact authorized server resource may supply its PMS credential.");
        JSONObject machineOnly =
                new JSONObject()
                        .put("machineIdentifier", "selected-server")
                        .put("provides", "server")
                        .put("accessToken", "server-credential");
        check(
                PlexResources.serverToken(new JSONArray().put(machineOnly), "selected-server")
                        .equals("server-credential"),
                "The explicit machineIdentifier form must resolve the same exact server.");
        JSONArray devices =
                new JSONArray()
                        .put(
                                resource("other-server", "server", "unrelated-jwt")
                                        .put("token", "unrelated"))
                        .put(
                                resource("selected-server", "server", "resource-jwt")
                                        .put("token", "compatible-pms-token"));
        check(
                PlexResources.deviceToken(devices, "selected-server")
                        .equals("compatible-pms-token"),
                "PMS compatibility must use the exact server device's token, not its JWT or another"
                    + " device.");
        try {
            PlexResources.deviceToken(resources, "selected-server");
            throw new AssertionError(
                    "A missing device token must not reuse its resource credential.");
        } catch (java.io.IOException expected) {
        }
        for (JSONArray missing :
                new JSONArray[] {
                    new JSONArray(),
                    new JSONArray().put(resource("selected-server-suffix", "server", "wrong")),
                    new JSONArray().put(resource("selected-server", "player", "wrong")),
                    new JSONArray().put(resource("selected-server", "server", "")),
                    new JSONArray().put(resource("selected-server", "server", "bad\r\nheader")),
                    new JSONArray()
                            .put(
                                    resource("selected-server", "server", "wrong")
                                            .put("machineIdentifier", "other-server")),
                    new JSONArray()
                            .put(resource("selected-server", "server", "first"))
                            .put(resource("selected-server", "server", "conflicting"))
                }) {
            try {
                PlexResources.serverToken(missing, "selected-server");
                throw new AssertionError(
                        "Missing or ambiguous access must never choose another server.");
            } catch (java.io.IOException expected) {
                check(
                        !expected.getMessage().contains("credential")
                                && !expected.getMessage().contains("wrong"),
                        "Failure messages must not expose server credentials.");
            }
        }
    }

    private static JSONObject resource(String machine, String provides, String token)
            throws Exception {
        return new JSONObject()
                .put("clientIdentifier", machine)
                .put("provides", provides)
                .put("accessToken", token);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
