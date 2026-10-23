package com.jeremykenedy.firetv.ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PlexHomeUsers {
    private PlexHomeUsers() {}

    static List<User> read(Object response) throws IOException {
        JSONObject object = response instanceof JSONObject ? (JSONObject) response : new JSONObject();
        JSONArray users = response instanceof JSONArray ? (JSONArray) response : object.optJSONArray("users");
        if (users == null) users = object.optJSONArray("User");
        JSONObject container = object.optJSONObject("MediaContainer");
        if (users == null && container != null) users = container.optJSONArray("User");
        if (users == null) throw new IOException("Plex Home users could not be read.");
        if (users.length() > 64) throw new IOException("Plex returned too many Home users.");
        Map<String, User> result = new LinkedHashMap<>();
        for (int i = 0; i < users.length(); i++) {
            JSONObject value = users.optJSONObject(i);
            if (value == null) continue;
            String id = value.optString("uuid", "");
            if (id.isEmpty()) id = value.optString("id", "");
            String name = value.optString("title", "").trim();
            if (name.isEmpty()) name = value.optString("friendlyName", "").trim();
            if (name.isEmpty()) name = value.optString("username", "").trim();
            if (!validId(id) || name.isEmpty() || name.length() > 128) continue;
            User user = new User(id, name, flag(value, "protected"), flag(value, "admin"));
            User previous = result.put(id, user);
            if (previous != null && (!previous.name.equals(user.name)
                    || previous.protectedByPin != user.protectedByPin))
                throw new IOException("Plex returned conflicting Home profiles.");
        }
        return new ArrayList<>(result.values());
    }

    static boolean validId(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{1,128}");
    }

    private static boolean flag(JSONObject value, String name) {
        return value.optBoolean(name, false) || "1".equals(value.optString(name, ""));
    }

    static User require(List<User> users, String id) throws IOException {
        for (User user : users) if (user.id.equals(id)) return user;
        throw new IOException("This profile is no longer available in your Plex Home.");
    }

    static final class User {
        final String id;
        final String name;
        final boolean protectedByPin;
        final boolean admin;

        User(String id, String name, boolean protectedByPin, boolean admin) {
            this.id = id;
            this.name = name;
            this.protectedByPin = protectedByPin;
            this.admin = admin;
        }
    }
}
