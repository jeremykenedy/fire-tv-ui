package com.jeremykenedy.firetv.ui.nativeapp.device.updates;

import java.util.*;

public final class UpdateBlockPolicyTest {
    static final String OTA = "com.amazon.device.software.ota", STORE = "com.amazon.venezia";
    public static void main(String[] args) throws Exception {
        Map<String,Object> prefs = new HashMap<>();
        check(!UpdateBlockPolicy.safeChoices(false, prefs));
        check(UpdateBlockPolicy.safeChoices(true, prefs));
        UpdateBlockStatus status = new UpdateBlockStatus(); final int[] calls = {0};
        UpdateBlockStatus.Listener listener = new UpdateBlockStatus.Listener() { public void onStatusChanged() { calls[0]++; } };
        status.add(listener); status.report("Consent required"); check(calls[0] == 1 && status.text().equals("Consent required"));
        status.remove(listener); status.report("Retry"); check(calls[0] == 1 && status.text().equals("Retry"));
        check(!UpdateBlockPolicy.enabled(prefs) && !UpdateBlockPolicy.includesAppstore(prefs));
        prefs.put(UpdateBlockPolicy.APPSTORE, true);
        check(UpdateBlockPolicy.safeChoices(true, prefs) && !UpdateBlockPolicy.enabled(prefs) && UpdateBlockPolicy.includesAppstore(prefs));
        prefs.put(UpdateBlockPolicy.UPDATES, false);
        check(UpdateBlockPolicy.safeChoices(true, prefs) && !UpdateBlockPolicy.enabled(prefs) && UpdateBlockPolicy.includesAppstore(prefs));
        prefs.put(UpdateBlockPolicy.UPDATES, "bad");
        check(!UpdateBlockPolicy.safeChoices(false, prefs));
        reject(() -> UpdateBlockPolicy.safeChoices(true, prefs));
        prefs.put(UpdateBlockPolicy.UPDATES, true); prefs.put(UpdateBlockPolicy.APPSTORE, false);
        check(UpdateBlockPolicy.enabled(prefs) && !UpdateBlockPolicy.includesAppstore(prefs));
        prefs.put(UpdateBlockPolicy.APPSTORE, "yes");
        reject(() -> UpdateBlockPolicy.includesAppstore(prefs));
        reject(() -> UpdateBlockPolicy.safeChoices(true, prefs));
        check(!UpdateBlockPolicy.safeChoices(false, prefs));
        Packages packages = new Packages();
        Builder builder = new Builder();
        reject(() -> UpdateBlockPolicy.build(builder, UpdateBlockPolicy.installed(packages, true, 10000)));
        check(builder.calls == 0 && builder.establishes == 0);
        packages.add(OTA, 10001); packages.add(STORE, 10002);
        UpdateBlockPolicy.Verified normal = UpdateBlockPolicy.installed(packages, false, 10000);
        check(normal.packages.equals(Collections.singletonList(OTA)));
        check("connected".equals(UpdateBlockPolicy.build(builder, normal)) && builder.calls == 1 && builder.establishes == 1);
        UpdateBlockPolicy.Verified both = UpdateBlockPolicy.installed(packages, true, 10000);
        check(both.packages.equals(Arrays.asList(OTA, STORE)));
        Builder broken = new Builder(); broken.failAt = 2;
        reject(() -> UpdateBlockPolicy.build(broken, both));
        check(broken.establishes == 0);
        Builder empty = new Builder(); empty.nullConnection = true;
        reject(() -> UpdateBlockPolicy.build(empty, normal));
        packages.members.put(10001, new String[]{OTA, "com.example.other"});
        reject(() -> UpdateBlockPolicy.installed(packages, true, 10000));
        packages.members.put(10001, null);
        reject(() -> UpdateBlockPolicy.installed(packages, true, 10000));
        packages.ids.put(OTA, 1000999);
        reject(() -> UpdateBlockPolicy.installed(packages, true, 10000));
        packages.ids.put(OTA, 999);
        reject(() -> UpdateBlockPolicy.installed(packages, true, 10000));
        System.out.println("Saved choices, package scope, shared UID safety, empty scope and builder failures passed.");
    }
    interface Task { void run() throws Exception; }
    static void reject(Task action) throws Exception { try { action.run(); throw new AssertionError("Expected rejection"); } catch (IllegalStateException | IllegalArgumentException expected) {} }
    static void check(boolean ok) { if (!ok) throw new AssertionError("Update blocker regression"); }
    static final class Packages implements UpdateBlockPolicy.Packages {
        final Map<String,Integer> ids = new HashMap<>(); final Map<Integer,String[]> members = new HashMap<>();
        void add(String pkg, int uid) { ids.put(pkg, uid); members.put(uid, new String[]{pkg}); }
        public Integer uid(String name) { return ids.get(name); }
        public String[] packagesForUid(int uid) { return members.get(uid); }
    }
    static final class Builder implements UpdateBlockPolicy.Builder<String> {
        int calls, establishes, failAt; boolean nullConnection;
        public void add(String name) { if (++calls == failAt) throw new IllegalStateException("add failed"); }
        public String establish() { establishes++; return nullConnection ? null : "connected"; }
    }
}
