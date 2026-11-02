package com.jeremykenedy.firetv.ui.nativeapp.widget.livetv;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LiveGuide {
    public static final int MAX_FAVORITES = 6;
    public enum State { READY, GAP, STALE, MISSING, UNAVAILABLE }
    public static final class Channel {
        public final long id;
        public final String publisher, name, sourceLink;
        private final String identity;
        public Channel(long id, String publisher, String name, String sourceLink) {
            this(id, publisher, name, sourceLink, "", "");
        }
        public Channel(long id, String publisher, String name, String sourceLink, String inputId, String providerId) {
            if (id <= 0 || publisher == null || !publisher.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))
                throw new IllegalArgumentException("Invalid published channel.");
            this.id = id; this.publisher = publisher; this.name = clean(name, 160); this.sourceLink = sourceLink == null ? "" : sourceLink;
            this.identity = providerId == null || providerId.isEmpty() ? Long.toString(id) : stable(inputId, providerId);
        }
        public String key() { return publisher + ":" + identity; }
        private static String stable(String inputId, String providerId) {
            try {
                byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(
                        ((inputId == null ? "" : inputId) + "\u0000" + providerId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder result = new StringBuilder("p");
                for (byte value : hash) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
                return result.toString();
            } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        }
    }
    public static final class Program {
        public final String title;
        public final long start, end;
        public Program(String title, long start, long end) {
            if (start <= 0 || end <= start) throw new IllegalArgumentException("Invalid guide interval.");
            this.title = clean(title, 240); this.start = start; this.end = end;
        }
    }
    public static final class Listing {
        public final Channel channel;
        public final State state;
        public final Program now, next;
        public Listing(Channel channel, State state, Program now, Program next) {
            this.channel = channel; this.state = state; this.now = now; this.next = next;
        }
    }
    public static Listing select(Channel channel, List<Program> programs, long now) {
        Program current = null, next = null;
        long lastEnd = 0;
        for (Program program : programs) {
            if (program.start <= now && program.end > now && (current == null || program.start > current.start)) current = program;
            if (program.end <= now) lastEnd = Math.max(lastEnd, program.end);
        }
        long nextStart = current == null ? now : current.end;
        for (Program program : programs) if (program.start >= nextStart && program.start > now
                && (next == null || program.start < next.start)) next = program;
        State state = current != null ? State.READY : next != null ? State.GAP : lastEnd > 0 ? State.STALE : State.MISSING;
        return new Listing(channel, state, current, next);
    }
    public static String zone(String value) {
        if (value == null || value.isEmpty()) return "";
        if (!ZoneId.getAvailableZoneIds().contains(value)) throw new IllegalArgumentException("Choose a valid IANA time zone.");
        return value;
    }
    public static List<String> favorites(List<String> keys) {
        if (keys.size() > MAX_FAVORITES) throw new IllegalArgumentException("Choose up to six favorite channels.");
        List<String> result = new ArrayList<>();
        for (String key : keys) {
            if (key == null || !key.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+:(?:[1-9][0-9]{0,18}|p[a-f0-9]{64})")
                    || result.contains(key)) throw new IllegalArgumentException("Invalid or duplicate favorite channel.");
            result.add(key);
        }
        return Collections.unmodifiableList(result);
    }
    private static String clean(String value, int max) {
        if (value == null || value.trim().isEmpty()) return "Untitled";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length() && result.length() < max; i++)
            if (!Character.isISOControl(value.charAt(i))) result.append(value.charAt(i));
        String cleaned = result.toString().trim();
        return cleaned.isEmpty() ? "Untitled" : cleaned;
    }
    private LiveGuide() {}
}
