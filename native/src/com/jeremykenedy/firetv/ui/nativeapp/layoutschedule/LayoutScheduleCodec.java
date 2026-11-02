package com.jeremykenedy.firetv.ui.nativeapp.layoutschedule;

import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutOptions;

import org.json.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded, versioned layout metadata. No providers, media, household data or credentials. */
public final class LayoutScheduleCodec {
    public static final int MAX_BYTES = 2 * 1024 * 1024;

    private LayoutScheduleCodec() {}

    public static byte[] encode(LayoutSchedule.State state) throws Exception {
        JSONObject object =
                new JSONObject()
                        .put("version", 1)
                        .put("mode", state.mode.name())
                        .put("manual", state.manual)
                        .put("rotationSeconds", state.rotationSeconds)
                        .put("anchorMillis", state.anchorMillis)
                        .put("zone", state.zone);
        JSONArray profiles = new JSONArray(), rotation = new JSONArray(), slots = new JSONArray();
        for (LayoutSchedule.Profile profile : state.profiles)
            profiles.put(
                    new JSONObject()
                            .put("id", profile.id)
                            .put("name", profile.name)
                            .put("layout", new JSONObject(profile.layout.backupValues())));
        for (String id : state.rotation) rotation.put(id);
        for (LayoutSchedule.Slot slot : state.slots)
            slots.put(new JSONObject().put("minute", slot.minute).put("profile", slot.profile));
        object.put("profiles", profiles).put("rotation", rotation).put("slots", slots);
        byte[] bytes = object.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Saved dashboards are too large.");
        return bytes;
    }

    public static LayoutSchedule.State decode(byte[] bytes) throws Exception {
        if (bytes == null || bytes.length > MAX_BYTES)
            throw new IOException("Saved dashboards are too large.");
        JSONObject object = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        exact(
                object,
                "version mode manual rotationSeconds anchorMillis zone profiles rotation slots");
        if (integer(object, "version") != 1)
            throw new IOException(
                    "This dashboard schedule version is not supported. Stored data has been kept.");
        JSONArray profiles = object.getJSONArray("profiles"),
                rotation = object.getJSONArray("rotation"),
                slots = object.getJSONArray("slots");
        if (profiles.length() > LayoutSchedule.MAX_PROFILES
                || rotation.length() > LayoutSchedule.MAX_PROFILES
                || slots.length() > LayoutSchedule.MAX_SLOTS)
            throw new IOException("Too many dashboards or scheduled changes.");
        List<LayoutSchedule.Profile> parsedProfiles = new ArrayList<>();
        List<String> parsedRotation = new ArrayList<>();
        List<LayoutSchedule.Slot> parsedSlots = new ArrayList<>();
        for (int i = 0; i < profiles.length(); i++) {
            JSONObject profile = profiles.getJSONObject(i);
            exact(profile, "id name layout");
            JSONObject layout = profile.getJSONObject("layout");
            exact(layout, "card_width_dp card_height_dp order hidden mode");
            Map<String, Object> values = new LinkedHashMap<>();
            for (Iterator<String> keys = layout.keys(); keys.hasNext(); ) {
                String key = keys.next();
                values.put(key, layout.get(key));
            }
            parsedProfiles.add(
                    new LayoutSchedule.Profile(
                            string(profile, "id"),
                            string(profile, "name"),
                            LayoutOptions.fromBackup(values)));
        }
        for (int i = 0; i < rotation.length(); i++) {
            Object id = rotation.get(i);
            if (!(id instanceof String)) throw new IOException("Invalid rotation dashboard.");
            parsedRotation.add((String) id);
        }
        for (int i = 0; i < slots.length(); i++) {
            JSONObject slot = slots.getJSONObject(i);
            exact(slot, "minute profile");
            parsedSlots.add(
                    new LayoutSchedule.Slot(integer(slot, "minute"), string(slot, "profile")));
        }
        Object anchor = object.get("anchorMillis");
        if (!(anchor instanceof Integer || anchor instanceof Long))
            throw new IOException("Invalid rotation start.");
        return new LayoutSchedule.State(
                parsedProfiles,
                LayoutSchedule.Mode.valueOf(string(object, "mode")),
                parsedRotation,
                integer(object, "rotationSeconds"),
                ((Number) anchor).longValue(),
                string(object, "zone"),
                parsedSlots,
                string(object, "manual"));
    }

    private static String string(JSONObject object, String key) throws Exception {
        Object value = object.get(key);
        if (!(value instanceof String)) throw new IOException("Invalid dashboard text.");
        return (String) value;
    }

    private static int integer(JSONObject object, String key) throws Exception {
        Object value = object.get(key);
        if (!(value instanceof Integer)) throw new IOException("Invalid dashboard number.");
        return (Integer) value;
    }

    private static void exact(JSONObject object, String keys) throws Exception {
        Set<String> expected = new HashSet<>(Arrays.asList(keys.split(" ")));
        if (object.length() != expected.size()) throw new IOException("Invalid dashboard fields.");
        for (Iterator<String> iterator = object.keys(); iterator.hasNext(); )
            if (!expected.contains(iterator.next()))
                throw new IOException("Unsupported dashboard field.");
    }
}
