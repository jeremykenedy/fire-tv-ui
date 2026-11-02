package com.jeremykenedy.firetv.ui.nativeapp.layoutschedule;

import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutOptions;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

/** Pure dashboard selection. It never reads or changes provider/widget state. */
public final class LayoutSchedule {
    public static final int MAX_PROFILES = 24;
    public static final int MAX_SLOTS = 48;

    public enum Mode {
        OFF,
        ROTATION,
        DAILY
    }

    public enum Status {
        BASE_LAYOUT,
        READY,
        NEEDS_CONFIGURATION,
        MISSING_PROFILE
    }

    public enum Reason {
        OFF,
        MANUAL,
        ROTATION,
        DAILY
    }

    public static final class Profile {
        public final String id, name;
        public final LayoutOptions layout;

        public Profile(String id, String name, LayoutOptions layout) {
            if (!validId(id) || layout == null) throw invalid("Invalid dashboard identity.");
            this.id = id;
            this.name = name(name);
            this.layout = LayoutOptions.fromBackup(layout.backupValues());
        }
    }

    public static final class Slot {
        public final int minute;
        public final String profile;

        public Slot(int minute, String profile) {
            if (minute < 0 || minute >= 1440 || !validId(profile))
                throw invalid("Choose a daily time and dashboard.");
            this.minute = minute;
            this.profile = profile;
        }

        public String time() {
            return String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
        }
    }

    public static final class State {
        public final List<Profile> profiles;
        public final Mode mode;
        public final List<String> rotation;
        public final int rotationSeconds;
        public final long anchorMillis;
        public final String zone, manual;
        public final List<Slot> slots;

        public State(
                List<Profile> profiles,
                Mode mode,
                List<String> rotation,
                int rotationSeconds,
                long anchorMillis,
                String zone,
                List<Slot> slots,
                String manual) {
            if (profiles == null
                    || profiles.size() > MAX_PROFILES
                    || mode == null
                    || rotation == null
                    || rotation.size() > MAX_PROFILES
                    || rotationSeconds < 1
                    || rotationSeconds > 86400
                    || anchorMillis < 0
                    || zone == null
                    || slots == null
                    || slots.size() > MAX_SLOTS
                    || manual == null
                    || !manual.isEmpty() && !validId(manual))
                throw invalid("Invalid dashboard schedule.");
            Set<String> ids = new HashSet<>(), names = new HashSet<>();
            for (Profile profile : profiles)
                if (profile == null
                        || !ids.add(profile.id)
                        || !names.add(profile.name.toLowerCase(Locale.ROOT)))
                    throw invalid("Dashboard names and identities must be unique.");
            Set<String> ordered = new HashSet<>();
            for (String id : rotation)
                if (!validId(id) || !ordered.add(id))
                    throw invalid("Rotation contains an invalid or duplicate dashboard.");
            if (!zone.isEmpty() && !ZoneId.getAvailableZoneIds().contains(zone))
                throw invalid("Choose an IANA time zone.");
            for (Slot slot : slots) if (slot == null) throw invalid("Invalid daily time.");
            List<Slot> sorted = new ArrayList<>(slots);
            Collections.sort(
                    sorted,
                    new Comparator<Slot>() {
                        @Override
                        public int compare(Slot a, Slot b) {
                            return Integer.compare(a.minute, b.minute);
                        }
                    });
            Set<Integer> times = new HashSet<>();
            for (Slot slot : sorted)
                if (slot == null || !times.add(slot.minute))
                    throw invalid("Choose only one dashboard at each daily time.");
            this.profiles = Collections.unmodifiableList(new ArrayList<>(profiles));
            this.mode = mode;
            this.rotation = Collections.unmodifiableList(new ArrayList<>(rotation));
            this.rotationSeconds = rotationSeconds;
            this.anchorMillis = anchorMillis;
            this.zone = zone;
            this.slots = Collections.unmodifiableList(sorted);
            this.manual = manual;
        }

        public static State empty() {
            return new State(
                    Collections.<Profile>emptyList(),
                    Mode.OFF,
                    Collections.<String>emptyList(),
                    60,
                    0,
                    "",
                    Collections.<Slot>emptyList(),
                    "");
        }

        public Profile profile(String id) {
            for (Profile profile : profiles) if (profile.id.equals(id)) return profile;
            return null;
        }

        public State profiles(List<Profile> next) {
            return new State(
                    next, mode, rotation, rotationSeconds, anchorMillis, zone, slots, manual);
        }

        public State mode(Mode next) {
            return new State(
                    profiles, next, rotation, rotationSeconds, anchorMillis, zone, slots, manual);
        }

