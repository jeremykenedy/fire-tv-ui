package com.jeremykenedy.firetv.ui.nativeapp.setups;

import android.content.Context;
import android.net.Uri;
import android.util.AtomicFile;

import com.jeremykenedy.firetv.ui.nativeapp.migration.PreferenceSnapshot;
import com.jeremykenedy.firetv.ui.nativeapp.backup.PortableBackup;

import org.json.*;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

public final class SetupTest {
    private static int checks;

    private interface Work {
        void run() throws Exception;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void rejects(Work work, String message) throws Exception {
        checks++;
        try {
            work.run();
        } catch (Exception expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static Context.Prefs prefs(Context context, String file) {
        context.getSharedPreferences(file, 0);
        return context.stores.get(file);
    }

    private static boolean same(
            Map<String, PreferenceSnapshot> a, Map<String, PreferenceSnapshot> b) {
        for (String file : a.keySet())
            if (!a.get(file).values().equals(b.get(file).values())) return false;
        return a.keySet().equals(b.keySet());
    }

    public static void main(String[] args) throws Exception {
        holidayPresets(new File(args[0], "holiday-fixtures"));
        for (String style : Arrays.asList("default", "none", "fade", "slide")) {
            Context transitionContext = new Context(new File(args[0], "transition-" + style));
            prefs(transitionContext, "launcher_prefs").values.put("menu_transition_style", style);
            Map<String, PreferenceSnapshot> saved = PresentationState.capture(transitionContext);
            prefs(transitionContext, "launcher_prefs").values.put("menu_transition_style", "none");
            PresentationState.apply(transitionContext, saved, true);
            check(style.equals(prefs(transitionContext, "launcher_prefs").values.get("menu_transition_style")),
                    "Saved setup restores the exact settings transition choice.");
        }
        Context focusContext = new Context(new File(args[0], "focus-mode"));
        prefs(focusContext, "native_focus").values.put("legacy_app_default", true);
        Map<String, PreferenceSnapshot> legacyMode = PresentationState.capture(focusContext);
        check(Boolean.TRUE.equals(legacyMode.get("native_focus").values().get("legacy_app_default")), "Legacy app focus survives capture.");
        prefs(focusContext, "native_focus").values.clear();
        prefs(focusContext, "native_focus").values.put("growth_percent", 0);
        Map<String, PreferenceSnapshot> sourceMode = PresentationState.capture(focusContext);
        PresentationState.apply(focusContext, legacyMode, true);
        check(Boolean.TRUE.equals(prefs(focusContext, "native_focus").values.get("legacy_app_default")) && !prefs(focusContext, "native_focus").values.containsKey("growth_percent"), "Restoring setup restores legacy mode and removes later native choice.");
        PresentationState.apply(focusContext, sourceMode, true);
        check(!prefs(focusContext, "native_focus").values.containsKey("legacy_app_default") && Integer.valueOf(0).equals(prefs(focusContext, "native_focus").values.get("growth_percent")), "Restoring a native setup clears legacy mode and restores explicit focus.");
        Map<String, PreferenceSnapshot> invalidFocus = new LinkedHashMap<>(legacyMode);
        invalidFocus.put("native_focus", new PreferenceSnapshot(Collections.<String, Object>singletonMap("legacy_app_default", "true")));
        rejects(() -> PresentationState.validate(invalidFocus), "Malformed legacy marker is rejected.");
        Context statusContext = new Context(new File(args[0], "status-mode"));
        Context.Prefs statusPrefs = prefs(statusContext, "launcher_prefs");
        statusPrefs.values.put("source_status_bar_off", true);
        statusPrefs.values.put("minimal_status_bar", false);
        SavedSetupManager statusManager = new SavedSetupManager(statusContext);
        String offSetup = statusManager.save("Status off", null);
        Map<String, PreferenceSnapshot> offState = PresentationState.capture(statusContext);
        PortableBackup.Validator portable = state -> PresentationState.validatePortable(state);
        Map<String, PreferenceSnapshot> backup = PortableBackup.decode(PortableBackup.encode(offState, portable), portable);
        statusPrefs.values.remove("source_status_bar_off");
        statusManager.apply(offSetup, true);
        check(Boolean.TRUE.equals(statusPrefs.values.get("source_status_bar_off")), "Saved setup restores explicit status off.");
        statusPrefs.values.remove("source_status_bar_off");
        PresentationState.apply(statusContext, backup, true);
        check(Boolean.TRUE.equals(statusPrefs.values.get("source_status_bar_off")), "Portable backup restores explicit status off.");
        JSONObject sourceOnly = new JSONObject().put("keys", new JSONArray(Arrays.asList("source_status_bar_off")))
                .put("values", new JSONObject().put("source_status_bar_off", true));
        check(Boolean.TRUE.equals(statusManager.prepareLegacy(sourceOnly,
                com.jeremykenedy.firetv.ui.nativeapp.migration.PresentationMappingContext.empty(), true)
                .get("launcher_prefs").values().get("source_status_bar_off")), "Source marker bypasses legacy mapper.");
        JSONObject legacyFull = new JSONObject().put("keys", new JSONArray(Arrays.asList("minimal_status_bar")))
                .put("values", new JSONObject().put("minimal_status_bar", false));
        check(!statusManager.prepareLegacy(legacyFull,
                com.jeremykenedy.firetv.ui.nativeapp.migration.PresentationMappingContext.empty(), true)
                .get("launcher_prefs").values().containsKey("source_status_bar_off"), "Imported legacy mode clears source off marker.");
        JSONObject unrelated = new JSONObject().put("keys", new JSONArray(Arrays.asList("use_24h_clock")))
                .put("values", new JSONObject().put("use_24h_clock", true));
        check(Boolean.TRUE.equals(statusManager.prepareLegacy(unrelated,
                com.jeremykenedy.firetv.ui.nativeapp.migration.PresentationMappingContext.empty(), true)
                .get("launcher_prefs").values().get("source_status_bar_off")), "Unrelated legacy setup keeps source off marker.");
        final Context context = new Context(new File(args[0], "fixtures"));
        prefs(context, "launcher_prefs").values.put("access_token", "private-fixture");
        prefs(context, "launcher_prefs").values.put("fire_tv_ui_sleep", 1234);
        prefs(context, "launcher_prefs").values.put("folder_lock_pin", "private-fixture");
        prefs(context, "launcher_prefs").values.put("menu_overlay_frozen", false);
        prefs(context, "native_app_grid").values.put("columns", 16);
        prefs(context, "native_app_grid").values.put("owner_future_metadata", "keep");
        prefs(context, "native_focus").values.put("growth_percent", 6);
        prefs(context, "native_cards").values.put("hidden_packages", "fixture.retained\nfixture.second");
        prefs(context, "native_inputs").values.put("name:vendor/.Input/HDMI1", "Receiver");
        prefs(context, "native_inputs").values.put("known:vendor/.Input/HDMI1", true);
        prefs(context, "native_icon_packs").values.put("package", "clean-line");
        prefs(context, "native_world_clocks").values.put("clocks", "v1\nAmerica/Los_Angeles\tHome");
        prefs(context, "native_breathing_break").values.put("inhale", 5);
        prefs(context, "native_breathing_break").values.put("elapsed", 1000L);
        prefs(context, "native_breathing_break").values.put("running", false);
        final Map<String, PreferenceSnapshot> first = PresentationState.capture(context);
        check(
                !first.get("launcher_prefs").values().containsKey("access_token"),
                "credentials excluded");
        check(
                !first.get("launcher_prefs").values().containsKey("folder_lock_pin"),
                "locks excluded");
        check(
                first.get("launcher_prefs").values().get("menu_overlay_frozen").equals(false),
                "explicit false retained");
        check(
                !first.get("native_focus").values().containsKey("duration_millis"),
                "absent default retained as absent");
        check(
                !first.get("native_inputs").values().containsKey("known:vendor/.Input/HDMI1"),
                "input discovery metadata excluded");
        PresentationState.validatePortable(first);
        check("fixture.retained\nfixture.second".equals(first.get("native_cards").values().get("hidden_packages")), "Package card choices are included in portable backup and setup state.");
        check(same(first, PortableBackup.decode(PortableBackup.encode(first, portable), portable)), "Portable backup round trip retains package card choices and absence.");
        Map<String, PreferenceSnapshot> holidayPack = new LinkedHashMap<>(first);
        holidayPack.put("native_icon_packs", new PreferenceSnapshot(Collections.singletonMap("package", "builtin:holiday:hanukkah")));
        PresentationState.validatePortable(holidayPack);
        holidayPack.put("native_icon_packs", new PreferenceSnapshot(Collections.singletonMap("package", "builtin:holiday:unknown")));
        rejects(() -> PresentationState.validatePortable(holidayPack), "unknown holiday pack rejected");
        Map<String, PreferenceSnapshot> exercise = new LinkedHashMap<>(first);
        exercise.put(
                "native_breathing_break",
                new PreferenceSnapshot(Collections.singletonMap("inhale", 6)));
        prefs(context, "native_breathing_break").values.put("running", true);
        rejects(
                () -> PresentationState.apply(context, exercise, true),
                "active breathing runtime blocks conflicting configuration restore");
        check(
                !PresentationState.hasRecovery(context),
                "rejected live-session conflict writes no journal");
        prefs(context, "native_breathing_break").values.put("running", false);
        final SavedSetupManager manager = new SavedSetupManager(context);
        final String id = manager.save("Evening", null);
        prefs(context, "native_cards").values.put("hidden_packages", "fixture.changed");
        prefs(context, "native_focus").values.put("duration_millis", 100);
        prefs(context, "native_app_grid").values.put("columns", 3);
        prefs(context, "launcher_prefs").values.put("color_later", 77);
        manager.apply(id, true);
        check(
                same(first, PresentationState.capture(context)),
                "all presentation choices and absence restored");
        check(
                prefs(context, "native_app_grid")
                        .values
                        .get("owner_future_metadata")
                        .equals("keep"),
                "unowned native key untouched");
        check(
                prefs(context, "launcher_prefs")
                                .values
                                .get("access_token")
                                .equals("private-fixture")
                        && prefs(context, "launcher_prefs")
                                .values
                                .get("fire_tv_ui_sleep")
                                .equals(1234),
                "account and TV controls untouched");
        check(!PresentationState.hasRecovery(context), "successful apply resolves private journal");
        manager.apply(id, true);
        check(!PresentationState.hasRecovery(context), "recurring apply supported");
        AtomicFile.failDelete = true;
        rejects(
                () -> manager.apply(id, true),
                "failed journal deletion cannot report successful apply");
        check(PresentationState.hasRecovery(context), "failed cleanup retains unresolved recovery");
        rejects(
                () -> PresentationState.recover(context),
                "failed cleanup cannot report successful recovery");
        rejects(() -> manager.apply(id, true), "unresolved cleanup blocks replay");
        AtomicFile.failDelete = false;
        PresentationState.recover(context);
        check(!PresentationState.hasRecovery(context), "retry recovery verifies cleanup absence");
        rejects(() -> manager.save(" evening ", null), "duplicate names rejected");
        rejects(() -> manager.rename(id, "\n"), "blank name rejected");
        AtomicFile.failNext = true;
        rejects(() -> manager.rename(id, "New"), "atomic index failure rejected");
        check(
                manager.list().get(0).getString("name").equals("Evening"),
                "index failure leaves original entry");
        prefs(context, "native_focus").values.put("growth_percent", 10);
        final Map<String, PreferenceSnapshot> beforeFailure = PresentationState.capture(context);
        prefs(context, "native_app_grid").failures = 1;
        rejects(() -> manager.apply(id, true), "mutating failed commit rejected");
        check(
                same(beforeFailure, PresentationState.capture(context)),
                "multi-file failure rolls back mutated memory");
        check(!PresentationState.hasRecovery(context), "verified rollback resolves journal");
        prefs(context, "native_app_grid").failures = 2;
        rejects(() -> manager.apply(id, true), "failed rollback rejected");
        check(PresentationState.hasRecovery(context), "failed rollback keeps private journal");
        rejects(() -> manager.apply(id, true), "pending recovery blocks new apply");
        PresentationState.recover(context);
        check(
                same(beforeFailure, PresentationState.capture(context)),
                "explicit recovery restores presentation");
        Map<String, PreferenceSnapshot> malicious = new LinkedHashMap<>(first);
        malicious.put(
                "private_account",
                new PreferenceSnapshot(Collections.singletonMap("token", "fixture")));
        rejects(() -> PresentationState.validate(malicious), "unknown store rejected");
        malicious.remove("private_account");
        malicious.put(
                "native_focus",
                new PreferenceSnapshot(Collections.singletonMap("access_token", "fixture")));
        rejects(() -> PresentationState.validate(malicious), "unknown key rejected");
        JSONObject subset =
                new JSONObject()
                        .put("keys", new JSONArray(Arrays.asList("apps_per_row")))
                        .put("values", new JSONObject().put("apps_per_row", 8));
        Map<String, PreferenceSnapshot> legacyPlan =
                manager.prepareLegacy(
                        subset,
                        com.jeremykenedy.firetv.ui.nativeapp.migration.PresentationMappingContext
                                .empty(),
                        true);
        check(
                legacyPlan.get("native_app_grid").values().get("columns").equals(8),
                "reviewed legacy scalar mapped through shared mapper");
        check(
                legacyPlan.get("launcher_prefs").values().get("menu_overlay_frozen").equals(false),
                "legacy subset preserves unrelated presentation keys");
        Context groupContext = new Context(new File(args[0], "grouped-fixture"));
        SavedSetupManager groupManager = new SavedSetupManager(groupContext);
        String grouped = "[[{\"id\":\"11111111-1111-1111-1111-111111111111\",\"type\":\"DigitalClock\",\"size\":\"Big\",\"data\":{}}]]";
        prefs(groupContext, "launcher_prefs").values.put("source_status_bar_off", true);
        prefs(groupContext, "native_layout").values.put("widget_groups", grouped);
        String importedGroup = groupManager.save("Imported group fixture", null);
        prefs(groupContext, "launcher_prefs").values.remove("source_status_bar_off");
        prefs(groupContext, "native_layout").values.remove("widget_groups");
        groupManager.apply(importedGroup, true);
        check(Boolean.TRUE.equals(prefs(groupContext, "launcher_prefs").values.get("source_status_bar_off"))
                && grouped.equals(prefs(groupContext, "native_layout").values.get("widget_groups")),
                "Saved setup restores imported header and exact native widget groups.");
        prefs(groupContext, "launcher_prefs").values.remove("source_status_bar_off");
        prefs(groupContext, "native_layout").values.remove("widget_groups");
        String emptyGroup = groupManager.save("No groups", null);
        prefs(groupContext, "native_layout").values.put("widget_groups", grouped);
        groupManager.apply(emptyGroup, true);
        check(!prefs(groupContext, "native_layout").values.containsKey("widget_groups"),
                "A new setup restores the explicit absence of groups.");
        Map<String, PreferenceSnapshot> oldPortable = new LinkedHashMap<>(PresentationState.capture(groupContext));
        Map<String, Object> portableLayout = new LinkedHashMap<>(oldPortable.get("native_layout").values());
        portableLayout.remove("widget_groups");
        oldPortable.put("native_layout", new PreferenceSnapshot(portableLayout));
        prefs(groupContext, "native_layout").values.put("widget_groups", grouped);
        byte[] exportedWithoutGroups = PortableBackup.encode(PresentationState.capturePortable(groupContext), portable);
        check(!PortableBackup.decode(exportedWithoutGroups, portable).get("native_layout").values().containsKey("widget_groups")
                && grouped.equals(prefs(groupContext, "native_layout").values.get("widget_groups")),
                "Portable export leaves groups on the TV and exports supported presentation.");
        byte[] oldBackup = PortableBackup.encode(oldPortable, portable);
        PresentationState.applyPortable(groupContext, PortableBackup.decode(oldBackup, portable));
        check(grouped.equals(prefs(groupContext, "native_layout").values.get("widget_groups")),
                "Older portable backup preserves groups it could not capture.");
        Map<String, PreferenceSnapshot> privateGroup = new LinkedHashMap<>(oldPortable);
        portableLayout.put("widget_groups", "[[{\"id\":\"11111111-1111-1111-1111-111111111111\",\"type\":\"DigitalClock\",\"size\":\"Big\",\"data\":{\"token\":\"private\"}}]]");
        privateGroup.put("native_layout", new PreferenceSnapshot(portableLayout));
        rejects(() -> PortableBackup.encode(privateGroup, portable), "Portable nested widget data rejected on export.");
        File groupFile = new File(groupContext.getFilesDir(), "saved-setups/setups.json");
        JSONObject oldSetups = new JSONObject(new String(Files.readAllBytes(groupFile.toPath()), java.nio.charset.StandardCharsets.UTF_8));
        JSONObject oldSetup = oldSetups.getJSONArray("setups").getJSONObject(1);
        oldSetup.remove("nativeVersion");
        Files.write(groupFile.toPath(), oldSetups.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        prefs(groupContext, "native_layout").values.put("widget_groups", grouped);
        groupManager.apply(emptyGroup, true);
        check(grouped.equals(prefs(groupContext, "native_layout").values.get("widget_groups")),
                "An older setup preserves groups it could not capture.");
        oldSetup.put("nativeVersion", 3);
        Files.write(groupFile.toPath(), oldSetups.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Map<String, PreferenceSnapshot> beforeUnknownSetup = PresentationState.capture(groupContext);
        rejects(() -> groupManager.apply(emptyGroup, true), "Unsupported setup versions are rejected.");
        check(same(beforeUnknownSetup, PresentationState.capture(groupContext)),
                "Unsupported setup versions leave presentation unchanged.");
        Map<String, PreferenceSnapshot> beforeDirect = PresentationState.capture(context);
        prefs(context, "launcher_prefs").values.put("use_24h_clock", false);
        prefs(context, "launcher_prefs").values.put("featured_artwork_fit", true);
        prefs(context, "launcher_prefs").values.put("minimal_status_bar", false);
        prefs(context, "launcher_prefs").values.put("tinted_status_bar", true);
        JSONObject direct = new JSONObject()
                .put("keys", new JSONArray(Arrays.asList("use_24h_clock", "featured_artwork_fit",
                        "minimal_status_bar", "status_bar_position", "hide_status_bar_unless_focused")))
                .put("values", new JSONObject().put("use_24h_clock", true)
                        .put("minimal_status_bar", true).put("status_bar_position", "Left")
                        .put("hide_status_bar_unless_focused", false));
        Map<String, PreferenceSnapshot> directTarget = manager.prepareLegacy(direct,
                com.jeremykenedy.firetv.ui.nativeapp.migration.PresentationMappingContext.empty(), true);
        Map<String, Object> directValues = directTarget.get("launcher_prefs").values();
        check(Boolean.TRUE.equals(directValues.get("use_24h_clock"))
                && !directValues.containsKey("featured_artwork_fit")
                && Boolean.TRUE.equals(directValues.get("minimal_status_bar"))
                && "Left".equals(directValues.get("status_bar_position"))
                && Boolean.FALSE.equals(directValues.get("hide_status_bar_unless_focused"))
                && Boolean.TRUE.equals(directValues.get("tinted_status_bar")),
                "Legacy direct choices apply, owned absence resets and unowned tint remains.");
        Map<String, PreferenceSnapshot> beforeDirectApply = PresentationState.capture(context);
        prefs(context, "launcher_prefs").failures = 1;
        rejects(() -> PresentationState.apply(context, directTarget, true), "Direct choice commit failure rejected.");
        check(same(beforeDirectApply, PresentationState.capture(context)) && !PresentationState.hasRecovery(context),
                "Direct choice commit failure restores exact prior preferences.");
        PresentationState.apply(context, directTarget, true);
        check(prefs(context, "launcher_prefs").values.get("use_24h_clock").equals(true)
                && !prefs(context, "launcher_prefs").values.containsKey("featured_artwork_fit"),
                "Applying a setup persists direct choices and reset.");
        Map<String, PreferenceSnapshot> appliedDirect = PresentationState.capture(context);
        for (JSONObject bad : Arrays.asList(
                new JSONObject().put("keys", new JSONArray(Arrays.asList("minimal_status_bar")))
                        .put("values", new JSONObject().put("minimal_status_bar", "true")),
                new JSONObject().put("keys", new JSONArray(Arrays.asList("status_bar_position")))
                        .put("values", new JSONObject().put("status_bar_position", "Left")),
                new JSONObject().put("keys", new JSONArray(Arrays.asList("minimal_status_bar", "status_bar_position")))
                        .put("values", new JSONObject().put("minimal_status_bar", true).put("status_bar_position", "Center")),
                new JSONObject().put("keys", new JSONArray(Arrays.asList("use_24h_clock", "poster_mode")))
                        .put("values", new JSONObject().put("use_24h_clock", false).put("poster_mode", true)))) {
            rejects(() -> manager.prepareLegacy(bad,
                    com.jeremykenedy.firetv.ui.nativeapp.migration.PresentationMappingContext.empty(), true),
                    "Malformed, incomplete or unsupported direct choice blocks setup.");
            check(same(appliedDirect, PresentationState.capture(context)) && !PresentationState.hasRecovery(context),
                    "Rejected preparation leaves preferences and journal unchanged.");
        }
        PresentationState.apply(context, beforeDirect, true);
        Map<String, Object> previousAppearance = new LinkedHashMap<>(prefs(context, "source_appearance").values);
        prefs(context, "source_appearance").values.put("scene", "christmas");
        prefs(context, "source_appearance").values.put("base", "aurora");
        prefs(context, "source_appearance").values.put("legacy_gradient", "cloud_nine");
        prefs(context, "source_appearance").values.put("overlay", "snow");
        JSONObject gradientSetup = new JSONObject()
                .put("keys", new JSONArray(Arrays.asList("mode", "custom_gradient")))
                .put("values", new JSONObject().put("mode", "custom").put("custom_gradient", "Electric Indigo"));
        Map<String, Object> gradientTarget = manager.prepareLegacy(gradientSetup,
                com.jeremykenedy.firetv.ui.nativeapp.migration.PresentationMappingContext.empty(), true)
                .get("source_appearance").values();
        check("still_legacy_gradient".equals(gradientTarget.get("base")) && "".equals(gradientTarget.get("scene"))
                && "electric_indigo".equals(gradientTarget.get("legacy_gradient")) && "snow".equals(gradientTarget.get("overlay")),
                "Legacy setup replaces its owned scene and gradient while preserving overlay");
        JSONObject clearedGradient = new JSONObject()
                .put("keys", new JSONArray(Arrays.asList("mode", "custom_gradient"))).put("values", new JSONObject());
        Map<String, Object> clearedTarget = manager.prepareLegacy(clearedGradient,
                com.jeremykenedy.firetv.ui.nativeapp.migration.PresentationMappingContext.empty(), true)
                .get("source_appearance").values();
        check("aurora".equals(clearedTarget.get("base")) && "christmas".equals(clearedTarget.get("scene"))
                && !clearedTarget.containsKey("legacy_gradient") && "snow".equals(clearedTarget.get("overlay")),
                "Absent mode preserves unrelated background and scene while clearing the saved gradient");
        JSONObject videoSetup = new JSONObject()
                .put("keys", new JSONArray(Arrays.asList("mode", "custom_video_uri")))
                .put("values", new JSONObject().put("mode", "customv").put("custom_video_uri", "content://fixture.invalid/video"));
        Map<String, Object> videoTarget = manager.prepareLegacy(videoSetup,
                com.jeremykenedy.firetv.ui.nativeapp.migration.PresentationMappingContext.empty(), true)
                .get("source_appearance").values();
        check("video".equals(videoTarget.get("wallpaper_mode")) && "aurora".equals(videoTarget.get("base"))
                && "christmas".equals(videoTarget.get("scene")) && "cloud_nine".equals(videoTarget.get("legacy_gradient")),
                "Video mode preserves independent background, scene and dormant gradient");
        JSONObject videoResetBase = new JSONObject()
                .put("keys", new JSONArray(Arrays.asList("mode", "custom_video_uri", "menu_animation")))
                .put("values", new JSONObject().put("mode", "customv").put("custom_video_uri", "content://fixture.invalid/video"));
        Map<String, Object> resetBaseTarget = manager.prepareLegacy(videoResetBase,
                com.jeremykenedy.firetv.ui.nativeapp.migration.PresentationMappingContext.empty(), true)
                .get("source_appearance").values();
        check(!resetBaseTarget.containsKey("base") && "christmas".equals(resetBaseTarget.get("scene")),
                "Explicitly absent animation resets only its own base");
        prefs(context, "source_appearance").values.clear();
        prefs(context, "source_appearance").values.putAll(previousAppearance);
        JSONObject excludedIcons = new JSONObject()
                .put("keys", new JSONArray(Arrays.asList("icon_pack_package", "factory_icon_packages", "app_visibility")))
                .put("values", new JSONObject()
                        .put("icon_pack_package", "unavailable.fixture.pack")
                        .put("factory_icon_packages", new JSONArray(Arrays.asList("missing.fixture.app")))
                        .put("app_visibility", "{\"missing.fixture.app\":{\"customIconUri\":\"file:/missing-private-image\"}}"));
        prefs(context, "native_icon_packs").values.put("package", "clean-line");
        Map<String, PreferenceSnapshot> noIcons = manager.prepareLegacy(excludedIcons,
                com.jeremykenedy.firetv.ui.nativeapp.migration.PresentationMappingContext.empty(), false);
        check(noIcons.get("native_icon_packs").values().get("package").equals("clean-line"),
                "legacy icon exclusion retains current native pack without requiring unavailable pack or image identities");
        check(noIcons.get("native_icons").values().equals(PresentationState.capture(context).get("native_icons").values()),
                "legacy icon exclusion retains every current native icon");
        rejects(() -> manager.prepareLegacy(excludedIcons,
                com.jeremykenedy.firetv.ui.nativeapp.migration.PresentationMappingContext.empty(), true),
                "included legacy icons still require verified identities");
        JSONObject unsupported =
                new JSONObject()
                        .put("keys", new JSONArray(Arrays.asList("folders")))
                        .put("values", new JSONObject().put("folders", "[]"));
        rejects(
                () ->
                        manager.prepareLegacy(
                                unsupported,
                                com.jeremykenedy.firetv.ui.nativeapp.migration
                                        .PresentationMappingContext.empty(),
                                true),
                "unmapped legacy format explicitly blocked");
        File journal = new File(context.getNoBackupFilesDir(), "saved-setup-apply-v1.json");
        Files.write(
                journal.toPath(),
                SavedSetupManager.encodeNative(beforeFailure)
                        .toString()
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        prefs(context, "native_app_grid").values.put("columns", 9);
        prefs(context, "native_focus").values.put("duration_millis", "malformed fixture");
        prefs(context, "launcher_prefs").values.put("apps_per_row", "malformed fixture");
        PresentationState.recover(context);
        check(
                same(beforeFailure, PresentationState.capture(context)),
                "restart recovery restores journal after simulated interruption");
        Map<String, PreferenceSnapshot> opaque = new LinkedHashMap<>(first);
        Map<String, Object> opaqueLegacy =
                new LinkedHashMap<>(first.get("launcher_prefs").values());
        opaqueLegacy.put("widgets_config", "{\"token\":\"fixture\"}");
        opaque.put("launcher_prefs", new PreferenceSnapshot(opaqueLegacy));
        rejects(
                () -> PresentationState.validatePortable(opaque),
                "portable opaque nested data rejected");
        File source = new File(context.files, "source.png");
        Files.write(source.toPath(), new byte[] {1, 2, 3});
        prefs(context, "source_appearance")
                .values
                .put("wallpaper_image_uri", Uri.fromFile(source).toString());
        prefs(context, "source_appearance").values.put("wallpaper_mode", "image");
        final String imageId = manager.save("Picture", null);
        final Map<String, PreferenceSnapshot> personal = PresentationState.capture(context);
        rejects(
                () -> PresentationState.validatePortable(personal),
                "portable personal media explicitly rejected");
        Files.delete(source.toPath());
        manager.apply(imageId, true);
        String retained =
                (String) prefs(context, "source_appearance").values.get("wallpaper_image_uri");
        check(
                new File(Uri.parse(retained).getPath()).isFile(),
                "saved wallpaper survives original source deletion");
        new File(Uri.parse(retained).getPath()).delete();
        final Map<String, PreferenceSnapshot> beforeMissing = PresentationState.capture(context);
        rejects(() -> manager.apply(imageId, true), "missing retained image rejected");
        check(
                manager.list().size() == 2
                        && same(beforeMissing, PresentationState.capture(context)),
                "missing media keeps setup and current preferences");
        prefs(context, "source_appearance").values.clear();
        String choice = "image:12345678-1234-1234-1234-123456789012.png";
        File icon = new File(context.files, "icons/" + choice.substring(6));
        icon.getParentFile().mkdirs();
        Files.write(icon.toPath(), new byte[] {8, 7, 6});
        prefs(context, "native_icons").values.put("proof.app/.Main", choice);
        final String iconId = manager.save("Artwork", null);
        icon.delete();
        prefs(context, "native_icons").values.put("proof.app/.Main", "factory");
        manager.apply(iconId, false);
        check(
                prefs(context, "native_icons").values.get("proof.app/.Main").equals("factory")
                        && !icon.exists(),
                "excluding icons preserves active choice and avoids media restoration");
        manager.apply(iconId, true);
        check(
                icon.isFile()
                        && prefs(context, "native_icons")
                                .values
                                .get("proof.app/.Main")
                                .equals(choice),
                "retained per-app artwork restored after original deletion");
        File activeRetained = new File(context.files, "saved-setups/active.image");
        Files.write(activeRetained.toPath(), new byte[] {2, 3, 4});
        prefs(context, "source_appearance")
                .values
                .put("wallpaper_image_uri", Uri.fromFile(activeRetained).toString());
        manager.delete(iconId);
        check(
                new File(context.files, "saved-setups")
                                .listFiles((dir, name) -> name.endsWith(".image"))
                                .length
                        >= 1,
                "deletion retains a referenced active wallpaper or other setup asset");
        prefs(context, "native_icons").values.clear();
        prefs(context, "source_appearance").values.clear();
        for (int i = manager.list().size(); i < 24; i++) manager.save("Setup " + i, null);
        rejects(() -> manager.save("Too many", null), "24 setup bound enforced");
        manager.rename(id, "Evening renamed");
        check(manager.list().get(0).getString("name").equals("Evening renamed"), "rename persists");
        prefs(context, "source_appearance").values.clear();
        prefs(context, "native_app_grid").values.put("show_all_apps_button", false);
        prefs(context, "native_inputs")
                .values
                .put("order", "vendor/.Input/HDMI2\nvendor/.Input/HDMI1");
        prefs(context, "native_inputs").values.put("hidden:vendor/.Input/HDMI1", false);
        prefs(context, "native_inputs").values.put("hidden:vendor/.Input/HDMI2", true);
        final Map<String, PreferenceSnapshot> organized = PresentationState.capture(context);
        check(
                Boolean.FALSE.equals(
                        organized.get("native_app_grid").values().get("show_all_apps_button")),
                "explicit All apps false retained");
        check(
                Boolean.FALSE.equals(
                        organized.get("native_inputs").values().get("hidden:vendor/.Input/HDMI1")),
                "explicit input shown false retained");
        check(
                organized
                        .get("native_inputs")
                        .values()
                        .get("order")
                        .equals("vendor/.Input/HDMI2\nvendor/.Input/HDMI1"),
                "exact input order retained");
        prefs(context, "native_inputs").values.put("hidden:vendor/.Input/HDMI1", true);
        prefs(context, "native_inputs").values.put("order", "");
        prefs(context, "native_app_grid").values.put("show_all_apps_button", true);
        PresentationState.apply(context, organized, true);
        check(
                same(organized, PresentationState.capture(context)),
                "input organization and All apps restored transactionally");
        prefs(context, "native_inputs").values.put("hidden:vendor/.Input/HDMI1", "false");
        rejects(() -> PresentationState.capture(context), "wrong input hidden type rejected");
        prefs(context, "native_inputs").values.put("hidden:vendor/.Input/HDMI1", false);
        prefs(context, "native_inputs")
                .values
                .put("order", "vendor/.Input/HDMI1\nvendor/.Input/HDMI1");
        rejects(() -> PresentationState.capture(context), "duplicate input order rejected");
        prefs(context, "native_inputs").values.put("order", "");
        prefs(context, "native_app_grid").values.put("show_all_apps_button", "false");
        rejects(() -> PresentationState.capture(context), "wrong All apps flag type rejected");
        prefs(context, "native_app_grid").values.put("show_all_apps_button", false);
        StringBuilder longIdBuilder = new StringBuilder();
        for (int i = 0; i < 2042; i++) longIdBuilder.append('a');
        String longId = longIdBuilder.toString();
        prefs(context, "native_inputs").values.put("hidden:" + longId, false);
        prefs(context, "native_icons").values.put("input:" + longId, "factory");
        Map<String, PreferenceSnapshot> longSnapshot = PresentationState.capture(context);
        check(
                Boolean.FALSE.equals(
                        SavedSetupManager.decodeNative(SavedSetupManager.encodeNative(longSnapshot))
                                .get("native_inputs")
                                .values()
                                .get("hidden:" + longId)),
                "maximum platform input identity survives typed encoding");
        check(
                "factory".equals(longSnapshot.get("native_icons").values().get("input:" + longId)),
                "maximum platform input artwork key retained");
        prefs(context, "native_icons").values.remove("input:" + longId);
        prefs(context, "native_icons").values.put("input:\n", "factory");
        rejects(
                () -> PresentationState.capture(context),
                "malformed input artwork identity rejected explicitly");
        prefs(context, "native_icons").values.remove("input:\n");
        prefs(context, "native_inputs").values.remove("hidden:" + longId);
        File oversized = new File(context.files, "oversized.png");
        try (RandomAccessFile file = new RandomAccessFile(oversized, "rw")) {
            file.setLength(20L * 1024 * 1024 + 1);
        }
        prefs(context, "source_appearance")
                .values
                .put("wallpaper_image_uri", Uri.fromFile(oversized).toString());
        rejects(
                () -> manager.save("Oversized", id),
                "20 MB single-media limit enforced before index replacement");
        check(
                manager.list().get(0).getString("name").equals("Evening renamed"),
                "failed media copy preserves prior setup");
        prefs(context, "source_appearance").values.clear();
        File capacity = new File(context.files, "saved-setups/capacity.image");
        try (RandomAccessFile file = new RandomAccessFile(capacity, "rw")) {
            file.setLength(96L * 1024 * 1024);
        }
        File tiny = new File(context.files, "tiny.png");
        Files.write(tiny.toPath(), new byte[] {1});
        prefs(context, "source_appearance")
                .values
                .put("wallpaper_image_uri", Uri.fromFile(tiny).toString());
        rejects(() -> manager.save("Full", id), "96 MB aggregate-media limit enforced");
        capacity.delete();
        final Context legacyContext = new Context(new File(args[0], "legacy-fixture"));
        File legacyDirectory = new File(legacyContext.files, "saved-setups");
        legacyDirectory.mkdirs();
        JSONObject original =
                new JSONObject()
                        .put("version", 1)
                        .put(
                                "setups",
                                new JSONArray()
                                        .put(
                                                new JSONObject()
                                                        .put("id", id)
                                                        .put("name", "Legacy scalar")
                                                        .put("savedAt", 1)
                                                        .put("settings", subset)));
        Files.write(
                new File(legacyDirectory, "setups.json").toPath(),
                original.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        SavedSetupManager legacyManager = new SavedSetupManager(legacyContext);
        check(legacyManager.list().size() == 1, "existing version 1 index reads without rewriting");
        legacyManager.apply(id, true);
        check(
                prefs(legacyContext, "native_app_grid").values.get("columns").equals(8),
                "existing version 1 setup applies reviewed mapped scalar");
        prefs(legacyContext, "source_accessibility").values.put("text_percent", 120);
        prefs(legacyContext, "source_accessibility").values.put("high_contrast", false);
        prefs(legacyContext, "source_accessibility").values.put("future_private", "kept");
        Map<String, PreferenceSnapshot> readable = PresentationState.capture(legacyContext);
        check(readable.get("source_accessibility").values().get("text_percent").equals(120)
                && readable.get("source_accessibility").values().get("high_contrast").equals(false)
                && !readable.get("source_accessibility").values().containsKey("future_private"),
                "readability capture retains exact explicit choices and excludes unrelated fields");
        Map<String, PreferenceSnapshot> olderPortable = new LinkedHashMap<>(readable);
        olderPortable.remove("source_accessibility");
        PresentationState.validatePortable(olderPortable);
        check(!olderPortable.containsKey("source_accessibility"), "older portable presentation validates without mutating its original store map");
        JSONObject olderNative = SavedSetupManager.encodeNative(readable);
        olderNative.remove("source_accessibility");
        Map<String, PreferenceSnapshot> olderDecoded = SavedSetupManager.decodeNative(olderNative);
        check(olderDecoded.get("source_accessibility").values().isEmpty(), "older native setup absence remains readable as absent optional readability fields");
        PresentationState.apply(legacyContext, olderDecoded, true);
        check(!prefs(legacyContext, "source_accessibility").values.containsKey("text_percent")
                && prefs(legacyContext, "source_accessibility").values.get("future_private").equals("kept"),
                "applying older setup removes owned readability choices while preserving unknown data");
        System.out.println("Saved setups: " + checks + " checks passed.");
    }
    private static void holidayPresets(File folder) throws Exception {
        Context context = new Context(folder);
        Context.Prefs appearance=prefs(context,"source_appearance"), focus=prefs(context,"native_focus"), packs=prefs(context,"native_icon_packs");
        appearance.values.put("scene","halloween"); appearance.values.put("wallpaper_image_uri","content://owner/image");
        appearance.values.put("base_speed",17); appearance.values.put("overlay_opacity",25);
        focus.values.put("growth_percent",6); focus.values.put("legacy_app_default",true); packs.values.put("package","original");
        for(com.jeremykenedy.firetv.ui.nativeapp.appearance.PaletteRecipes.Theme theme : com.jeremykenedy.firetv.ui.nativeapp.appearance.PaletteRecipes.ALL) {
            HolidayPresetTransaction.apply(context,theme.id,true,true,false);
            check(appearance.values.get("overlay").equals(theme.motif) && appearance.values.get("overlay_palette").equals(theme.id) && appearance.values.get("scene").equals("halloween"), "Each recipe applies its own overlay without changing the scene.");
        }
        HolidayPresetTransaction.apply(context,"eid_fitr",true,false,false);
        check(appearance.values.get("scene").equals("halloween") && appearance.values.get("overlay").equals("bokeh")
                && appearance.values.get("overlay_palette").equals("eid_fitr") && appearance.values.get("overlay_frozen").equals(true), "Overlay preset preserves scene and applies recipe, palette and still choice.");
        check(packs.values.get("package").equals("original") && !focus.values.containsKey("border_rgb") && Boolean.TRUE.equals(focus.values.get("legacy_app_default")), "Unselected icon and focus choices remain untouched.");
        HolidayPresetTransaction.apply(context,"hanukkah",false,true,true);
        check(appearance.values.get("scene").equals("") && appearance.values.get("base").equals("lanterns") && appearance.values.get("base_frozen").equals(false), "Background preset becomes visible and clears the selected scene.");
        check(packs.values.get("package").equals("builtin:holiday:hanukkah") && focus.values.get("border_enabled").equals(true) && focus.values.get("border_rgb").equals(0xacbfdc) && focus.values.get("fill_enabled").equals(false) && !focus.values.containsKey("legacy_app_default"), "Optional coordinated icons and focus are applied.");
        check(appearance.values.get("wallpaper_image_uri").equals("content://owner/image") && appearance.values.get("base_speed").equals(17) && focus.values.get("growth_percent").equals(6), "Unrelated choices are retained.");
        rejects(() -> HolidayPresetTransaction.apply(context,"unknown",true,true,true), "Unknown recipe rejected");
        for(String file:new String[]{"source_appearance","native_focus","native_icon_packs"}) {
            Map<String,Object> oldAppearance=new LinkedHashMap<>(appearance.values),oldFocus=new LinkedHashMap<>(focus.values),oldPacks=new LinkedHashMap<>(packs.values);
            prefs(context,file).failures=1;
            rejects(() -> HolidayPresetTransaction.apply(context,"diwali",true,true,true), "Failed preset write is reported");
            check(appearance.values.equals(oldAppearance)&&focus.values.equals(oldFocus)&&packs.values.equals(oldPacks)&&!HolidayPresetTransaction.hasRecovery(context), "Each failed commit restores all affected keys and absence.");
        }
        appearance.values.put("base",99L); appearance.values.remove("base_palette");
        AtomicFile.failDelete=true;
        rejects(() -> HolidayPresetTransaction.apply(context,"christmas",false,true,true), "Unresolved recovery is reported");
        check(HolidayPresetTransaction.hasRecovery(context), "Failed recovery cleanup keeps journal");
        rejects(() -> com.jeremykenedy.firetv.ui.nativeapp.appearance.AppearancePreferences.string(context,"base","stars"), "Pending recovery blocks ordinary appearance edits");
        rejects(() -> com.jeremykenedy.firetv.ui.nativeapp.appearance.AppearancePreferences.load(context), "Pending recovery blocks partial appearance reads");
        rejects(() -> PresentationState.capture(context), "Pending recovery blocks mixed setup capture");
        appearance.values.put("unrelated_fixture","keep"); AtomicFile.failDelete=false;
        Context restarted=new Context(folder);
        for(String file:new String[]{"source_appearance","native_focus","native_icon_packs"}) prefs(restarted,file).values.putAll(prefs(context,file).values);
        HolidayPresetTransaction.recover(restarted);
        check(prefs(restarted,"source_appearance").values.get("base").equals(99L) && !prefs(restarted,"source_appearance").values.containsKey("base_palette") && prefs(restarted,"source_appearance").values.get("unrelated_fixture").equals("keep"), "Reopened recovery restores exact original type and absence without touching unrelated keys.");
        check(!HolidayPresetTransaction.hasRecovery(restarted), "Verified recovery resolves journal");
        final java.util.concurrent.CountDownLatch start=new java.util.concurrent.CountDownLatch(1);
        final Throwable[] errors=new Throwable[2];
        Thread preset=new Thread(() -> { try { start.await(); HolidayPresetTransaction.apply(context,"diwali",false,true,true); } catch(Throwable failure) { errors[0]=failure; } });
        Thread edit=new Thread(() -> { try { start.await(); com.jeremykenedy.firetv.ui.nativeapp.appearance.AppearancePreferences.string(context,"base","stars"); } catch(Throwable failure) { errors[1]=failure; } });
        preset.start(); edit.start(); start.countDown(); preset.join(); edit.join();
        check(errors[0]==null&&errors[1]==null && (appearance.values.get("base").equals("stars") || appearance.values.get("base").equals("lanterns")), "Concurrent manual edit and preset serialize without a lost or malformed result.");
        check(appearance.values.get("base_palette").equals("diwali") && packs.values.get("package").equals("builtin:holiday:diwali") && !HolidayPresetTransaction.hasRecovery(context), "Concurrent completion leaves a complete verified preset and no journal.");
        for(String pending:new String[]{"saved-setup-apply-v1.json","portable-household-import-v1.snapshot","presentation-migration-v1.snapshot"}) {
            File journal=new File(context.getNoBackupFilesDir(),pending); journal.getParentFile().mkdirs(); Files.write(journal.toPath(),new byte[]{1});
            Map<String,Object> old=new LinkedHashMap<>(appearance.values);
            rejects(() -> HolidayPresetTransaction.apply(context,"diwali",true,true,true), "Other pending recovery blocks preset");
            check(appearance.values.equals(old), "Blocked preset preserves appearance");
            Files.delete(journal.toPath());
        }
        File corrupt=new File(context.getNoBackupFilesDir(),"holiday-preset-v1.snapshot");
        Files.write(corrupt.toPath(),new byte[]{1,2,3});
        Map<String,Object> beforeCorrupt=new LinkedHashMap<>(appearance.values);
        rejects(() -> HolidayPresetTransaction.recover(context), "Corrupt recovery cannot be applied");
        rejects(() -> com.jeremykenedy.firetv.ui.nativeapp.appearance.AppearancePreferences.string(context,"base","stars"), "Corrupt recovery blocks writes");
        check(appearance.values.equals(beforeCorrupt) && HolidayPresetTransaction.hasRecovery(context), "Failed recovery keeps journal and does not write affected values");
        Files.delete(corrupt.toPath());
        for(int variant=0;variant<4;variant++) {
            Map<String,Object> malformed=new LinkedHashMap<>();
            malformed.put("present/source_appearance/base",true);
            malformed.put("value/source_appearance/base","aurora");
            if(variant==0)malformed.put("present/native_focus/border_rgb","true");
            if(variant==1)malformed.put("value/native_focus/border_rgb",123);
            if(variant==2){malformed.put("present/native_focus/border_rgb",false);malformed.put("value/native_focus/border_rgb",123);}
            if(variant==3)malformed.put("unknown/source_appearance/base",true);
            byte[] content=new PreferenceSnapshot(malformed).encode();
            byte[] checksum=java.security.MessageDigest.getInstance("SHA-256").digest(content);
            try(FileOutputStream output=new FileOutputStream(corrupt)){output.write(content);output.write(checksum);}
            Map<String,Object> priorAppearance=new LinkedHashMap<>(appearance.values),priorFocus=new LinkedHashMap<>(focus.values);
            rejects(() -> HolidayPresetTransaction.recover(context), "Mixed malformed recovery rejected");
            check(appearance.values.equals(priorAppearance)&&focus.values.equals(priorFocus)&&HolidayPresetTransaction.hasRecovery(context), "Malformed recovery retains all values and journal without partial restore");
            Files.delete(corrupt.toPath());
        }
    }
}
