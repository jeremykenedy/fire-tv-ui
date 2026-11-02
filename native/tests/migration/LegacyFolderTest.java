package com.jeremykenedy.firetv.ui.nativeapp.migration;

import java.util.*;

public final class LegacyFolderTest {
    private static final String MAIN = "fixture.alpha/fixture.alpha.Main", ALIAS = "fixture.alpha/fixture.alpha.Alternate";
    public static void main(String[] args) {
        Map<String, List<String>> inventory = Collections.singletonMap("fixture.alpha", Arrays.asList(MAIN));
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("folders", "{\"1700000000000\":{\"name\":\"Fixture\",\"isLocked\":true,\"apps\":[\"fixture.alpha\"]}}");
        source.put("folder_lock_pin", new String(new char[] {'1', '8', '3', '7'}));
        source.put("app_order", "opaque-future-order");
        PreferenceSnapshot snapshot = new PreferenceSnapshot(source);
        LegacyFolderMigration.Plan plan = LegacyFolderMigration.decode(snapshot, inventory);
        check(plan.folders.folders.get(0).id.equals("1700000000000"));
        check(plan.folders.folders.get(0).components.equals(Arrays.asList(MAIN)));
        check(plan.lockedTargets.equals(new LinkedHashSet<>(Arrays.asList("folder:1700000000000", MAIN, "package:fixture.alpha"))));
        char[] pin = LegacyFolderMigration.privatePin(snapshot);
        check(new String(pin).equals(source.get("folder_lock_pin"))); Arrays.fill(pin, '\0');
        check(snapshot.values().equals(source));
        reject(() -> LegacyFolderMigration.decode(snapshot, Collections.<String, List<String>>emptyMap()));
        reject(() -> LegacyFolderMigration.decode(snapshot, Collections.singletonMap("fixture.alpha", Arrays.asList(MAIN, ALIAS))));
        Map<String, Object> bad = new LinkedHashMap<>(source); bad.remove("folder_lock_pin");
        reject(() -> LegacyFolderMigration.decode(new PreferenceSnapshot(bad), inventory));
        bad.put("folder_lock_pin", "malformed");
        reject(() -> LegacyFolderMigration.decode(new PreferenceSnapshot(bad), inventory));
        for (String folders : Arrays.asList(
                "{\"170\":{\"name\":\"Fixture\",\"isLocked\":false,\"apps\":[\"fixture.alpha\",\"fixture.alpha\"]}}",
                "{\"170\":{\"name\":\"Fixture\",\"isLocked\":false,\"apps\":[],\"future\":true}}",
                "{\"opaque\":{\"name\":\"Fixture\",\"isLocked\":false,\"apps\":[]}}",
                "{\"170\":{\"name\":\"Fixture\",\"isLocked\":false,\"apps\":[]},\"170\":{}}")) {
            Map<String, Object> malformed = new LinkedHashMap<>(); malformed.put("folders", folders);
            reject(() -> LegacyFolderMigration.decode(new PreferenceSnapshot(malformed), inventory));
        }
        Map<String, Object> unlocked = new LinkedHashMap<>();
        unlocked.put("folders", "{\"170\":{\"name\":\"Fixture\",\"isLocked\":false,\"apps\":[\"fixture.alpha\"]}}");
        check(LegacyFolderMigration.decode(new PreferenceSnapshot(unlocked), inventory).lockedTargets.isEmpty());
        System.out.println("Observed folder identity, package-wide lock coverage and private PIN validation passed.");
    }
    private static void check(boolean value) { if (!value) throw new AssertionError("Folder migration regression."); }
    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Unsupported folder data accepted.");
    }
}
