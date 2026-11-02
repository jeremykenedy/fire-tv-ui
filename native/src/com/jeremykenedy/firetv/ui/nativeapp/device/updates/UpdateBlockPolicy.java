package com.jeremykenedy.firetv.ui.nativeapp.device.updates;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** The historical owned launcher package list; no network destinations are inspected. */
public final class UpdateBlockPolicy {
    public static final String UPDATES = "fire_tv_ui_block_updates";
    public static final String APPSTORE = "fire_tv_ui_block_appstore";
    private static final List<String> SYSTEM = Collections.unmodifiableList(Arrays.asList(
            "com.amazon.device.software.ota", "com.amazon.device.software.ota.override",
            "com.amazon.tv.forcedotaupdater", "com.amazon.tv.forcedotaupdater.v2"));
    private static final String STORE = "com.amazon.venezia";
    public interface Packages {
        Integer uid(String name);
        String[] packagesForUid(int uid);
    }
    public interface Builder<T> {
        void add(String packageName) throws Exception;
        T establish() throws Exception;
    }
    public static final class Verified {
        public final List<String> packages;
        private final Map<String, Integer> uids;
        private Verified(List<String> packages, Map<String, Integer> uids) {
            this.packages = Collections.unmodifiableList(packages);
            this.uids = Collections.unmodifiableMap(uids);
        }
        public boolean sameScope(Verified other) { return other != null && uids.equals(other.uids); }
    }
    private UpdateBlockPolicy() {}

    public static boolean safeChoices(boolean ready, Map<String, ?> preferences) {
        if (!ready) return false;
        enabled(preferences); includesAppstore(preferences);
        return true;
    }
    public static boolean enabled(Map<String, ?> preferences) {
        return flag(preferences, UPDATES);
    }
    public static boolean includesAppstore(Map<String, ?> preferences) {
        return flag(preferences, APPSTORE);
    }
    private static boolean flag(Map<String, ?> prefs, String key) {
        Object value = prefs.get(key);
        if (value == null && !prefs.containsKey(key)) return false;
        if (!(value instanceof Boolean)) throw new IllegalArgumentException("Invalid saved update-protection choice.");
        return (Boolean) value;
    }
    public static Verified installed(Packages packages, boolean appstore, int firstApplicationUid) {
        List<String> candidates = new ArrayList<>(SYSTEM);
        if (appstore) candidates.add(STORE);
        Set<String> allowed = new LinkedHashSet<>(candidates);
        List<String> result = new ArrayList<>();
        Map<String, Integer> uids = new LinkedHashMap<>();
        for (String candidate : candidates) {
            Integer uid = packages.uid(candidate);
            if (uid == null) continue;
            if (uid < 0 || uid % 100000 < firstApplicationUid) throw new IllegalStateException("An updater shares a core system UID.");
            String[] shared = packages.packagesForUid(uid);
            if (shared == null || shared.length == 0) throw new IllegalStateException("The updater's shared UID could not be verified.");
            boolean found = false;
            for (String member : shared) {
                if (candidate.equals(member)) found = true;
                if (!allowed.contains(member)) throw new IllegalStateException("An updater shares a UID with another app.");
            }
            if (!found) throw new IllegalStateException("The updater's shared UID could not be verified.");
            result.add(candidate); uids.put(candidate, uid);
        }
        return new Verified(result, uids);
    }
    public static <T> T build(Builder<T> builder, Verified verified) throws Exception {
        if (verified == null || verified.packages.isEmpty()) throw new IllegalStateException("No Amazon update packages are available.");
        for (String pkg : verified.packages) builder.add(pkg);
        T connection = builder.establish();
        if (connection == null) throw new IllegalStateException("The TV did not start update protection.");
        return connection;
    }
}
