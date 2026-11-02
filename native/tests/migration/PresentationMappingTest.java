package com.jeremykenedy.firetv.ui.nativeapp.migration;

import com.jeremykenedy.firetv.ui.nativeapp.cards.CardOptions;
import java.io.*;
import java.util.*;

public final class PresentationMappingTest {
    private static final String FEED_A = repeat('a'), FEED_B = repeat('b'), ITEM_A = repeat('c'), ITEM_B = repeat('d'), SERIES = repeat('e');
    private static final String GROUP = "18a3053603b64823919a55842b9d6856816d93565c00fb84a7fbc293279314b2";
    private static final String NATIVE_A = "tv:" + repeat('1'), NATIVE_B = "tv:" + repeat('2');
    private static final String ALPHA = "fixture.alpha/fixture.alpha.Main", BETA = "fixture.beta/fixture.beta.Main";
    private static final String INPUT_ALIAS = "tvinput_name:HDMI 1", INPUT_ID = "fixture.input/.Service/HDMI1000001";
    public static void main(String[] args) throws Exception {
        actualMapping(); contentModeMapping(); iconPackMapping(); appearanceMapping(); absentDefaults(); unsupportedAndConflicts(); strictJson(); multiFileRecovery(); processRestartRecovery();
        System.out.println("Native presentation mappings, unsupported-value rejection and interrupted multi-store recovery passed.");
    }
    private static PresentationMappingContext identities() {
        Map<String, String> components = new LinkedHashMap<>(); components.put("fixture.alpha", ALPHA); components.put("fixture.beta", BETA);
        Map<String, PresentationMappingContext.Feed> feeds = new LinkedHashMap<>();
        feeds.put(FEED_A, new PresentationMappingContext.Feed("fixture.alpha", NATIVE_A));
        feeds.put(FEED_B, new PresentationMappingContext.Feed("fixture.beta", NATIVE_B));
        Map<String, PresentationMappingContext.Item> items = new LinkedHashMap<>();
        items.put(ITEM_A, new PresentationMappingContext.Item("fixture.alpha", "program:one", "One", false));
        items.put(ITEM_B, new PresentationMappingContext.Item("fixture.alpha", "program:two", "Two", false));
        items.put(SERIES, new PresentationMappingContext.Item("fixture.alpha", "series:fixture", "Fixture series", true));
        return new PresentationMappingContext(components, feeds, items,
                Collections.singletonMap("asset:fixture-original.png", "catalog:sonos"), Collections.singletonMap(INPUT_ALIAS, INPUT_ID));
    }
    private static Map<String, Object> legacy() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("apps_per_row", 7); values.put("app_max_rows", 0); values.put("app_scroll_direction", "horizontal");
        values.put("display_order", "fixture.beta,fixture.alpha");
        values.put("app_visibility", "{\"fixture.alpha\":{\"hidden\":false,\"customIconUri\":\"asset:fixture-original.png\"},\"fixture.beta\":{\"hidden\":true}}");
        values.put("factory_icon_packages", set("fixture.beta"));
        values.put("input_names", "{\"tvinput_name:HDMI 1\":\"Console\"}");
        values.put("icon_focus_style", "border"); values.put("icon_focus_border", 0x80123456); values.put("icon_focus_background", 0xff345678);
        values.put("white_border", false); values.put("featured_rotation_seconds", 0);
        values.put("featured_feed_rotation_seconds", set(FEED_A + ":7", FEED_B + ":0"));
        values.put("featured_feed_order", set(GROUP + ":0001:" + FEED_A, GROUP + ":0000:" + FEED_B));
        values.put("featured_card_order", set(FEED_A + ":0001:" + ITEM_B, FEED_A + ":0000:" + ITEM_A));
        values.put("hidden_featured_cards", set(ITEM_A, SERIES));
        values.put("unknown_future", 9000000000L); values.put("private_fixture", "local-only-fixture");
        return values;
    }
    private static Map<String, PreferenceSnapshot> beforeNative() {
        Map<String, PreferenceSnapshot> before = new LinkedHashMap<>();
        before.put(LegacyPresentationMapper.GRID, snapshot(Collections.singletonMap("future_grid", 1.25f)));
        before.put(LegacyPresentationMapper.LAYOUT, snapshot(Collections.singletonMap("mode", "WIDGETS_ONLY")));
        return before;
    }
    private static LegacyPresentationMapper.Plan plan() { return LegacyPresentationMapper.plan(snapshot(legacy()), identities(), beforeNative()); }
    private static void actualMapping() throws Exception {
        LegacyPresentationMapper.Plan plan = plan();
        Map<String, Object> grid = plan.after.get(LegacyPresentationMapper.GRID).values();
        check(grid.get("columns").equals(7) && grid.get("rows").equals(0) && grid.get("horizontal").equals(true), "Typed grid values mapped.");
        check(grid.get("order").equals(BETA + "\n" + ALPHA) && grid.get("hidden").equals(BETA), "Verified component order and hidden identity mapped.");
        check(grid.get("future_grid").equals(1.25f), "Unknown native values retained.");
        Map<String, Object> focus = plan.after.get(LegacyPresentationMapper.FOCUS).values();
        check(focus.get("fill_enabled").equals(false) && focus.get("border_enabled").equals(true), "Border style overrides legacy white_border false as original rules do.");
        check(focus.get("border_rgb").equals(0x123456) && focus.get("fill_rgb").equals(0x345678), "Legacy forced-opaque colors become native RGB without altering color.");
        check(!focus.containsKey("duration_millis") && !focus.containsKey("growth_percent") && !focus.containsKey("border_opacity"), "New controls keep their own defaults, no invented legacy values.");
        Map<String, Object> cardValues = new LinkedHashMap<>(CardOptions.defaults().backupValues());
        cardValues.putAll(plan.after.get(LegacyPresentationMapper.CARDS).values());
        CardOptions cards = CardOptions.fromBackup(cardValues);
        check(cards.globalSeconds == 0 && cards.timerSeconds(NATIVE_A) == 7 && cards.timerSeconds(NATIVE_B) == 0, "Manual and per-feed custom timers survive hashed identity conversion.");
        check(cards.isHidden("fixture.alpha", "program:one") && cards.isSeriesHidden("fixture.alpha", "series:fixture"), "Individual and series hides stay independent.");
        check(cards.restoreSeries("fixture.alpha", "series:fixture").isHidden("fixture.alpha", "program:one"), "Restoring a series keeps an independent hidden episode.");
        check(cards.orderedKeys("fixture.alpha", NATIVE_A, Arrays.asList("program:two", "program:one")).equals(Arrays.asList("program:one", "program:two")), "Per-feed ranked title ordering maps to new card model.");
        Map<String, Object> layout = plan.after.get(LegacyPresentationMapper.LAYOUT).values();
        check(layout.get("order").equals("channel:" + NATIVE_B + "\nchannel:" + NATIVE_A), "Ranked feed order becomes stable shared slots.");
        check(layout.get("mode").equals("WIDGETS_ONLY") && !layout.containsKey("card_width_dp"), "Existing layout mode survives; missing dimensions stay absent.");
        Map<String, Object> icons = plan.after.get(LegacyPresentationMapper.ICONS).values();
        check(icons.get(ALPHA).equals("catalog:sonos") && icons.get(BETA).equals("factory"), "Custom icon and factory reset remain per component.");
        Map<String, Object> inputs = plan.after.get(LegacyPresentationMapper.INPUTS).values();
        check(inputs.get("name:" + INPUT_ID).equals("Console") && inputs.get("known:" + INPUT_ID).equals(true), "Input label uses exact supplied platform ID, not user label.");
        Map<String, Object> retained = new LinkedHashMap<>(plan.after.get(LauncherMigration.PREFERENCES).values());
        retained.remove(LegacyPresentationMapper.VERSION_KEY);
        check(retained.equals(legacy()), "Entire original typed state remains in place for recovery.");
        check(plan.unmappedLegacyKeys.equals(set("unknown_future", "private_fixture")), "Unmapped domains are explicit rather than described as migrated.");
        check(!plan.patches.containsKey(LauncherMigration.PREFERENCES), "Portable/setup patches have no source marker or private values.");
        check(!plan.patches.get(LegacyPresentationMapper.GRID).values().containsKey("future_grid"), "Patches contain mapped choices only.");
        PresentationSnapshot all = new PresentationSnapshot(plan.after);
        check(all.sameValues(PresentationSnapshot.decode(all.encode())), "Complete multi-file private recovery round-trips.");
        byte[] damaged = all.encode(); damaged[damaged.length / 2] ^= 1;
        reject(() -> PresentationSnapshot.decode(damaged));
    }
    private static void contentModeMapping() throws Exception {
        for (Object[] choice : new Object[][]{{false, true, "BOTH"}, {false, false, "CARDS_ONLY"}, {true, true, "WIDGETS_ONLY"}}) {
            Map<String, Object> legacy = new LinkedHashMap<>(); legacy.put("hide_watch_next", choice[0]); legacy.put("enable_widgets", choice[1]);
            LegacyPresentationMapper.Plan plan = LegacyPresentationMapper.plan(snapshot(legacy), identities(), Collections.emptyMap());
            check(plan.after.get(LegacyPresentationMapper.LAYOUT).values().get("mode").equals(choice[2]), "Explicit card and widget visibility maps to stable content mode.");
            check(plan.unmappedLegacyKeys.isEmpty(), "Only both explicit flags establish a complete content-mode mapping.");
        }
        Map<String, Object> bothOff = new LinkedHashMap<>(); bothOff.put("hide_watch_next", true); bothOff.put("enable_widgets", false);
        reject(() -> LegacyPresentationMapper.plan(snapshot(bothOff), identities(), Collections.emptyMap()));
        LegacyPresentationMapper.Plan unknownDefault = LegacyPresentationMapper.plan(snapshot(Collections.singletonMap("hide_watch_next", false)), identities(), Collections.emptyMap());
        check(unknownDefault.patches.isEmpty() && unknownDefault.unmappedLegacyKeys.equals(set("hide_watch_next")), "Absent widget default is not guessed.");
    }
    private static void iconPackMapping() throws Exception {
        PresentationMappingContext standard = identities();
        PresentationMappingContext packs = new PresentationMappingContext(standard.components, standard.feeds, standard.items,
                standard.icons, standard.inputs, set("fixture.icons"));
        LegacyPresentationMapper.Plan selected = LegacyPresentationMapper.plan(snapshot(Collections.singletonMap("icon_pack_package", "fixture.icons")), packs, Collections.emptyMap());
        check(selected.after.get(LegacyPresentationMapper.ICON_PACKS).values().get("package").equals("fixture.icons"), "An explicitly verified installed pack keeps its original package identity.");
        LegacyPresentationMapper.Plan empty = LegacyPresentationMapper.plan(snapshot(Collections.singletonMap("icon_pack_package", "  ")), packs, Collections.emptyMap());
        check(empty.after.get(LegacyPresentationMapper.ICON_PACKS).values().get("package").equals("original"), "Explicit blank legacy pack maps to original, matching maintained whitespace handling.");
        reject(() -> LegacyPresentationMapper.plan(snapshot(Collections.singletonMap("icon_pack_package", "fixture.icons")), standard, Collections.emptyMap()));
        reject(() -> LegacyPresentationMapper.plan(snapshot(Collections.singletonMap("icon_pack_package", "unavailable.pack")), packs, Collections.emptyMap()));
    }
    private static void appearanceMapping() throws Exception {
        Map<String, Object> background = new LinkedHashMap<>();
        background.put("menu_animation", "aurora"); background.put("menu_animation_frozen", true);
        background.put("menu_animation_speed", 0); background.put("menu_animation_intensity", 100);
        background.put("menu_animation_brightness", 75); background.put("menu_overlay", "hearts_floating");
        background.put("menu_overlay_frozen", false); background.put("menu_overlay_speed", 25);
        background.put("menu_overlay_intensity", 50); background.put("menu_overlay_opacity", 0);
        background.put("custom_wallpaper_uri", "file:///fixture-private/unavailable.image");
        background.put("custom_video_uri", "content://fixture.invalid/retained-video"); background.put("mode", "customv");
        background.put("menu_overlay_palette", "valentine");
        LegacyPresentationMapper.Plan plan = LegacyPresentationMapper.plan(snapshot(background), identities(), Collections.emptyMap());
        Map<String, Object> nativeValues = plan.after.get(LegacyPresentationMapper.APPEARANCE).values();
        check(nativeValues.get("base").equals("aurora") && nativeValues.get("base_frozen").equals(true)
                && nativeValues.get("base_speed").equals(0) && nativeValues.get("base_brightness").equals(75), "Background values map without clamping or changing explicit zero.");
        check(nativeValues.get("overlay").equals("hearts_floating") && nativeValues.get("overlay_opacity").equals(0)
                && nativeValues.get("overlay_frozen").equals(false), "Independent overlay values remain independent.");
        check(nativeValues.get("wallpaper_mode").equals("video") && nativeValues.get("wallpaper_video_uri").equals(background.get("custom_video_uri"))
                && nativeValues.get("wallpaper_image_uri").equals(background.get("custom_wallpaper_uri")), "Verified video mode and unavailable dormant references retained exactly without opening media.");
        check(nativeValues.get("overlay_palette").equals("valentine") && plan.unmappedLegacyKeys.isEmpty(), "Reviewed palette identity survives without dropping source value.");
        for (Object[] bad : new Object[][]{{"menu_animation", "not-a-style"}, {"menu_overlay_palette", "not-a-palette"}, {"menu_overlay", "aurora"},
                {"menu_animation_speed", 101}, {"menu_overlay_opacity", -1}, {"menu_animation_frozen", 1},
                {"custom_video_uri", "file:///fixture-private/unsupported-active-video"}}) {
            Map<String, Object> changed = new LinkedHashMap<>(background); changed.put((String) bad[0], bad[1]);
            reject(() -> LegacyPresentationMapper.plan(snapshot(changed), identities(), Collections.emptyMap()));
        }
        Map<String, Object> unsupportedGradient = new LinkedHashMap<>(); unsupportedGradient.put("mode", "custom"); unsupportedGradient.put("custom_gradient", "Fixture gradient");
        LegacyPresentationMapper.Plan gradient = LegacyPresentationMapper.plan(snapshot(unsupportedGradient), identities(), Collections.emptyMap());
        check(gradient.patches.isEmpty() && gradient.unmappedLegacyKeys.equals(unsupportedGradient.keySet()), "Custom gradient mode is not guessed to mean image wallpaper.");
    }
    private static void absentDefaults() {
        LegacyPresentationMapper.Plan absent = LegacyPresentationMapper.plan(snapshot(Collections.emptyMap()), identities(), Collections.emptyMap());
        for (Map.Entry<String, PreferenceSnapshot> entry : absent.after.entrySet()) {
            if (!entry.getKey().equals(LauncherMigration.PREFERENCES)) check(entry.getValue().values().isEmpty(), "Absent settings do not manufacture native defaults.");
        }
        check(absent.patches.isEmpty(), "Absent source emits no presentation patches.");
        LegacyPresentationMapper.Plan shortComponent = LegacyPresentationMapper.plan(snapshot(Collections.singletonMap("display_order", "fixture.alpha/.Main")), identities(), Collections.emptyMap());
        check(shortComponent.after.get(LegacyPresentationMapper.GRID).values().get("order").equals(ALPHA), "Short component names canonicalize to the inventory's full component identity.");
        Map<String, Object> explicit = new LinkedHashMap<>(); explicit.put("app_max_rows", 0); explicit.put("white_border", false); explicit.put("display_order", "");
        LegacyPresentationMapper.Plan defaults = LegacyPresentationMapper.plan(snapshot(explicit), identities(), Collections.emptyMap());
        check(defaults.patches.get(LegacyPresentationMapper.GRID).values().get("rows").equals(0), "Explicit Auto remains present.");
        check(defaults.patches.get(LegacyPresentationMapper.GRID).values().get("order").equals(""), "Explicit empty order remains present.");
        check(defaults.patches.get(LegacyPresentationMapper.FOCUS).values().get("border_enabled").equals(false), "Explicit false remains present.");
        Map<String, Set<String>> owned = LegacyPresentationMapper.ownedNativeKeys(set("apps_per_row", "input_names", "app_visibility"), identities());
        check(owned.get(LegacyPresentationMapper.GRID).equals(set("columns", "hidden")), "Absence/reset ownership is selective.");
        check(owned.get(LegacyPresentationMapper.INPUTS).equals(set("name:" + INPUT_ID)), "Resetting a saved input label preserves factory/known metadata.");
        check(owned.get(LegacyPresentationMapper.ICONS).contains("input:" + INPUT_ID), "Icon resets include exact known input identity.");
    }
    private static void unsupportedAndConflicts() throws Exception {
        for (Object[] invalid : new Object[][]{{"apps_per_row", 1}, {"apps_per_row", 13}, {"apps_per_row", 7L},
                {"app_max_rows", 7}, {"app_scroll_direction", "diagonal"}, {"display_order", "fixture.alpha,fixture.alpha"},
                {"display_order", "fixture.unknown"}, {"display_order", "fixture.alpha,"},
                {"display_order", "fixture.alpha,tvinput_name:HDMI 1"}, {"featured_rotation_seconds", -1},
                {"featured_rotation_seconds", 86401}, {"white_border", "false"}, {"icon_focus_border", 123L},
                {"featured_feed_rotation_seconds", set(FEED_A + ":7", FEED_A + ":8")},
                {"featured_feed_rotation_seconds", set(FEED_A + ":86401")},
                {"hidden_featured_cards", set(repeat('f'))}, {"featured_card_order", set(FEED_A + ":4096:" + ITEM_A)},
                {"featured_card_order", set(FEED_A + ":0000:" + ITEM_A, FEED_A + ":0000:" + ITEM_B)},
                {"featured_card_order", set(FEED_B + ":0000:" + ITEM_A)},
                {"input_names", "{\"tvinput_name:HDMI 1\":\" Console\"}"},
                {"app_visibility", "{\"fixture.alpha\":{\"hidden\":\"true\"}}"},
                {"app_visibility", "{\"fixture.alpha\":{\"hidden\":true},\"fixture.alpha/.Main\":{\"hidden\":false}}"},
                {"app_visibility", "{\"fixture.alpha\":{\"customIconUri\":\"content://fixture.invalid/missing\"}}"}}) {
            Map<String, Object> bad = legacy(); bad.put((String) invalid[0], invalid[1]);
            reject(() -> LegacyPresentationMapper.plan(snapshot(bad), identities(), beforeNative()));
        }
        Map<String, PreferenceSnapshot> conflict = beforeNative();
        conflict.put(LegacyPresentationMapper.GRID, snapshot(Collections.singletonMap("columns", 5)));
        reject(() -> LegacyPresentationMapper.plan(snapshot(legacy()), identities(), conflict));
        Map<String, Object> unknown = new LinkedHashMap<>();
        unknown.put("icon_focus_style", "default"); unknown.put("icon_focus_border", 0xff123456);
        unknown.put("app_order", "opaque-owned-serializer-unavailable"); unknown.put("folders", "opaque-folder-data");
        unknown.put("app_visibility", "{\"fixture.alpha\":{\"hideWatchNext\":true}}");
        LegacyPresentationMapper.Plan partial = LegacyPresentationMapper.plan(snapshot(unknown), identities(), Collections.emptyMap());
        check(partial.patches.isEmpty() && partial.unmappedLegacyKeys.equals(unknown.keySet()), "Unsupported domain semantics remain explicit and raw, never guessed.");
        Map<String, PresentationMappingContext.Feed> duplicateFeeds = new LinkedHashMap<>();
        duplicateFeeds.put(FEED_A, new PresentationMappingContext.Feed("fixture.alpha", NATIVE_A));
        duplicateFeeds.put(FEED_B, new PresentationMappingContext.Feed("fixture.beta", NATIVE_A));
        reject(() -> new PresentationMappingContext(Collections.emptyMap(), duplicateFeeds, Collections.emptyMap(), Collections.emptyMap()));
    }
    private static void strictJson() throws Exception {
        for (String bad : new String[]{"{} trailing", "{\"a\":1,\"a\":2}", "{\"a\":{\"x\":false,\"x\":true}}", "{\"a\":01}",
                "{\"a\":1.}", "{\"a\":1e+}", "{\"a\":NaN}", "{\"a\":1e2147483647}", "{\"a\":\"\\uZZZZ\"}", "{\"a\":true,}", "{\"a\":[1,]}",
                "{\"a\":\"line\nfeed\"}", "{\"a\":TRUE}", "{\"a\":'x'}", "{\"a\":\"\\x\"}"}) reject(() -> LegacyJson.object(bad));
        String nested = "0";
        for (int index = 0; index < 35; index++) nested = "[" + nested + "]";
        final String tooDeep = "{\"a\":" + nested + "}";
        reject(() -> LegacyJson.object(tooDeep));
        Map<String, Object> json = LegacyJson.object("{\"a\":false,\"b\":\"\\u0041\\n\\t\\\\\\\"\",\"c\":[null, -1.2e2]}");
        check(json.get("a").equals(false) && json.get("b").equals("A\n\t\\\""), "Strict parser decodes actual JSON string and boolean values.");
    }
    private static void multiFileRecovery() throws Exception {
        LegacyPresentationMapper.Plan plan = plan();
        FixtureDisk disk = new FixtureDisk(new PresentationSnapshot(plan.before));
        FixtureStore success = new FixtureStore(disk);
        check(PresentationTransaction.apply(success, plan), "Mapped native stores applied.");
        check(new PresentationSnapshot(plan.after).sameValues(success.read()), "Complete mapped readback verified.");
        check(disk.events.get(0).equals("recovery") && disk.events.get(disk.events.size() - 1).equals(LauncherMigration.PREFERENCES), "Private recovery comes first, source marker last.");
        check(!PresentationTransaction.apply(success, new PresentationSnapshot(plan.after), new PresentationSnapshot(plan.after)), "Already-identical operation is a no-op.");
        for (int failure = 1; failure <= 7; failure++) {
            FixtureDisk failedDisk = new FixtureDisk(new PresentationSnapshot(plan.before));
            FixtureStore failed = new FixtureStore(failedDisk); failed.failAt = failure;
            try { PresentationTransaction.apply(failed, plan); throw new AssertionError("Failed multi-store write accepted."); }
            catch (PresentationTransaction.CommitFailure expected) { check(expected.rollbackVerified, "Failure after any mutated file restores all attempted stores."); }
            check(new PresentationSnapshot(plan.before).sameValues(failed.read()), "Unknown and private original values restored across every store.");
            check(failedDisk.journal != null, "Durable recovery survives a failed transaction.");
        }
        FixtureStore corruptReadback = new FixtureStore(new FixtureDisk(new PresentationSnapshot(plan.before)));
        corruptReadback.corruptAfterWrite = true;
        try { PresentationTransaction.apply(corruptReadback, plan); throw new AssertionError("Bad readback accepted."); }
        catch (PresentationTransaction.CommitFailure expected) { check(expected.rollbackVerified, "Readback mismatch restores all stores."); }
        FixtureStore failedRollback = new FixtureStore(new FixtureDisk(new PresentationSnapshot(plan.before)));
        failedRollback.failAt = 2; failedRollback.failRollback = true;
        try { PresentationTransaction.apply(failedRollback, plan); throw new AssertionError("Failed rollback accepted."); }
        catch (PresentationTransaction.CommitFailure expected) { check(!expected.rollbackVerified, "Rollback failure exposed explicitly."); }
        FixtureStore recoveryFailure = new FixtureStore(new FixtureDisk(new PresentationSnapshot(plan.before))); recoveryFailure.failRecovery = true;
        reject(() -> PresentationTransaction.apply(recoveryFailure, plan));
        check(recoveryFailure.disk.events.isEmpty(), "Failed journal persistence prevents any native mutation.");
        FixtureStore stale = new FixtureStore(new FixtureDisk(new PresentationSnapshot(plan.before))); stale.changeDuringRecovery = true;
        reject(() -> PresentationTransaction.apply(stale, plan));
        check(stale.disk.events.equals(Arrays.asList("recovery")), "A change after recovery capture aborts before applying any mapping.");
    }
    private static void processRestartRecovery() throws Exception {
        LegacyPresentationMapper.Plan plan = plan();
        for (int death = 1; death <= 7; death++) {
            FixtureDisk disk = new FixtureDisk(new PresentationSnapshot(plan.before));
            FixtureStore process = new FixtureStore(disk); process.dieAt = death;
            try { PresentationTransaction.apply(process, plan); throw new AssertionError("Fixture process did not stop."); }
            catch (ProcessDeath expected) {}
            FixtureStore restarted = new FixtureStore(disk);
            PresentationSnapshot original = PresentationSnapshot.decode(disk.journal);
            PresentationTransaction.recover(restarted, original);
            check(new PresentationSnapshot(plan.before).sameValues(restarted.read()), "A new process restores exact complete state from the retained journal after any interrupted write.");
        }
    }
    private static final class FixtureDisk {
        final Map<String, PreferenceSnapshot> files;
        final List<String> events = new ArrayList<>();
        byte[] journal;
        FixtureDisk(PresentationSnapshot initial) { files = new LinkedHashMap<>(initial.stores); }
    }
    private static final class FixtureStore implements PresentationTransaction.Store {
        final FixtureDisk disk;
        int writes, failAt, dieAt;
        boolean failRecovery, failRollback, corruptAfterWrite, changeDuringRecovery;
        FixtureStore(FixtureDisk disk) { this.disk = disk; }
        @Override public Object lock() { return disk; }
        @Override public PresentationSnapshot read() { return new PresentationSnapshot(disk.files); }
        @Override public void saveRecovery(PresentationSnapshot snapshot) throws IOException {
            if (failRecovery || disk.journal != null) throw new IOException("Fixture journal failure.");
            disk.journal = snapshot.encode();
            if (!snapshot.sameValues(PresentationSnapshot.decode(disk.journal))) throw new IOException("Fixture journal readback mismatch.");
            disk.events.add("recovery");
            if (changeDuringRecovery) disk.files.put(LegacyPresentationMapper.GRID, snapshot(Collections.singletonMap("changed", true)));
        }
        @Override public boolean replace(String file, PreferenceSnapshot value) {
            writes++; disk.events.add(file);
            if (failRollback && failAt > 0 && writes > failAt) return false;
            disk.files.put(file, value);
            if (writes == dieAt) throw new ProcessDeath();
            if (writes == failAt) return false;
            if (corruptAfterWrite && file.equals(LauncherMigration.PREFERENCES)) {
                corruptAfterWrite = false; disk.files.put(file, snapshot(Collections.singletonMap("partial_fixture", true)));
            }
            return true;
        }
    }
    private static final class ProcessDeath extends Error { private static final long serialVersionUID = 1L; }
    private static PreferenceSnapshot snapshot(Map<String, ?> values) { return new PreferenceSnapshot(values); }
    private static String repeat(char value) { char[] chars = new char[64]; Arrays.fill(chars, value); return new String(chars); }
    private static Set<String> set(String... values) { return new LinkedHashSet<>(Arrays.asList(values)); }
    private interface Action { void run() throws Exception; }
    private static void reject(Action action) throws Exception {
        try { action.run(); throw new AssertionError("Unsupported or failed operation accepted."); }
        catch (IllegalArgumentException | IOException expected) {}
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
