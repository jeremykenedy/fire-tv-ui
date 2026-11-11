package com.jeremykenedy.firetv.ui.nativeapp.migration;

import java.io.*;
import java.util.*;

public final class MigrationTest {
    public static void main(String[] args) throws Exception {
        originalBackupJsonBounds();
        typedRecovery();
        portableBoundary();
        malformedSnapshots();
        transactionRecovery();
        System.out.println("Typed migration, portable boundaries and recovery regressions passed.");
    }

    private static void originalBackupJsonBounds() throws Exception {
        char[] text = new char[1024 * 1024];
        Arrays.fill(text, 'x');
        String original = "{\"saved\":\"" + new String(text) + "\"}";
        reject(() -> LegacyJson.object(original));
        check(((String) LegacyJson.object(original, 8 * 1024 * 1024).get("saved")).length() == text.length,
                "Original backup parser accepts a larger explicitly bounded document.");
        reject(() -> LegacyJson.object(original, original.length() - 1));
        reject(() -> LegacyJson.object("{}", 8 * 1024 * 1024 + 1));
        reject(() -> LegacyJson.object("{}", 0));
        reject(() -> LegacyJson.object("{\"a\":1,\"a\":2}", 8 * 1024 * 1024));
        String deep = "0";
        for (int i = 0; i < 34; i++) deep = "[" + deep + "]";
        final String nested = "{\"a\":" + deep + "}";
        reject(() -> LegacyJson.object(nested, 8 * 1024 * 1024));
    }

