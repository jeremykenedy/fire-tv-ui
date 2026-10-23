package com.jeremykenedy.firetv.ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

final class PlexResources {
    private PlexResources() {}

    static String serverToken(JSONArray resources, String machine) throws IOException {
        return credential(resources, machine, "accessToken");
    }

    static String deviceToken(JSONArray devices, String machine) throws IOException {
        return credential(devices, machine, "token");
    }

    private static String credential(JSONArray resources, String machine, String field)
            throws IOException {
        if (machine == null || machine.isEmpty())
            throw new IOException("The Plex server identity is unavailable.");
        String found = null;
        for (int i = 0; i < resources.length(); i++) {
            JSONObject resource = resources.optJSONObject(i);
            if (resource == null) continue;
            String client = resource.optString("clientIdentifier", "");
            String identity = resource.optString("machineIdentifier", "");
            if (!machine.equals(client) && !machine.equals(identity)) continue;
            if ((!client.isEmpty() && !machine.equals(client))
                    || (!identity.isEmpty() && !machine.equals(identity))) continue;
            boolean server = false;
            for (String capability : resource.optString("provides", "").split(","))
                if ("server".equals(capability.trim())) server = true;
            if (!server) continue;
            String token = resource.optString(field, "");
            if (token.isEmpty()
                    || "null".equals(token)
                    || token.length() > 32768
                    || token.contains("\r")
                    || token.contains("\n")) continue;
            if (found != null && !found.equals(token))
                throw new IOException("Plex returned conflicting access for this server.");
            found = token;
        }
        if (found == null)
            throw new IOException(
                    "This approved Plex account has not shared access to this server.");
        return found;
    }
}