        public State manual(String id) {
            return new State(
                    profiles, mode, rotation, rotationSeconds, anchorMillis, zone, slots, id);
        }

        public State rotation(List<String> ids, int seconds, long now) {
            return new State(profiles, mode, ids, seconds, now, zone, slots, manual);
        }

        public State daily(String timezone, List<Slot> next) {
            return new State(
                    profiles,
                    mode,
                    rotation,
                    rotationSeconds,
                    anchorMillis,
                    timezone,
                    next,
                    manual);
        }
    }

    public static final class Decision {
        public final Status status;
        public final Reason reason;
        public final String profileId;
        public final Profile profile;

        /** The caller may check again at this instant. No alarm or power action is implied. */
        public final long nextCheckMillis;

        private Decision(Status status, Reason reason, String id, Profile profile, long next) {
            this.status = status;
            this.reason = reason;
            profileId = id;
            this.profile = profile;
            nextCheckMillis = next;
        }
    }

    private LayoutSchedule() {}

    public static Decision resolve(State state, long nowMillis) {
        if (state == null || nowMillis < 0) throw invalid("Invalid dashboard selection time.");
        if (!state.manual.isEmpty())
            return selected(state, state.manual, Reason.MANUAL, Long.MAX_VALUE);
        if (state.mode == Mode.OFF)
            return new Decision(Status.BASE_LAYOUT, Reason.OFF, "", null, Long.MAX_VALUE);
        if (state.mode == Mode.ROTATION) {
            if (state.rotation.isEmpty())
                return new Decision(
                        Status.NEEDS_CONFIGURATION, Reason.ROTATION, "", null, Long.MAX_VALUE);
            long interval = state.rotationSeconds * 1000L;
            long elapsed = Math.max(0, nowMillis - state.anchorMillis);
            long step = elapsed / interval;
            int index = (int) (step % state.rotation.size());
            long next =
                    nowMillis < state.anchorMillis
                            ? state.anchorMillis + interval
                            : nowMillis + (interval - elapsed % interval);
            if (next < nowMillis) next = Long.MAX_VALUE;
            return selected(state, state.rotation.get(index), Reason.ROTATION, next);
        }
        if (state.zone.isEmpty() || state.slots.isEmpty())
            return new Decision(Status.NEEDS_CONFIGURATION, Reason.DAILY, "", null, Long.MAX_VALUE);
        ZoneId zone = ZoneId.of(state.zone);
        ZonedDateTime now = Instant.ofEpochMilli(nowMillis).atZone(zone);
        // Compare real occurrences, so a repeated local hour does not replay an already-passed
        // switch.
        Slot chosen = null;
        long previous = Long.MIN_VALUE, next = Long.MAX_VALUE;
        for (int day = -1; day <= 1; day++)
            for (Slot slot : state.slots) {
                java.time.LocalDateTime local =
                        now.toLocalDate().plusDays(day).atStartOfDay().plusMinutes(slot.minute);
                java.time.zone.ZoneRules rules = zone.getRules();
                List<java.time.ZoneOffset> offsets = rules.getValidOffsets(local);
                ZonedDateTime occurrence =
                        offsets.isEmpty()
                                ? rules.getTransition(local).getDateTimeAfter().atZone(zone)
                                : ZonedDateTime.ofLocal(local, zone, offsets.get(0));
                long time = occurrence.toInstant().toEpochMilli();
                if (time <= nowMillis
                        && (time > previous
                                || time == previous
                                        && chosen != null
                                        && slot.minute > chosen.minute)) {
                    previous = time;
                    chosen = slot;
                }
                if (time > nowMillis && time < next) next = time;
            }
        return selected(state, chosen.profile, Reason.DAILY, next);
    }

    private static Decision selected(State state, String id, Reason reason, long next) {
        Profile profile = state.profile(id);
        return new Decision(
                profile == null ? Status.MISSING_PROFILE : Status.READY, reason, id, profile, next);
    }

    public static int minute(String time) {
        if (time == null || !time.matches("[0-2][0-9]:[0-5][0-9]"))
            throw invalid("Enter a 24-hour time such as 08:30.");
        int hour = Integer.parseInt(time.substring(0, 2));
        if (hour > 23) throw invalid("Enter a time from 00:00 through 23:59.");
        return hour * 60 + Integer.parseInt(time.substring(3));
    }

    public static String name(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || name.length() > 60)
            throw invalid("Enter a dashboard name of 1 to 60 characters.");
        for (int i = 0; i < name.length(); i++)
            if (Character.isISOControl(name.charAt(i)))
                throw invalid("Dashboard names must use a single line.");
        return name;
    }

    public static boolean validId(String value) {
        return value != null && value.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}");
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
