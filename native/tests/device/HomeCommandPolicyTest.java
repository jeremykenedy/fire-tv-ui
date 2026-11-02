package com.jeremykenedy.firetv.ui.nativeapp.device;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HomeCommandPolicyTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void failure(Store store, String operation) {
        try { HomeCommandPolicy.execute(store, operation); throw new AssertionError("Failure expected"); }
        catch (IllegalStateException expected) { }
    }
    public static void main(String[] args) {
        Store store = new Store();
        store.values.put("unrelated", "preserved");
        check("home-preference=unset".equals(HomeCommandPolicy.execute(store, "home-preference-state")), "Absent state exact reply");
        HomeCommandPolicy.execute(store, "home-preference-on");
        check("home-preference=on".equals(HomeCommandPolicy.execute(store, "home-preference-state")), "On state exact reply");
        check(store.values.size() == 2, "Preference-only operations change just the master flag");
        HomeCommandPolicy.execute(store, "home-preference-clear");
        check(!store.values.containsKey(HomeSetupPolicy.LEGACY_HOME), "Clear restores actual absence");
        Map<String, Object> before = new LinkedHashMap<>(store.values);
        store.route = false;
        failure(store, "home-on");
        check(before.equals(store.values), "Route mismatch changes no private flags");
        store.route = true;
        check("ok".equals(HomeCommandPolicy.execute(store, "home-on")), "Full commit exact reply");
        check(Boolean.TRUE.equals(store.values.get(HomeSetupPolicy.HOME_KEYS)), "Full on explicitly selects source keys");
        check(Boolean.TRUE.equals(store.values.get(HomeSetupPolicy.HOME_WINDOWS)), "Full on explicitly selects source fallback");
        check("preserved".equals(store.values.get("unrelated")), "Other settings remain untouched");
        before = new LinkedHashMap<>(store.values);
        store.failNextWrite = true;
        failure(store, "home-preference-off");
        check(before.equals(store.values) && store.recovery == null, "Failed commit rolls back in-memory effects and completes recovery");
        store.routeChangesAfterWrite = true;
        failure(store, "home-on");
        check(before.equals(store.values), "Post-write OS route change rolls every flag back");
        store.routeChangesAfterWrite = false;
        store.route = false;
        HomeCommandPolicy.execute(store, "home-off");
        check(Boolean.FALSE.equals(store.values.get(HomeSetupPolicy.HOME_KEYS)), "Full off clears source key opt-in");
        check("home-preference=off".equals(HomeCommandPolicy.execute(store, "home-preference-state")), "Full off exact state");
        Store restarted = new Store();
        restarted.values.put("unrelated", "still here");
        restarted.values.put(HomeSetupPolicy.LEGACY_HOME, true);
        restarted.recovery = new LinkedHashMap<>();
        restarted.recovery.put(HomeSetupPolicy.LEGACY_HOME, null);
        check("home-preference=unset".equals(HomeCommandPolicy.execute(restarted, "home-preference-state")), "Interrupted transaction restores absent master before new commands");
        check("still here".equals(restarted.values.get("unrelated")), "Recovery is scoped to journaled flags");
        store.values.put(HomeSetupPolicy.HOME_KEYS, "invalid");
        before = new LinkedHashMap<>(store.values);
        failure(store, "home-off");
        check(before.equals(store.values), "Malformed saved flags are preserved for review");
        System.out.println("Home command compatibility transaction checks passed");
    }

    private static final class Store implements HomeCommandPolicy.Store {
        final Map<String, Object> values = new LinkedHashMap<>();
        Map<String, Boolean> recovery;
        boolean route;
        boolean failNextWrite;
        boolean routeChangesAfterWrite;
        @Override public Map<String, ?> preferences() { return new LinkedHashMap<>(values); }
        @Override public boolean write(Map<String, Boolean> changes) {
            for (Map.Entry<String, Boolean> change : changes.entrySet()) {
                if (change.getValue() == null) values.remove(change.getKey()); else values.put(change.getKey(), change.getValue());
            }
            if (failNextWrite) { failNextWrite = false; return false; }
            if (routeChangesAfterWrite) route = !route;
            return true;
        }
        @Override public boolean routeMatches(boolean enabled) { return route == enabled; }
        @Override public Map<String, Boolean> pending() { return recovery; }
        @Override public boolean journal(Map<String, Boolean> before) { recovery = new LinkedHashMap<>(before); return true; }
        @Override public boolean clearJournal() { recovery = null; return true; }
    }
}