    private static Map<String, Object> fixture() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("apps_per_row", 7);
        values.put("app_scroll_direction", "horizontal");
        values.put("app_max_rows", 0);
        values.put("menu_transition_style", "default");
        values.put("icon_focus_style", "border");
        values.put("icon_focus_background", 0xff123456);
        values.put("white_border", false);
        values.put("custom_video_uri", "content://fixture.invalid/unavailable-video");
        values.put("app_visibility", "{\"fixture.player/.Main\":{\"customIconUri\":\"content://fixture.invalid/missing\"}}");
        values.put("input_names", "{\"hdmi\":\"Fixture input\"}");
        values.put("app_order", "[\"fixture.player/.Main\",\"fixture.other/.Main\"]");
        values.put("featured_feed_order", new LinkedHashSet<>(Arrays.asList("fixture-feed:1", "fixture-feed:2")));
        values.put("hidden_featured_cards", new LinkedHashSet<>(Arrays.asList(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")));
        values.put("factory_icon_packages", new LinkedHashSet<>(Arrays.asList("fixture.player/.Main")));
        values.put("unknown_future_value", 5000000000L);
        values.put("unknown_float", -0.0f);
        values.put("unknown_string", "fixture\ud800value");
        values.put("folder_lock_pin", "fixture-only");
        values.put("fire_tv_ui_equipment_remote", "fixture-identity");
        values.put("fire_tv_ui_home_enabled", false);
        values.put("fire_tv_ui_sleep", 123456);
        values.put("plex_token", "fixture-only-no-account");
        return values;
    }

    private static void typedRecovery() throws Exception {
        Map<String, Object> original = fixture();
        PreferenceSnapshot snapshot = new PreferenceSnapshot(original);
        original.put("apps_per_row", 12);
        ((Set<?>) original.get("factory_icon_packages")).clear();
        check(snapshot.values().get("apps_per_row").equals(7), "Snapshot copied its input.");
        check(((Set<?>) snapshot.values().get("factory_icon_packages")).size() == 1, "Sets copied defensively.");
        try { snapshot.values().clear(); throw new AssertionError("Mutable snapshot."); }
        catch (UnsupportedOperationException expected) {}
        try { ((Set<?>) snapshot.values().get("factory_icon_packages")).clear(); throw new AssertionError("Mutable set."); }
        catch (UnsupportedOperationException expected) {}
        String longInputKey = "hidden:" + String.join("", Collections.nCopies(2042, "i"));
        PreferenceSnapshot longInput = new PreferenceSnapshot(Collections.singletonMap(longInputKey, false));
        check(longInput.values().equals(PreferenceSnapshot.decode(longInput.encode()).values()), "Valid 2049-character input metadata keys round-trip without truncation.");
        Map<String, Object> oversizedKey = Collections.singletonMap(String.join("", Collections.nCopies(PreferenceSnapshot.MAX_KEY_LENGTH + 1, "k")), true);
        reject(() -> new PreferenceSnapshot(oversizedKey));
        PreferenceSnapshot decoded = PreferenceSnapshot.decode(snapshot.encode());
        check(snapshot.values().equals(decoded.values()), "Every supported type round-trips exactly.");
        check(decoded.values().get("unknown_future_value") instanceof Long, "Long remains Long.");
        check(decoded.values().get("unknown_float") instanceof Float, "Float remains Float.");
        PreferenceSnapshot next = LauncherMigration.prepare(decoded);
        Map<String, Object> withoutMarker = new LinkedHashMap<>(next.values());
        withoutMarker.remove(LauncherMigration.VERSION_KEY);
        check(withoutMarker.equals(snapshot.values()), "Only the additive migration marker changes.");
        PreferenceSnapshot empty = LauncherMigration.prepare(new PreferenceSnapshot(Collections.emptyMap()));
        check(empty.values().size() == 1, "Absent preferences are not populated with factory defaults.");
        check(!empty.values().containsKey("white_border"), "Absent and explicit false remain distinct.");
        check(next.values().get("white_border").equals(false), "Explicit false remains present.");
        check(next.values().get("app_max_rows").equals(0), "Explicit Auto remains present.");
        check(next.values().get("custom_video_uri").equals(snapshot.values().get("custom_video_uri")),
                "Unavailable media references retained without filesystem access.");
        for (Object invalid : new Object[]{"1", 2, -1, 1L}) {
            Map<String, Object> bad = fixture();
            bad.put(LauncherMigration.VERSION_KEY, invalid);
            reject(() -> LauncherMigration.prepare(new PreferenceSnapshot(bad)));
        }
    }

    private static void portableBoundary() throws Exception {
        PreferenceSnapshot portable = LauncherMigration.portableValues(new PreferenceSnapshot(fixture()));
        for (String key : new String[]{"plex_token", "folder_lock_pin", "fire_tv_ui_equipment_remote",
                "fire_tv_ui_home_enabled", "fire_tv_ui_sleep", "unknown_future_value", "app_visibility",
                "input_names", "custom_video_uri", "factory_icon_packages"}) {
            check(!portable.values().containsKey(key), "Portable whitelist excludes private and unreviewed keys.");
        }
        check(portable.values().get("apps_per_row").equals(7), "Presentation choice exported.");
        check(portable.values().get("white_border").equals(false), "Explicit default exported.");
        for (String key : new String[]{"apps_per_row", "app_max_rows", "menu_transition_style",
                "app_scroll_direction", "icon_focus_style", "white_border", "custom_video_uri",
                "factory_icon_packages"}) {
            Map<String, Object> bad = fixture();
            bad.put(key, new Object());
            reject(() -> LauncherMigration.prepare(new PreferenceSnapshot(bad)));
        }
        for (Object[] invalid : new Object[][] {
                {"app_max_rows", 7}, {"app_scroll_direction", "diagonal"},
                {"menu_transition_style", "unknown"}, {"icon_focus_style", "unknown"},
                {"white_border", "false"}, {"custom_video_uri", true}, {"factory_icon_packages", "[]"},
                {"featured_rotation_seconds", 86401}, {"icon_focus_border", 12L}}) {
            Map<String, Object> bad = fixture();
            bad.put((String) invalid[0], invalid[1]);
            reject(() -> LauncherMigration.prepare(new PreferenceSnapshot(bad)));
        }
        for (Object badValue : new Object[]{"7", 7L, 7.0f, false, -1, 21}) {
            Map<String, Object> bad = fixture();
            bad.put("apps_per_row", badValue);
            reject(() -> LauncherMigration.prepare(new PreferenceSnapshot(bad)));
        }
    }

    private static void malformedSnapshots() throws Exception {
        byte[] encoded = new PreferenceSnapshot(fixture()).encode();
        for (int cut : new int[]{0, 1, 7, 11, encoded.length - 1}) {
            byte[] truncated = Arrays.copyOf(encoded, cut);
            reject(() -> PreferenceSnapshot.decode(truncated));
        }
        byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);
        reject(() -> PreferenceSnapshot.decode(trailing));
        byte[] version = encoded.clone(); version[7] = 2;
        reject(() -> PreferenceSnapshot.decode(version));
        byte[] excessive = encoded.clone(); excessive[8] = 0x7f;
        reject(() -> PreferenceSnapshot.decode(excessive));
        for (Object bad : new Object[]{null, 1.0d, Float.NaN, Float.POSITIVE_INFINITY,
                Arrays.asList("not-a-set"), Collections.singleton(12)}) {
            Map<String, Object> values = new LinkedHashMap<>(); values.put("unknown", bad);
            reject(() -> new PreferenceSnapshot(values));
        }
        Map<String, Object> tooBig = new LinkedHashMap<>();
        char[] chars = new char[1024 * 1024]; Arrays.fill(chars, 'x');
        for (int index = 0; index < 5; index++) tooBig.put("key" + index, new String(chars));
        reject(() -> new PreferenceSnapshot(tooBig));
        reject(() -> PreferenceSnapshot.decode(new byte[PreferenceSnapshot.MAX_BYTES + 1]));
        // Duplicate map keys and invalid boolean values are rejected before a store is involved.
        ByteArrayOutputStream duplicate = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(duplicate);
        output.writeInt(0x46545549); output.writeInt(1); output.writeInt(2);
        for (int index = 0; index < 2; index++) {
            output.writeInt(1); output.writeChar('x'); output.writeByte(3); output.writeInt(index);
        }
        reject(() -> PreferenceSnapshot.decode(duplicate.toByteArray()));
        byte[] flag = new PreferenceSnapshot(Collections.singletonMap("x", true)).encode();
        flag[flag.length - 1] = 2;
        reject(() -> PreferenceSnapshot.decode(flag));
    }

    private static void transactionRecovery() throws Exception {
        FakeStore success = new FakeStore();
        check(MigrationTransaction.apply(success), "Migration explicitly applied.");
        check(success.recovery.values().equals(fixture()), "Recovery captured before marker.");
        check(success.events.equals(Arrays.asList("recovery", "replace")), "Recovery persists before any preference commit.");
        check(!MigrationTransaction.apply(success), "Repeated migration is a no-op.");
        check(success.events.size() == 2, "No-op does not overwrite recovery.");
        MigrationTransaction.recover(success, success.recovery);
        check(success.read().values().equals(fixture()), "Explicit recovery removes additive keys and restores exact types.");
        for (int mode : new int[]{1, 2, 3}) {
            FakeStore failure = new FakeStore(); failure.failureMode = mode;
            try { MigrationTransaction.apply(failure); throw new AssertionError("Failed commit accepted."); }
            catch (MigrationTransaction.CommitFailure expected) {
                check(expected.rollbackVerified, "Failure rolls back even after memory was mutated.");
            }
            check(failure.read().values().equals(fixture()), "Complete previous snapshot restored.");
            check(failure.recovery.values().equals(fixture()), "Recovery retained after failure.");
        }
        FakeStore unrecoverable = new FakeStore(); unrecoverable.failureMode = 4;
        try { MigrationTransaction.apply(unrecoverable); throw new AssertionError("Failed rollback accepted."); }
        catch (MigrationTransaction.CommitFailure expected) { check(!expected.rollbackVerified, "Rollback failure is explicit."); }
        check(unrecoverable.recovery != null, "Durable recovery retained for corrective path.");
        FakeStore recoveryFailure = new FakeStore(); recoveryFailure.failRecovery = true;
        reject(() -> MigrationTransaction.apply(recoveryFailure));
        check(recoveryFailure.events.isEmpty() && recoveryFailure.read().values().equals(fixture()),
                "Recovery failure prevents preference writes.");
        FakeStore malformed = new FakeStore();
        Map<String, Object> bad = fixture(); bad.put("app_max_rows", 7);
        malformed.current = new PreferenceSnapshot(bad);
        reject(() -> MigrationTransaction.apply(malformed));
        check(malformed.events.isEmpty(), "Validation is complete before snapshot persistence or writes.");
        FakeStore stale = new FakeStore(); stale.changeDuringRecovery = true;
        reject(() -> MigrationTransaction.apply(stale));
        check(stale.events.equals(Arrays.asList("recovery")), "Changed source prevents application without overwriting its new value.");
    }

    private static final class FakeStore implements MigrationTransaction.Store {
        PreferenceSnapshot current = new PreferenceSnapshot(fixture()), recovery;
        final List<String> events = new ArrayList<>();
        int failureMode, replacements;
        boolean failRecovery, changeDuringRecovery;
        @Override public Object lock() { return this; }
        @Override public PreferenceSnapshot read() { return current; }
        @Override public void saveRecovery(PreferenceSnapshot before) throws IOException {
            if (failRecovery) throw new IOException("Fixture recovery failure.");
            recovery = PreferenceSnapshot.decode(before.encode());
            events.add("recovery");
            if (changeDuringRecovery) {
                Map<String, Object> changed = new LinkedHashMap<>(current.values());
                changed.put("apps_per_row", 8); current = new PreferenceSnapshot(changed);
            }
        }
        @Override public boolean replace(PreferenceSnapshot values) throws IOException {
            events.add("replace"); replacements++;
            if (failureMode == 4 && replacements > 1) return false;
            current = values;
            if (replacements == 1) {
                if (failureMode == 1 || failureMode == 4) return false;
                if (failureMode == 2) throw new IOException("Fixture commit failure.");
                if (failureMode == 3) current = new PreferenceSnapshot(Collections.singletonMap("partial", true));
            }
            return true;
        }
    }

    private interface Action { void run() throws Exception; }
    private static void reject(Action action) throws Exception {
        try { action.run(); throw new AssertionError("Malformed or failed operation accepted."); }
        catch (IllegalArgumentException | IOException expected) {}
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
