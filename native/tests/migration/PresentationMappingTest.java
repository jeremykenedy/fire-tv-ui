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
        actualMapping(); menuTransitionGate(); savedGridColumns(); plexAliasMapping(); focusOwnedDefaults(); legacyAppFocusMode(); inputOrderAndVisibility(); privateMediaMapping(); contentModeMapping(); iconPackMapping(); appearanceMapping(); absentDefaults(); unsupportedAndConflicts(); strictJson(); multiFileRecovery(); processRestartRecovery(); upgradeLifecycle(); folderPublication(); productionGate(); protectionPreflight();
        System.out.println("Native presentation mappings, unsupported-value rejection and interrupted multi-store recovery passed.");
    }
    private static void menuTransitionGate() throws Exception {
        for (String style : Arrays.asList("default", "none", "fade", "slide")) {
            Map<String, Object> values = Collections.<String, Object>singletonMap("menu_transition_style", style);
            LegacyPresentationMapper.Plan plan = LegacyPresentationMapper.plan(snapshot(values), identities(), Collections.emptyMap());
            PresentationSnapshot before = new PresentationSnapshot(plan.before);
            FixtureDisk disk = new FixtureDisk(before);
            ProductionMigrationGate.commit(new FixtureStore(disk), before, identities(), null);
            check(style.equals(disk.files.get(LauncherMigration.PREFERENCES).values().get("menu_transition_style")),
                    "Supported transition choice survives the production gate unchanged.");
        }
        for (Object style : Arrays.<Object>asList("unknown", "Fade", 1, true)) {
            Map<String, Object> values = Collections.<String, Object>singletonMap("menu_transition_style", style);
            LegacyPresentationMapper.Plan plan = LegacyPresentationMapper.plan(snapshot(values), identities(), Collections.emptyMap());
            PresentationSnapshot before = new PresentationSnapshot(plan.before);
            FixtureDisk disk = new FixtureDisk(before);
            reject(() -> ProductionMigrationGate.commit(new FixtureStore(disk), before, identities(), null));
            check(disk.events.isEmpty() && before.sameValues(new FixtureStore(disk).read()),
                    "Unsupported transition choice blocks without writing.");
        }
    }
    private static void savedGridColumns() {
        for (int columns : new int[] {1, 2, 12, 13, 16, 20}) {
            LegacyPresentationMapper.Plan plan = LegacyPresentationMapper.plan(
                    snapshot(Collections.singletonMap("apps_per_row", columns)), identities(), Collections.emptyMap());
            check(plan.after.get(LegacyPresentationMapper.GRID).values().get("columns").equals(columns),
                    "Established legacy column choices map exactly without clamping.");
            check(plan.after.get(LauncherMigration.PREFERENCES).values().get("apps_per_row").equals(columns)
                    && plan.unmappedLegacyKeys.isEmpty(), "Original typed column choice stays intact and maps completely.");
        }
    }
    private static void plexAliasMapping() throws Exception {
        String sourceKey = repeat('1');
        String plexKey = "plex:" + sourceKey + ":12";
        Map<String, PresentationMappingContext.Item> items = new LinkedHashMap<>();
        items.put(ITEM_A, new PresentationMappingContext.Item("fixture.owner", "program:12", "Episode", false,
                "com.plexapp.android", plexKey));
        PresentationMappingContext context = new PresentationMappingContext(Collections.emptyMap(), Collections.emptyMap(), items, Collections.emptyMap());
        Map<String, Object> original = Collections.singletonMap("hidden_featured_cards", set(ITEM_A));
        LegacyPresentationMapper.Plan plan = LegacyPresentationMapper.plan(snapshot(original), context, Collections.emptyMap());
        Map<String, Object> values = new LinkedHashMap<>(CardOptions.defaults().backupValues());
        values.putAll(plan.after.get(LegacyPresentationMapper.CARDS).values());
        CardOptions cards = CardOptions.fromBackup(values);
        check(cards.isHidden("fixture.owner", "program:12") && cards.isHidden("com.plexapp.android", plexKey),
                "Both representations of the same legacy hidden episode stay hidden.");
        check(!cards.isHidden("com.plexapp.android", "plex:" + sourceKey + ":13"), "An unrelated episode remains visible.");
        check(plan.after.get(LauncherMigration.PREFERENCES).values().get("hidden_featured_cards").equals(set(ITEM_A)),
                "Original legacy records survive mapping.");
        reject(() -> LegacyPresentationMapper.plan(snapshot(original), PresentationMappingContext.empty(), Collections.emptyMap()));
        items.put(ITEM_B, new PresentationMappingContext.Item("fixture.owner", "program:13", "Other", false,
                "com.plexapp.android", plexKey));
        reject(() -> new PresentationMappingContext(Collections.emptyMap(), Collections.emptyMap(), items, Collections.emptyMap()));
    }
    private static void focusOwnedDefaults() {
        for (String style : Arrays.asList("border", "fill")) {
            LegacyPresentationMapper.Plan plan = LegacyPresentationMapper.plan(
                    snapshot(Collections.singletonMap("icon_focus_style", style)), identities(), Collections.emptyMap());
            Map<String, Object> focus = plan.after.get(LegacyPresentationMapper.FOCUS).values();
            check(focus.get("border_rgb").equals(0xffffff) && focus.get("fill_rgb").equals(0x263a5c),
                    "Owned legacy color defaults survive when colors were never explicitly saved.");
            check(focus.get("border_opacity").equals(100) && focus.get("fill_opacity").equals(100),
                    "Owned legacy colors force opaque alpha.");
            check(focus.get("fill_enabled").equals(style.equals("fill")), "Explicit style selects the expected fill behavior.");
        }
    }
    private static void legacyAppFocusMode() throws Exception {
        for (Map<String, Object> source : Arrays.asList(Collections.<String, Object>singletonMap("apps_per_row", 5), Collections.<String, Object>singletonMap("icon_focus_style", "default"))) {
            LegacyPresentationMapper.Plan plan = LegacyPresentationMapper.plan(snapshot(source), identities(), Collections.emptyMap(), null, false, true);
            check(Boolean.TRUE.equals(plan.after.get(LegacyPresentationMapper.FOCUS).values().get("legacy_app_default")), "Observed default chooses the app-only presentation.");
            if (source.containsKey("icon_focus_style")) check(!plan.unmappedLegacyKeys.contains("icon_focus_style"), "Verified explicit Default uses the same app presentation as absent style.");
            Map<String, PreferenceSnapshot> existing = new LinkedHashMap<>();
            existing.put(LegacyPresentationMapper.FOCUS, snapshot(Collections.<String, Object>singletonMap("growth_percent", 0)));
            Map<String, Object> focus = LegacyPresentationMapper.plan(snapshot(source), identities(), existing, null, false, true).after.get(LegacyPresentationMapper.FOCUS).values();
            check(focus.get("growth_percent").equals(0) && !focus.containsKey("legacy_app_default"), "Explicit native focus wins without data loss.");
            if (source.containsKey("icon_focus_style")) check(LegacyPresentationMapper.plan(snapshot(source), identities(), existing, null, false, true).unmappedLegacyKeys.contains("icon_focus_style"), "Default remains unresolved when an explicit native focus choice exists.");
        }
        LegacyPresentationMapper.Plan fragment = LegacyPresentationMapper.plan(snapshot(Collections.<String, Object>singletonMap("use_24h_clock", true)), identities(), Collections.emptyMap());
        check(!fragment.after.get(LegacyPresentationMapper.FOCUS).values().containsKey("legacy_app_default"), "A partial saved setup does not invent a default focus mode.");
        check(LegacyPresentationMapper.ownedNativeKeys(set("icon_focus_style"), identities()).get(LegacyPresentationMapper.FOCUS).contains("legacy_app_default"), "Applying a saved explicit style clears an older legacy mode marker.");
        Map<String, Object> verified = new LinkedHashMap<>(); verified.put("apps_per_row", 5); verified.put("icon_focus_style", "default");
        LegacyPresentationMapper.Plan verifiedPlan = LegacyPresentationMapper.plan(snapshot(verified), identities(), Collections.emptyMap(), null, false, true);
        ProductionMigrationGate.preflight(new PresentationSnapshot(verifiedPlan.before), identities(), null);
        verified.put("unverified_future", true);
        LegacyPresentationMapper.Plan futurePlan = LegacyPresentationMapper.plan(snapshot(verified), identities(), Collections.emptyMap(), null, false, true);
        reject(() -> ProductionMigrationGate.preflight(new PresentationSnapshot(futurePlan.before), identities(), null));
        Map<String, Object> withBorder = new LinkedHashMap<>(); withBorder.put("icon_focus_style", "default"); withBorder.put("white_border", false);
        LegacyPresentationMapper.Plan borderPlan = LegacyPresentationMapper.plan(snapshot(withBorder), identities(), Collections.emptyMap(), null, false, true);
        check(!borderPlan.after.get(LegacyPresentationMapper.FOCUS).values().containsKey("legacy_app_default") && borderPlan.unmappedLegacyKeys.contains("icon_focus_style"), "Unverified border choice remains gated.");
        Map<String, Object> withColor = new LinkedHashMap<>(); withColor.put("icon_focus_style", "default"); withColor.put("icon_focus_background", 0xff123456);
        LegacyPresentationMapper.Plan colorPlan = LegacyPresentationMapper.plan(snapshot(withColor), identities(), Collections.emptyMap(), null, false, true);
        check(colorPlan.unmappedLegacyKeys.contains("icon_focus_style") && colorPlan.unmappedLegacyKeys.contains("icon_focus_background"), "Dormant custom colors remain gated.");
        Map<String, PreferenceSnapshot> malformed = new LinkedHashMap<>(); malformed.put(LegacyPresentationMapper.FOCUS, snapshot(Collections.<String, Object>singletonMap("legacy_app_default", "true")));
        reject(() -> LegacyPresentationMapper.plan(snapshot(Collections.<String, Object>singletonMap("icon_focus_style", "border")), identities(), malformed, null, false, true));
        Map<String, PreferenceSnapshot> conflict = new LinkedHashMap<>(); conflict.put(LegacyPresentationMapper.FOCUS, snapshot(Collections.<String, Object>singletonMap("legacy_app_default", false)));
        reject(() -> LegacyPresentationMapper.plan(snapshot(Collections.<String, Object>singletonMap("icon_focus_style", "default")), identities(), conflict, null, false, true));
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
        check(!focus.containsKey("duration_millis") && !focus.containsKey("growth_percent") && focus.get("border_opacity").equals(100), "Unverified timing and geometry stay untouched; original border remains opaque.");
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
    private static void inputOrderAndVisibility() throws Exception {
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("display_order", "fixture.beta,tvinput_name:HDMI 1,fixture.alpha");
        legacy.put("app_visibility", "{\"tvinput_name:HDMI 1\":{\"hidden\":true},\"fixture.alpha\":{\"hidden\":false}}");
        LegacyPresentationMapper.Plan plan = LegacyPresentationMapper.plan(snapshot(legacy), identities(), Collections.emptyMap());
        check(plan.patches.get(LegacyPresentationMapper.GRID).values().get("order").equals(BETA + "\n" + ALPHA), "Mixed input/app order retains relative app order.");
        Map<String, Object> inputs = plan.patches.get(LegacyPresentationMapper.INPUTS).values();
        check(inputs.get("order").equals(INPUT_ID) && inputs.get("hidden:" + INPUT_ID).equals(true) && inputs.get("known:" + INPUT_ID).equals(true), "Input order and hidden state use the exact platform ID and retain missing identities.");
        check(plan.unmappedLegacyKeys.isEmpty() && plan.patches.get(LegacyPresentationMapper.GRID).values().get("home_order").equals(BETA + "\ninput:" + INPUT_ID + "\n" + ALPHA), "Unified Home ordering preserves exact app/input interleaving.");
        check(plan.after.get(LauncherMigration.PREFERENCES).values().get("display_order").equals(legacy.get("display_order")), "Full mixed original ordering remains unchanged for later unified migration.");
        legacy.put("app_visibility", "{\"tvinput_name:HDMI 1\":{\"hidden\":false}}");
        LegacyPresentationMapper.Plan shown = LegacyPresentationMapper.plan(snapshot(legacy), identities(), Collections.emptyMap());
        check(shown.patches.get(LegacyPresentationMapper.INPUTS).values().get("hidden:" + INPUT_ID).equals(false), "An explicitly shown input remains an explicit false.");
        legacy.put("display_order", "tvinput_name:HDMI 1,tvinput_name:HDMI 1");
        reject(() -> LegacyPresentationMapper.plan(snapshot(legacy), identities(), Collections.emptyMap()));
        LegacyPresentationMapper.Plan allApps = LegacyPresentationMapper.plan(snapshot(Collections.singletonMap("show_all_apps_button", false)), identities(), Collections.emptyMap());
        check(allApps.patches.get(LegacyPresentationMapper.GRID).values().get("show_all_apps_button").equals(false), "Explicit optional All apps setting maps without enabling it by default.");
        reject(() -> LegacyPresentationMapper.plan(snapshot(Collections.singletonMap("show_all_apps_button", "false")), identities(), Collections.emptyMap()));
    }
    private static void privateMediaMapping() throws Exception {
        java.nio.file.Path temp = java.nio.file.Files.createTempDirectory("migration-private-media-");
        try {
            java.nio.file.Path files = java.nio.file.Files.createDirectory(temp.resolve("files"));
            java.nio.file.Path noBackup = java.nio.file.Files.createDirectory(temp.resolve("no-backup"));
            java.nio.file.Path cache = java.nio.file.Files.createDirectory(temp.resolve("cache"));
            java.nio.file.Path privateVideo = java.nio.file.Files.write(files.resolve("fixture video.mp4"), new byte[]{1, 2, 3});
            String uri = privateVideo.toUri().toString();
            PrivateMediaReference approved = PrivateMediaReference.verify(uri, files.toFile(), noBackup.toFile(), cache.toFile());
            PresentationMappingContext standard = identities();
            PresentationMappingContext verified = new PresentationMappingContext(standard.components, standard.feeds, standard.items,
                    standard.icons, standard.inputs, standard.advertisedIconPacks, Arrays.asList(approved));
            Map<String, Object> legacy = new LinkedHashMap<>(); legacy.put("mode", "customv"); legacy.put("custom_video_uri", uri);
            LegacyPresentationMapper.Plan plan = LegacyPresentationMapper.plan(snapshot(legacy), verified, Collections.emptyMap());
            check(plan.after.get(LegacyPresentationMapper.APPEARANCE).values().get("wallpaper_video_uri").equals(uri)
                    && plan.after.get(LegacyPresentationMapper.APPEARANCE).values().get("wallpaper_mode").equals("video"), "Verified app-private video keeps its exact reference and active mode.");
            check(Arrays.equals(java.nio.file.Files.readAllBytes(privateVideo), new byte[]{1, 2, 3}), "Verification and mapping do not copy or alter media.");
            reject(() -> LegacyPresentationMapper.plan(snapshot(legacy), standard, Collections.emptyMap()));
            java.nio.file.Path outside = java.nio.file.Files.write(temp.resolve("outside.mp4"), new byte[]{4});
            reject(() -> PrivateMediaReference.verify(outside.toUri().toString(), files.toFile(), noBackup.toFile(), cache.toFile()));
            reject(() -> PrivateMediaReference.verify(files.toUri().toString(), files.toFile(), noBackup.toFile(), cache.toFile()));
            reject(() -> PrivateMediaReference.verify(uri + "?query=not-a-file", files.toFile(), noBackup.toFile(), cache.toFile()));
            reject(() -> PrivateMediaReference.verify("file://remote.invalid" + privateVideo, files.toFile(), noBackup.toFile(), cache.toFile()));
            java.nio.file.Path escaping = java.nio.file.Files.createSymbolicLink(files.resolve("outside-link.mp4"), outside);
            reject(() -> PrivateMediaReference.verify(escaping.toUri().toString(), files.toFile(), noBackup.toFile(), cache.toFile()));
            java.nio.file.Files.delete(privateVideo);
            reject(() -> LegacyPresentationMapper.plan(snapshot(legacy), verified, Collections.emptyMap()));
            check(legacy.get("custom_video_uri").equals(uri), "A disappeared media file leaves the original typed reference untouched.");
        } finally {
            try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.walk(temp)) {
                for (java.nio.file.Path path : (Iterable<java.nio.file.Path>) paths.sorted(Comparator.reverseOrder())::iterator) java.nio.file.Files.deleteIfExists(path);
            }
        }
    }
    private static void contentModeMapping() throws Exception {
        for (Object[] choice : new Object[][]{{false, false, "CARDS_ONLY"}, {true, false, "NONE"}}) {
            Map<String, Object> legacy = new LinkedHashMap<>(); legacy.put("hide_watch_next", choice[0]); legacy.put("enable_widgets", choice[1]);
            LegacyPresentationMapper.Plan plan = LegacyPresentationMapper.plan(snapshot(legacy), identities(), Collections.emptyMap());
            check(plan.after.get(LegacyPresentationMapper.LAYOUT).values().get("mode").equals(choice[2]), "Explicit card and widget visibility maps to stable content mode.");
            check(plan.unmappedLegacyKeys.isEmpty(), "Only both explicit flags establish a complete content-mode mapping.");
        }
        Map<String, Object> hiddenWithWidgets = new LinkedHashMap<>(); hiddenWithWidgets.put("hide_watch_next", true); hiddenWithWidgets.put("enable_widgets", true);
        LegacyPresentationMapper.Plan unresolved = LegacyPresentationMapper.plan(snapshot(hiddenWithWidgets), identities(), Collections.emptyMap());
        check(unresolved.unmappedLegacyKeys.equals(set("hide_watch_next", "enable_widgets")) && unresolved.patches.isEmpty(), "The observed hidden row remains unresolved until its behavior with cards is verified.");
        check(unresolved.after.get(LauncherMigration.PREFERENCES).values().get("hide_watch_next").equals(true)
                && unresolved.after.get(LauncherMigration.PREFERENCES).values().get("enable_widgets").equals(true), "Original content choices remain intact.");
        hiddenWithWidgets.put("hide_watch_next", false);
        LegacyPresentationMapper.Plan coexistence = LegacyPresentationMapper.plan(snapshot(hiddenWithWidgets), identities(), Collections.emptyMap());
        check(coexistence.unmappedLegacyKeys.equals(set("hide_watch_next", "enable_widgets")) && coexistence.patches.isEmpty(), "Cards and widgets together require visible-feed proof.");
        for (LegacyPresentationMapper.Plan held : Arrays.asList(unresolved, coexistence)) {
            PresentationSnapshot original = new PresentationSnapshot(held.before);
            FixtureDisk disk = new FixtureDisk(original);
            reject(() -> ProductionMigrationGate.commit(new FixtureStore(disk), original, identities(), null));
            check(disk.events.isEmpty() && original.sameValues(new FixtureStore(disk).read()), "Unverified content mode blocks before writing any store.");
        }
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
        for (String[] builtin : new String[][]{{"builtin:clean_line", "clean-line"}, {"builtin:muted_solid", "muted-solid"}}) {
            LegacyPresentationMapper.Plan mapped = LegacyPresentationMapper.plan(snapshot(Collections.singletonMap("icon_pack_package", builtin[0])), standard, Collections.emptyMap());
            check(mapped.after.get(LegacyPresentationMapper.ICON_PACKS).values().get("package").equals(builtin[1]), "Maintained built-in pack identifiers preserve the named native style choice.");
        }
        reject(() -> LegacyPresentationMapper.plan(snapshot(Collections.singletonMap("icon_pack_package", "builtin:holiday:christmas")), standard, Collections.emptyMap()));
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
        for (Object[] bad : new Object[][]{{"menu_animation", "not-a-style"}, {"menu_overlay_palette", "not-a-palette"}, {"menu_overlay", "not-an-overlay"},
                {"menu_animation_speed", 101}, {"menu_overlay_opacity", -1}, {"menu_animation_frozen", 1},
                {"custom_video_uri", "file:///fixture-private/unsupported-active-video"}}) {
            Map<String, Object> changed = new LinkedHashMap<>(background); changed.put((String) bad[0], bad[1]);
            reject(() -> LegacyPresentationMapper.plan(snapshot(changed), identities(), Collections.emptyMap()));
        }
        Map<String, Object> unsupportedGradient = new LinkedHashMap<>(); unsupportedGradient.put("mode", "custom"); unsupportedGradient.put("custom_gradient", "Fixture gradient");
        reject(() -> LegacyPresentationMapper.plan(snapshot(unsupportedGradient), identities(), Collections.emptyMap()));
        for (String[] choice : new String[][]{{"Electric Indigo", "electric_indigo"}, {"Cloud Nine", "cloud_nine"}}) {
            Map<String, Object> custom = new LinkedHashMap<>();
            custom.put("mode", "custom"); custom.put("custom_gradient", choice[0]);
            custom.put("menu_animation", "off");
            LegacyPresentationMapper.Plan selected = LegacyPresentationMapper.plan(snapshot(custom), identities(), Collections.emptyMap());
            Map<String, Object> appearance = selected.after.get(LegacyPresentationMapper.APPEARANCE).values();
            check(appearance.get("base").equals("still_legacy_gradient") && appearance.get("scene").equals("")
                    && appearance.get("legacy_gradient").equals(choice[1]) && selected.unmappedLegacyKeys.isEmpty(), "Known active gradient maps exactly and stays separate from wallpaper.");
            custom.remove("mode"); custom.remove("menu_animation");
            LegacyPresentationMapper.Plan dormant = LegacyPresentationMapper.plan(snapshot(custom), identities(), Collections.emptyMap());
            check(dormant.after.get(LegacyPresentationMapper.APPEARANCE).values().get("legacy_gradient").equals(choice[1])
                    && !dormant.after.get(LegacyPresentationMapper.APPEARANCE).values().containsKey("base"), "Dormant gradient is retained without selecting a mode.");
        }
        Map<String, Object> missing = new LinkedHashMap<>(); missing.put("mode", "custom");
        reject(() -> LegacyPresentationMapper.plan(snapshot(missing), identities(), Collections.emptyMap()));
        Map<String, Object> conflict = new LinkedHashMap<>(); conflict.put("mode", "custom"); conflict.put("custom_gradient", "Electric Indigo"); conflict.put("menu_animation", "aurora");
        reject(() -> LegacyPresentationMapper.plan(snapshot(conflict), identities(), Collections.emptyMap()));
        conflict.remove("menu_animation");
        Map<String, PreferenceSnapshot> target = new LinkedHashMap<>(); target.put(LegacyPresentationMapper.APPEARANCE, snapshot(Collections.singletonMap("scene", "christmas")));
        reject(() -> LegacyPresentationMapper.plan(snapshot(conflict), identities(), target));
        target.put(LegacyPresentationMapper.APPEARANCE, snapshot(Collections.singletonMap("scene", "valentine")));
        reject(() -> LegacyPresentationMapper.plan(snapshot(conflict), identities(), target));
        LegacyPresentationMapper.Plan preservedScene = LegacyPresentationMapper.plan(snapshot(Collections.singletonMap("custom_gradient", "Cloud Nine")), identities(), target);
        check(preservedScene.after.get(LegacyPresentationMapper.APPEARANCE).values().get("scene").equals("valentine"), "A dormant gradient retains an existing native scene.");
        target.put(LegacyPresentationMapper.APPEARANCE, snapshot(Collections.singletonMap("scene", "unknown_scene")));
        reject(() -> LegacyPresentationMapper.plan(snapshot(Collections.singletonMap("custom_gradient", "Cloud Nine")), identities(), target));
        Map<String, Object> otherMode = new LinkedHashMap<>(); otherMode.put("mode", "dark"); otherMode.put("custom_gradient", "Cloud Nine");
        LegacyPresentationMapper.Plan heldMode = LegacyPresentationMapper.plan(snapshot(otherMode), identities(), Collections.emptyMap());
        check(heldMode.unmappedLegacyKeys.contains("mode") && heldMode.after.get(LegacyPresentationMapper.APPEARANCE).values().get("legacy_gradient").equals("cloud_nine"), "Unknown mode remains unresolved while its dormant gradient is retained.");
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
        check(owned.get(LegacyPresentationMapper.INPUTS).equals(set("name:" + INPUT_ID, "hidden:" + INPUT_ID)), "Reviewed name/visibility ownership excludes factory/known metadata.");
        check(LegacyPresentationMapper.ownedNativeKeys(set("input_names"), identities()).get(LegacyPresentationMapper.INPUTS).equals(set("name:" + INPUT_ID)), "Resetting a saved input label preserves factory/known and visibility metadata.");
        check(owned.get(LegacyPresentationMapper.ICONS).contains("input:" + INPUT_ID), "Icon resets include exact known input identity.");
    }
    private static void unsupportedAndConflicts() throws Exception {
        for (Object[] invalid : new Object[][]{{"apps_per_row", 0}, {"apps_per_row", 21}, {"apps_per_row", 7L},
                {"app_max_rows", 7}, {"app_scroll_direction", "diagonal"}, {"display_order", "fixture.alpha,fixture.alpha"},
                {"display_order", "fixture.unknown"}, {"display_order", "fixture.alpha,"},
                {"display_order", "fixture.alpha,tvinput_name:Unknown input"}, {"featured_rotation_seconds", -1},
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
    private static void upgradeLifecycle() throws Exception {
        LegacyPresentationMapper.Plan plan = plan();
        FixtureDisk disk = new FixtureDisk(new PresentationSnapshot(plan.before));
        FixtureStore first = new FixtureStore(disk);
        PresentationUpgrade.Result result = PresentationUpgrade.apply(first, identities());
        check(result.migrated && !result.recovered && result.unmappedLegacyKeys.contains("unknown_future"), "Upgrade reports actual mapping and preserved unknown domains.");
        check(disk.journal == null, "Successful upgrade resolves private journal before ordinary writers run.");
        Map<String, Object> changed = new LinkedHashMap<>(disk.files.get(LegacyPresentationMapper.GRID).values());
        changed.put("columns", 8); disk.files.put(LegacyPresentationMapper.GRID, snapshot(changed));
        check(!PresentationUpgrade.apply(new FixtureStore(disk), identities()).migrated, "Next startup does not reapply old legacy choices.");
        check(disk.files.get(LegacyPresentationMapper.GRID).values().get("columns").equals(8), "Choices made after migration survive future starts.");
        for (int death = 1; death <= 7; death++) {
            FixtureDisk interrupted = new FixtureDisk(new PresentationSnapshot(plan.before));
            FixtureStore process = new FixtureStore(interrupted); process.dieAt = death;
            try { PresentationUpgrade.apply(process, identities()); throw new AssertionError("Process did not interrupt."); }
            catch (ProcessDeath expected) {}
            PresentationUpgrade.Result resumed = PresentationUpgrade.apply(new FixtureStore(interrupted), identities());
            check(resumed.recovered && resumed.migrated && interrupted.journal == null, "Interrupted upgrade restores original typed state then retries and resolves.");
            check(new PresentationSnapshot(plan.after).sameValues(new FixtureStore(interrupted).read()), "Restart reaches verified complete native values.");
        }
        FixtureDisk unresolved = new FixtureDisk(new PresentationSnapshot(plan.before));
        FixtureStore fails = new FixtureStore(unresolved); fails.failResolution = true;
        reject(() -> PresentationUpgrade.apply(fails, identities()));
        check(unresolved.journal != null, "Failed journal removal keeps recovery and blocks writer startup.");
        check(PresentationUpgrade.apply(new FixtureStore(unresolved), identities()).recovered, "Restart handles death after source marker but before journal resolution.");
        Map<String, PreferenceSnapshot> freshFiles = new LinkedHashMap<>();
        for (String file : LegacyPresentationMapper.FILES) freshFiles.put(file, snapshot(Collections.emptyMap()));
        FixtureDisk fresh = new FixtureDisk(new PresentationSnapshot(freshFiles));
        check(!PresentationUpgrade.apply(new FixtureStore(fresh), identities()).migrated
                && fresh.files.get(LauncherMigration.PREFERENCES).values().isEmpty() && fresh.journal == null,
                "Fresh install preserves exact legacy absence without creating marker or defaults.");
        for (Object unsupportedVersion : Arrays.<Object>asList(2, -1, "1", 1L)) {
            Map<String, PreferenceSnapshot> unsupportedFiles = new LinkedHashMap<>(freshFiles);
            unsupportedFiles.put(LauncherMigration.PREFERENCES, snapshot(Collections.singletonMap(LegacyPresentationMapper.VERSION_KEY, unsupportedVersion)));
            FixtureDisk unsupportedDisk = new FixtureDisk(new PresentationSnapshot(unsupportedFiles));
            reject(() -> PresentationUpgrade.apply(new FixtureStore(unsupportedDisk), identities()));
            check(unsupportedDisk.journal == null && unsupportedDisk.events.isEmpty(), "Invalid version is rejected before journaling or writing.");
        }
        FixtureDisk corrupt = new FixtureDisk(new PresentationSnapshot(plan.before)); corrupt.journal = new byte[]{1};
        reject(() -> PresentationUpgrade.apply(new FixtureStore(corrupt), identities()));
        check(new PresentationSnapshot(plan.before).sameValues(new FixtureStore(corrupt).read()), "Corrupt recovery prevents writes.");
    }
    private static void folderPublication() throws Exception {
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("folders", "{\"123\":{\"name\":\"Kids \\\"TV\\\"\",\"isLocked\":false,\"apps\":[\"fixture.beta\",\"fixture.alpha\"]}}");
        legacy.put("display_order", "fixture.alpha,folder://123,fixture.beta");
        PreferenceSnapshot source = snapshot(legacy);
        Map<String, List<String>> packages = new LinkedHashMap<>();
        packages.put("fixture.alpha", Arrays.asList(ALPHA)); packages.put("fixture.beta", Arrays.asList(BETA));
        LegacyFolderMigration.Plan folders = LegacyFolderMigration.decode(source, packages);
        LegacyPresentationMapper.Plan plan = LegacyPresentationMapper.plan(source, identities(), Collections.emptyMap(), folders, false);
        Map<String, Object> values = plan.after.get(LegacyPresentationMapper.FOLDERS).values();
        check(values.get("folders").equals("[{\"id\":\"123\",\"name\":\"Kids \\\"TV\\\"\",\"components\":[\"" + BETA + "\",\"" + ALPHA + "\"]}]"),
                "Folder id, escaped name and member order are preserved in the native store.");
        check(plan.after.get(LegacyPresentationMapper.GRID).values().get("home_order").equals(ALPHA + "\nfolder:123\n" + BETA),
                "Folder position remains interleaved with Home app order.");
        Map<String, PreferenceSnapshot> conflict = new LinkedHashMap<>();
        conflict.put(LegacyPresentationMapper.FOLDERS, snapshot(Collections.singletonMap("folders", "[]")));
        reject(() -> LegacyPresentationMapper.plan(source, identities(), conflict, folders, false));
        for (int death = 1; death <= 3; death++) {
            FixtureDisk disk = new FixtureDisk(new PresentationSnapshot(plan.before));
            FixtureStore interrupted = new FixtureStore(disk); interrupted.dieAt = death;
            try { PresentationUpgrade.apply(interrupted, identities(), folders, false); throw new AssertionError("Process did not interrupt."); }
            catch (ProcessDeath expected) {}
            PresentationSnapshot original = PresentationSnapshot.decode(disk.journal);
            PresentationTransaction.recover(new FixtureStore(disk), original);
            check(original.sameValues(new FixtureStore(disk).read()), "Interrupted folder publication restores every store.");
            new FixtureStore(disk).resolveRecovery(original, original);
            check(PresentationUpgrade.apply(new FixtureStore(disk), identities(), folders, false).migrated,
                    "Recovered folder publication retries safely.");
            check(new PresentationSnapshot(plan.after).sameValues(new FixtureStore(disk).read()), "Retry publishes verified folder and Home order together.");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(0x46545053); output.writeInt(1); output.writeInt(LegacyPresentationMapper.FILES.size() - 1);
        for (String file : LegacyPresentationMapper.FILES) if (!file.equals(LegacyPresentationMapper.FOLDERS)) {
            byte[] encoded = plan.before.get(file).encode();
            output.writeUTF(file); output.writeInt(encoded.length); output.write(encoded);
        }
        output.flush(); output.write(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        PresentationSnapshot oldJournal = PresentationSnapshot.decode(bytes.toByteArray());
        Map<String, PreferenceSnapshot> current = new LinkedHashMap<>(plan.after);
        current.put(LegacyPresentationMapper.FOLDERS, snapshot(Collections.singletonMap("folders", "[owner data]")));
        FixtureDisk oldDisk = new FixtureDisk(new PresentationSnapshot(current));
        PresentationTransaction.recover(new FixtureStore(oldDisk), oldJournal);
        check(oldJournal.matches(new FixtureStore(oldDisk).read())
                && oldDisk.files.get(LegacyPresentationMapper.FOLDERS).values().get("folders").equals("[owner data]"),
                "Older recovery journals restore only the stores they captured and leave later folders intact.");
    }
    private static void productionGate() throws Exception {
        Map<String, PreferenceSnapshot> emptyFiles = new LinkedHashMap<>();
        for (String file : LegacyPresentationMapper.FILES) emptyFiles.put(file, snapshot(Collections.emptyMap()));
        FixtureDisk fresh = new FixtureDisk(new PresentationSnapshot(emptyFiles));
        ProductionMigrationGate.markFresh(new FixtureStore(fresh), new FixtureStore(fresh).read());
        check(ProductionMigrationGate.completed(new FixtureStore(fresh).read()) && fresh.journal == null,
                "Proven fresh installation records completion transactionally for later starts.");
        Map<String, PreferenceSnapshot> notEmpty = new LinkedHashMap<>(emptyFiles);
        notEmpty.put(LegacyPresentationMapper.GRID, snapshot(Collections.singletonMap("columns", 7)));
        FixtureDisk existing = new FixtureDisk(new PresentationSnapshot(notEmpty));
        reject(() -> ProductionMigrationGate.markFresh(new FixtureStore(existing), new FixtureStore(existing).read()));
        check(existing.events.isEmpty(), "Fresh marker never overwrites existing settings.");
        Map<String, Object> source = new LinkedHashMap<>(); source.put("apps_per_row", 7);
        LegacyPresentationMapper.Plan absentBlocker = LegacyPresentationMapper.plan(snapshot(source), identities(), Collections.emptyMap(), null, false, true);
        ProductionMigrationGate.preflight(new PresentationSnapshot(absentBlocker.before), identities(), null);
        source.put("is_premium", true); source.put("fire_tv_ui_home_enabled", false);
        source.put("app_launch_count", 4); source.put("rate_app_shown", true);
        source.put("use_24h_clock", true);
        source.put("featured_artwork_fit", false);
        source.put("minimal_status_bar", false); source.put("status_bar_position", "Right");
        source.put("hide_status_bar_unless_focused", true); source.put("tinted_status_bar", true);
        source.put("fire_tv_ui_block_updates", false); source.put("fire_tv_ui_block_appstore", false);
        source.put("start_on_boot", false); source.put("override_home_button", false); source.put("better_home_detection", true);
        LegacyPresentationMapper.Plan plan = LegacyPresentationMapper.plan(snapshot(source), identities(), Collections.emptyMap(), null, false, true);
        PresentationSnapshot before = new PresentationSnapshot(plan.before);
        FixtureDisk disk = new FixtureDisk(before);
        ProductionMigrationGate.commit(new FixtureStore(disk), before, identities(), null);
        check(ProductionMigrationGate.completed(new FixtureStore(disk).read()) && disk.journal == null,
                "Complete marker is committed with verified native settings and resolved recovery.");
        for (String key : Arrays.asList("is_premium", "app_launch_count", "rate_app_shown", "use_24h_clock", "featured_artwork_fit", "minimal_status_bar", "status_bar_position", "hide_status_bar_unless_focused", "tinted_status_bar", "fire_tv_ui_block_updates", "fire_tv_ui_block_appstore", "fire_tv_ui_home_enabled", "start_on_boot", "override_home_button", "better_home_detection"))
            check(source.get(key).equals(disk.files.get(LauncherMigration.PREFERENCES).values().get(key)), "Directly consumed compatibility choice remains intact.");
        Map<String, Object> leftStatus = new LinkedHashMap<>(source); leftStatus.put("minimal_status_bar", true); leftStatus.put("status_bar_position", "Left");
        LegacyPresentationMapper.Plan leftPlan = LegacyPresentationMapper.plan(snapshot(leftStatus), identities(), Collections.emptyMap());
        ProductionMigrationGate.preflight(new PresentationSnapshot(leftPlan.before), identities(), null);
        Map<String, Object> minimalOnly = Collections.<String, Object>singletonMap("minimal_status_bar", false);
        LegacyPresentationMapper.Plan minimalPlan = LegacyPresentationMapper.plan(snapshot(minimalOnly), identities(), Collections.emptyMap());
        FixtureDisk minimalDisk = new FixtureDisk(new PresentationSnapshot(minimalPlan.before));
        ProductionMigrationGate.commit(new FixtureStore(minimalDisk), new FixtureStore(minimalDisk).read(), identities(), null);
        Map<String, Object> minimalSaved = minimalDisk.files.get(LauncherMigration.PREFERENCES).values();
        check(!minimalSaved.containsKey("status_bar_position") && !minimalSaved.containsKey("hide_status_bar_unless_focused")
                && !minimalSaved.containsKey("tinted_status_bar"), "Absent status choices are not synthesized.");
        for (String key : Arrays.asList("status_bar_position", "hide_status_bar_unless_focused", "tinted_status_bar")) {
            Map<String, Object> noMinimal = new LinkedHashMap<>(); noMinimal.put(key, source.get(key));
            LegacyPresentationMapper.Plan incomplete = LegacyPresentationMapper.plan(snapshot(noMinimal), identities(), Collections.emptyMap());
            reject(() -> ProductionMigrationGate.preflight(new PresentationSnapshot(incomplete.before), identities(), null));
        }
        Map<String, Object> badStatus = new LinkedHashMap<>(source); badStatus.put("status_bar_position", "center");
        LegacyPresentationMapper.Plan invalidStatus = LegacyPresentationMapper.plan(snapshot(badStatus), identities(), Collections.emptyMap());
        reject(() -> ProductionMigrationGate.preflight(new PresentationSnapshot(invalidStatus.before), identities(), null));
        for (String key : Arrays.asList("minimal_status_bar", "hide_status_bar_unless_focused", "tinted_status_bar")) {
            Map<String, Object> malformedStatus = new LinkedHashMap<>(source); malformedStatus.put(key, "true");
            LegacyPresentationMapper.Plan malformedPlan = LegacyPresentationMapper.plan(snapshot(malformedStatus), identities(), Collections.emptyMap());
            PresentationSnapshot original = new PresentationSnapshot(malformedPlan.before);
            FixtureDisk malformedDisk = new FixtureDisk(original);
            reject(() -> ProductionMigrationGate.commit(new FixtureStore(malformedDisk), original, identities(), null));
            check(malformedDisk.events.isEmpty() && original.sameValues(new FixtureStore(malformedDisk).read()), "Malformed status choice leaves every typed store unchanged.");
        }
        Map<String, Object> malformedClock = new LinkedHashMap<>(source); malformedClock.put("use_24h_clock", "true");
        LegacyPresentationMapper.Plan invalidClock = LegacyPresentationMapper.plan(snapshot(malformedClock), identities(), Collections.emptyMap());
        reject(() -> ProductionMigrationGate.preflight(new PresentationSnapshot(invalidClock.before), identities(), null));
        Map<String, Object> malformedArt = new LinkedHashMap<>(source); malformedArt.put("featured_artwork_fit", "false");
        LegacyPresentationMapper.Plan invalidArt = LegacyPresentationMapper.plan(snapshot(malformedArt), identities(), Collections.emptyMap());
        reject(() -> ProductionMigrationGate.preflight(new PresentationSnapshot(invalidArt.before), identities(), null));
        for (String key : Arrays.asList("fire_tv_ui_block_updates", "fire_tv_ui_block_appstore")) {
            for (Object unsupported : Arrays.<Object>asList(true, "false", 0)) {
                Map<String, Object> changed = new LinkedHashMap<>(source); changed.put(key, unsupported);
                LegacyPresentationMapper.Plan unsafe = LegacyPresentationMapper.plan(snapshot(changed), identities(), Collections.emptyMap());
                FixtureDisk unchanged = new FixtureDisk(new PresentationSnapshot(unsafe.before));
                reject(() -> ProductionMigrationGate.commit(new FixtureStore(unchanged), new FixtureStore(unchanged).read(), identities(), null));
                check(unchanged.events.isEmpty() && new PresentationSnapshot(unsafe.before).sameValues(new FixtureStore(unchanged).read()),
                        "Enabled or malformed update protection blocks without changing preferences.");
            }
        }
        source.put("unknown_future", true);
        LegacyPresentationMapper.Plan unsupported = LegacyPresentationMapper.plan(snapshot(source), identities(), Collections.emptyMap(), null, false, true);
        FixtureDisk blocked = new FixtureDisk(new PresentationSnapshot(unsupported.before));
        reject(() -> ProductionMigrationGate.commit(new FixtureStore(blocked), new FixtureStore(blocked).read(), identities(), null));
        check(blocked.events.isEmpty(), "Unknown saved data blocks before journal or preference writes.");
        source.remove("unknown_future"); source.put(LegacyPresentationMapper.VERSION_KEY, 1);
        LegacyPresentationMapper.Plan partial = LegacyPresentationMapper.plan(snapshot(source), identities(), Collections.emptyMap(), null, false, true);
        reject(() -> ProductionMigrationGate.preflight(new PresentationSnapshot(partial.before), identities(), null));
        String plexKey = "plex:" + repeat('9') + ":123";
        Map<String, PresentationMappingContext.Item> hiddenItems = new LinkedHashMap<>();
        hiddenItems.put(ITEM_A, new PresentationMappingContext.Item("com.jeremykenedy.firetv.ui", plexKey, "Fixture", false,
                "com.plexapp.android", plexKey));
        PresentationMappingContext stable = new PresentationMappingContext(Collections.emptyMap(), Collections.emptyMap(), hiddenItems, Collections.emptyMap());
        Map<String, Object> hiddenSource = Collections.<String, Object>singletonMap("hidden_featured_cards", set(ITEM_A));
        LegacyPresentationMapper.Plan hiddenPlan = LegacyPresentationMapper.plan(snapshot(hiddenSource), stable, Collections.emptyMap());
        ProductionMigrationGate.preflight(new PresentationSnapshot(hiddenPlan.before), stable, null);
        hiddenItems.put(ITEM_A, new PresentationMappingContext.Item("fixture.alpha", "program:one", "Fixture", false));
        PresentationMappingContext transientIdentity = new PresentationMappingContext(Collections.emptyMap(), Collections.emptyMap(), hiddenItems, Collections.emptyMap());
        reject(() -> ProductionMigrationGate.preflight(new PresentationSnapshot(hiddenPlan.before), transientIdentity, null));
    }
    private static void protectionPreflight() throws Exception {
        Map<String, Object> saved = new LinkedHashMap<>();
        saved.put("fire_tv_ui_protect_settings", true);
        saved.put("fire_tv_ui_keep_adb", true);
        saved.put("fire_tv_ui_deep_sleep_enabled", true);
        saved.put("fire_tv_ui_dream_unlocked", false);
        saved.put("fire_tv_ui_preview_pending", false);
        saved.put("fire_tv_ui_dream_enabled", true);
        saved.put("fire_tv_ui_sleep", 5000);
        saved.put("fire_tv_ui_sleep_saved", 1200000);
        saved.put("fire_tv_ui_auto_wake", 1);
        saved.put("fire_tv_ui_idle", 300000);
        saved.put("fire_tv_ui_dream", "fixture.dream/.Service");
        LegacyPresentationMapper.Plan plan = LegacyPresentationMapper.plan(snapshot(saved), identities(), Collections.emptyMap());
        FixtureDisk disk = new FixtureDisk(new PresentationSnapshot(plan.before));
        ProductionMigrationGate.commit(new FixtureStore(disk), new FixtureStore(disk).read(), identities(), null);
        Map<String, Object> retained = new LinkedHashMap<>(disk.files.get(LauncherMigration.PREFERENCES).values());
        retained.remove("source_ui_strict_migration_version");
        retained.remove(LegacyPresentationMapper.VERSION_KEY);
        check(saved.equals(retained), "Device choices and their exact types survive strict commit.");
        for (String key : saved.keySet()) {
            Map<String, Object> invalid = new LinkedHashMap<>(saved);
            invalid.put(key, key.equals("fire_tv_ui_dream") ? 1 : "invalid");
            assertProtectionRejected(invalid);
        }
        for (Object[] invalid : new Object[][]{
                {"fire_tv_ui_sleep", 0}, {"fire_tv_ui_sleep_saved", -1},
                {"fire_tv_ui_auto_wake", 2}, {"fire_tv_ui_idle", 0},
                {"fire_tv_ui_preview_pending", true}, {"fire_tv_ui_dream", "invalid"},
                {"fire_tv_ui_dream", "fixture.dream/."}, {"fire_tv_ui_dream", "fixture.dream/$."},
                {"fire_tv_ui_dream", "fixture.dream/.Service..Child"}, {"fire_tv_ui_dream", "fixture.dream/.1Service"}}) {
            Map<String, Object> choices = new LinkedHashMap<>(saved);
            choices.put((String) invalid[0], invalid[1]);
            assertProtectionRejected(choices);
        }
        Map<String, Object> dormant = new LinkedHashMap<>(saved);
        dormant.put("fire_tv_ui_protect_settings", false);
        dormant.put("fire_tv_ui_idle", "invalid");
        assertProtectionRejected(dormant);
    }
    private static void assertProtectionRejected(Map<String, Object> source) throws Exception {
        LegacyPresentationMapper.Plan plan = LegacyPresentationMapper.plan(snapshot(source), identities(), Collections.emptyMap(), null, false, true);
        PresentationSnapshot original = new PresentationSnapshot(plan.before);
        FixtureDisk disk = new FixtureDisk(original);
        reject(() -> ProductionMigrationGate.commit(new FixtureStore(disk), original, identities(), null));
        check(disk.events.isEmpty() && disk.journal == null && original.sameValues(new FixtureStore(disk).read()),
                "Invalid protection settings are rejected before journaling or preference writes.");
    }
    private static final class FixtureDisk {
        final Map<String, PreferenceSnapshot> files;
        final List<String> events = new ArrayList<>();
        byte[] journal;
        FixtureDisk(PresentationSnapshot initial) { files = new LinkedHashMap<>(initial.stores); }
    }
    private static final class FixtureStore implements PresentationUpgrade.Store {
        final FixtureDisk disk;
        int writes, failAt, dieAt;
        boolean failRecovery, failRollback, corruptAfterWrite, changeDuringRecovery, failResolution;
        FixtureStore(FixtureDisk disk) { this.disk = disk; }
        @Override public Object lock() { return disk; }
        @Override public PresentationSnapshot read() { return new PresentationSnapshot(disk.files); }
        @Override public boolean hasRecovery() { return disk.journal != null; }
        @Override public PresentationSnapshot readRecovery() throws IOException { return PresentationSnapshot.decode(disk.journal); }
        @Override public void resolveRecovery(PresentationSnapshot original, PresentationSnapshot current) throws IOException {
            if (failResolution || !original.sameValues(readRecovery()) || !current.matches(read()))
                throw new IOException("Fixture resolution failed.");
            disk.journal = null;
        }
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
