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

    private static void widgetGroupWrites(File folder) throws Exception {
        final java.util.concurrent.CountDownLatch verifying = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        Context context = new Context(folder) {
            @Override public android.content.ContentResolver getContentResolver() {
                return new android.content.ContentResolver() {
                    @Override public InputStream openInputStream(Uri uri) throws IOException {
                        if (Thread.currentThread().getName().equals("group-setup-apply")) {
                            verifying.countDown();
                            try {
                                if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IOException("Fixture timeout");
                            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
                        }
                        return super.openInputStream(uri);
                    }
                };
            }
        };
        File image = new File(folder, "wallpaper.png");
        Files.write(image.toPath(), new byte[] {1, 2, 3});
        prefs(context, "source_appearance").values.put("wallpaper_image_uri", Uri.fromFile(image).toString());
        prefs(context, "source_appearance").values.put("wallpaper_mode", "image");
        SavedSetupManager manager = new SavedSetupManager(context);
        String id = manager.save("Older native setup", null);
        File index = new File(folder, "saved-setups/setups.json");
        JSONObject saved = new JSONObject(new String(Files.readAllBytes(index.toPath()), java.nio.charset.StandardCharsets.UTF_8));
        saved.getJSONArray("setups").getJSONObject(0).remove("nativeVersion");
        Files.write(index.toPath(), saved.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String original = "[[{\"id\":\"11111111-1111-1111-1111-111111111111\",\"type\":\"DigitalClock\",\"size\":\"Big\",\"data\":{}}]]";
        prefs(context, "native_layout").values.put("widget_groups", original);
        com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroupsStore store = new com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroupsStore(context);
        com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroups edited = store.load().appendEmptyGroup();
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Boolean> committed = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.CountDownLatch editStarted = new java.util.concurrent.CountDownLatch(1);
        Thread apply = new Thread(() -> { try { manager.apply(id, true); } catch (Throwable error) { failure.set(error); } }, "group-setup-apply");
        Thread edit = new Thread(() -> { editStarted.countDown(); try { committed.set(store.save(original, edited)); } catch (Throwable error) { failure.set(error); } }, "group-edit");
        apply.start();
        try {
            check(verifying.await(5, java.util.concurrent.TimeUnit.SECONDS), "Setup reaches media validation after preserving old-setup groups.");
            edit.start();
            check(editStarted.await(2, java.util.concurrent.TimeUnit.SECONDS), "Concurrent widget edit starts.");
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
            while (edit.isAlive() && edit.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) Thread.sleep(1);
            check(edit.getState() == Thread.State.BLOCKED, "Widget edit waits while setup validation and application are in progress.");
        } finally { release.countDown(); }
        apply.join(5000); edit.join(5000);
        check(!apply.isAlive() && !edit.isAlive() && failure.get() == null, "Concurrent setup and widget edit complete without failure.");
        check(Boolean.TRUE.equals(committed.get()) && edited.encode().equals(store.raw()), "An acknowledged widget edit survives older-setup application.");
        Map<String, PreferenceSnapshot> before = PresentationState.capture(context);
        Map<String, PreferenceSnapshot> target = new LinkedHashMap<>(before);
        Map<String, Object> layout = new LinkedHashMap<>(before.get("native_layout").values());
        layout.put("widget_groups", original);
        target.put("native_layout", new PreferenceSnapshot(layout));
        AtomicFile.failDelete = true;
        try { rejects(() -> PresentationState.apply(context, target, true), "Interrupted cleanup retains recovery."); }
        finally { AtomicFile.failDelete = false; }
        check(PresentationState.hasRecovery(context), "Private setup recovery remains pending.");
        rejects(() -> store.save(original, edited), "Pending recovery blocks widget edits.");
        check(original.equals(store.raw()), "Rejected widget edit leaves current groups intact.");
        PresentationState.recover(context);
        check(same(before, PresentationState.capture(context)), "Recovery restores the original groups and presentation without losing an acknowledged edit.");
    }

    public static void main(String[] args) throws Exception {
        widgetGroupWrites(new File(args[0], "group-writer-fixture"));
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
        byte[] exportedGroups = PortableBackup.encode(PresentationState.capturePortable(groupContext), portable);
        check(grouped.equals(PortableBackup.decode(exportedGroups, portable).get("native_layout").values().get("widget_groups")),
                "Portable export includes exact supported widget layout.");
        prefs(groupContext, "native_layout").values.put("widget_groups", "[]");
        PresentationState.applyPortable(groupContext, PortableBackup.decode(exportedGroups, portable));
        check(grouped.equals(prefs(groupContext, "native_layout").values.get("widget_groups")),
                "Portable import restores exported groups over destination layout.");
        prefs(groupContext, "launcher_prefs").values.put("widgets_config", grouped);
        byte[] migratedGroups = PortableBackup.encode(PresentationState.capturePortable(groupContext), portable);
        check(grouped.equals(PortableBackup.decode(migratedGroups, portable).get("launcher_prefs").values().get("widgets_config")),
                "Portable backup retains supported original widget records after migration.");
        prefs(groupContext, "launcher_prefs").values.remove("widgets_config");
        byte[] oldBackup = PortableBackup.encode(oldPortable, portable);
        PresentationState.applyPortable(groupContext, PortableBackup.decode(oldBackup, portable));
        check(grouped.equals(prefs(groupContext, "native_layout").values.get("widget_groups")),
                "Older portable backup preserves groups it could not capture.");
        Context upgradedBackup = new Context(new File(args[0], "upgraded-portable"));
        String historicalIcons = "{ \n \"fixture.player/.Main\": { \"customIconUri\": \"content://fixture.invalid/previous-image\" } }";
        prefs(upgradedBackup, "launcher_prefs").values.put("app_visibility", historicalIcons);
        prefs(upgradedBackup, "launcher_prefs").values.put("widgets_config", grouped);
        prefs(upgradedBackup, "launcher_prefs").values.put("source_ui_strict_migration_version", 1);
        prefs(upgradedBackup, "launcher_prefs").values.put("use_24h_clock", true);
        prefs(upgradedBackup, "native_icons").values.put("fixture.player/fixture.player.Main", "catalog:sony");
        prefs(upgradedBackup, "native_layout").values.put("widget_groups", grouped);
        Map<String, PreferenceSnapshot> currentPortable = PresentationState.capturePortable(upgradedBackup);
        check(!currentPortable.get("launcher_prefs").values().containsKey("app_visibility")
                && !currentPortable.get("launcher_prefs").values().containsKey("widgets_config")
                && Boolean.TRUE.equals(currentPortable.get("launcher_prefs").values().get("use_24h_clock")),
                "Completed migration exports current native choices and keeps direct legacy consumers.");
        PresentationState.applyPortable(upgradedBackup, PortableBackup.decode(PortableBackup.encode(currentPortable, portable), portable));
        check(historicalIcons.equals(prefs(upgradedBackup, "launcher_prefs").values.get("app_visibility"))
                && grouped.equals(prefs(upgradedBackup, "launcher_prefs").values.get("widgets_config")),
                "Portable round trip preserves original migration records on this TV.");
        SavedSetupManager upgradedSetups = new SavedSetupManager(upgradedBackup);
        Map<String, Object> originals = new LinkedHashMap<>(prefs(upgradedBackup, "launcher_prefs").values);
        String themeSetup = upgradedSetups.save("Current builtin", null);
        prefs(upgradedBackup, "native_icons").values.put("fixture.player/fixture.player.Main", "factory");
        upgradedSetups.apply(themeSetup, true);
        check("catalog:sony".equals(prefs(upgradedBackup, "native_icons").values.get("fixture.player/fixture.player.Main"))
                && originals.equals(prefs(upgradedBackup, "launcher_prefs").values),
                "Current setup restores builtin choice while retaining historical originals without accessing missing media.");
        prefs(upgradedBackup, "native_icons").values.put("fixture.player/fixture.player.Main", "factory");
        upgradedSetups.apply(themeSetup, false);
        check("factory".equals(prefs(upgradedBackup, "native_icons").values.get("fixture.player/fixture.player.Main"))
                && originals.equals(prefs(upgradedBackup, "launcher_prefs").values),
                "Artwork unchecked preserves exact current icon and historical JSON text.");
        String currentImage = "image:99999999-9999-9999-9999-999999999999.png";
        File currentIcon = new File(upgradedBackup.files, "icons/" + currentImage.substring(6));
        currentIcon.getParentFile().mkdirs();
        Files.write(currentIcon.toPath(), new byte[]{3, 7, 9});
        prefs(upgradedBackup, "native_icons").values.put("fixture.player/fixture.player.Main", currentImage);
        Map<String,Object> currentPending = new LinkedHashMap<>();
        currentPending.put("icon:fixture.pending",currentImage);
        prefs(upgradedBackup, "native_icons").values.put(com.jeremykenedy.firetv.ui.nativeapp.appgrid.RetainedHomeChoices.KEY,
                com.jeremykenedy.firetv.ui.nativeapp.appgrid.RetainedHomeChoices.write("native_icons",currentPending));
        String nativeImageSetup = upgradedSetups.save("Current personal", null);
        currentIcon.delete();
        prefs(upgradedBackup, "native_icons").values.clear();
        upgradedSetups.apply(nativeImageSetup, true);
        check(currentImage.equals(prefs(upgradedBackup, "native_icons").values.get("fixture.player/fixture.player.Main"))
                && Arrays.equals(new byte[]{3, 7, 9},Files.readAllBytes(currentIcon.toPath()))
                && originals.equals(prefs(upgradedBackup, "launcher_prefs").values),
                "New current snapshot retains active artwork and reapplies its private copy after managed file deletion.");
        check(currentPending.equals(com.jeremykenedy.firetv.ui.nativeapp.appgrid.RetainedHomeChoices.read("native_icons",
                prefs(upgradedBackup, "native_icons").values.get(com.jeremykenedy.firetv.ui.nativeapp.appgrid.RetainedHomeChoices.KEY))),
                "Current snapshot retains exact pending unavailable-app artwork.");
        prefs(upgradedBackup, "launcher_prefs").values.put("source_ui_strict_migration_version", 2);
        rejects(() -> upgradedSetups.save("Unqualified migration", null), "Unknown migration version never skips active legacy image retention.");
        prefs(upgradedBackup, "launcher_prefs").values.remove("source_ui_strict_migration_version");
        rejects(() -> PresentationState.capturePortable(upgradedBackup), "Unverified legacy data is never silently omitted.");
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
                        .put("values", new JSONObject().put("use_24h_clock", false).put("poster_mode", "true")))) {
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
        originalBackup(new File(args[0],"original-backup"));
        scopedLegacyIcons(new File(args[0],"scoped-legacy-icons"));
        System.out.println("Saved setups: " + checks + " checks passed.");
    }
    private static void scopedLegacyIcons(File folder) throws Exception {
        Context context=new Context(folder);SavedSetupManager manager=new SavedSetupManager(context);
        String included="com.example.included/com.example.included.Main",other="com.example.other/com.example.other.Main";
        String missing="com.example.missing",absent=missing+"/"+missing+".Main",uri="file:/retained-fixture.image";
        Map<String,Object> pending=new LinkedHashMap<>();pending.put("icon:"+missing,"factory");pending.put("icon:com.example.later","factory");
        prefs(context,"native_icons").values.put("retained_choices",com.jeremykenedy.firetv.ui.nativeapp.appgrid.RetainedHomeChoices.write("native_icons",pending));
        prefs(context,"native_icons").values.put("edited:"+included,true);prefs(context,"native_icons").values.put("edited:"+absent,true);
        prefs(context,"native_icons").values.put(other,"catalog:sonos");prefs(context,"native_icons").values.put("edited:"+other,true);
        Map<String,String> components=new LinkedHashMap<>();components.put("com.example.included",included);components.put("com.example.other",other);
        com.jeremykenedy.firetv.ui.nativeapp.migration.PresentationMappingContext identities=new com.jeremykenedy.firetv.ui.nativeapp.migration.PresentationMappingContext(components,Collections.emptyMap(),Collections.emptyMap(),Collections.singletonMap(uri,"catalog:jbl"));
        String visibility=new JSONObject().put("com.example.included",new JSONObject().put("customIconUri",uri)).put(missing,new JSONObject().put("customIconUri",uri)).toString();
        JSONObject settings=new JSONObject().put("keys",new JSONArray(Arrays.asList("app_visibility"))).put("values",new JSONObject().put("app_visibility",visibility));
        Map<String,Object> before=new LinkedHashMap<>(prefs(context,"native_icons").values);
        Map<String,Object> restored=manager.prepareLegacy(settings,identities,true).get("native_icons").values();
        check("catalog:jbl".equals(restored.get(included))&&!restored.containsKey("edited:"+included),"Legacy captured artwork replaces current Theme and its edit marker.");
        check("catalog:sonos".equals(restored.get(other))&&Boolean.TRUE.equals(restored.get("edited:"+other)),"Legacy artwork restore preserves unrelated resolved choice and marker.");
        Map<String,Object> retained=com.jeremykenedy.firetv.ui.nativeapp.appgrid.RetainedHomeChoices.read("native_icons",restored.get("retained_choices"));
        check("catalog:jbl".equals(retained.get("icon:"+missing))&&"factory".equals(retained.get("icon:com.example.later")),"Legacy pending artwork merges by included identity.");
        check(!restored.containsKey("edited:"+absent),"Captured unavailable app clears its prior Theme reset marker.");
        check("catalog:jbl".equals(com.jeremykenedy.firetv.ui.nativeapp.appgrid.RetainedHomeChoices.promoteIcons(restored,Collections.singletonMap(missing,absent)).get(absent)),"Captured unavailable artwork promotes after restoration.");
        check(before.equals(manager.prepareLegacy(settings,identities,false).get("native_icons").values()),"Excluding artwork preserves every current native icon choice.");
        settings.put("values",new JSONObject().put("app_visibility",new JSONObject().put("com.example.included",new JSONObject()).toString()));
        prefs(context,"native_icons").values.put(included,"catalog:pc");
        restored=manager.prepareLegacy(settings,identities,true).get("native_icons").values();
        check(!restored.containsKey(included)&&!restored.containsKey("edited:"+included)&&"catalog:sonos".equals(restored.get(other)),"Captured default Theme clears only its included identity.");
        JSONObject bad=new JSONObject().put("keys",new JSONArray(Arrays.asList("app_visibility")))
                .put("values",new JSONObject().put("app_visibility",new JSONObject().put("com.example.included",new JSONObject().put("customIconUri","file:/")).toString()));
        File directory=new File(context.getFilesDir(),"saved-setups");directory.mkdirs();
        Files.write(new File(directory,"setups.json").toPath(),new JSONObject().put("version",1).put("setups",new JSONArray().put(new JSONObject()
                .put("id","11111111-1111-1111-1111-111111111111").put("name","Malformed reference").put("savedAt",1).put("settings",bad))).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Map<String,PreferenceSnapshot> beforeBad=PresentationState.capture(context);
        try{manager.apply("11111111-1111-1111-1111-111111111111",true);throw new AssertionError("Root image URI accepted");}
        catch(IOException expected){check(expected.getMessage().contains("unsupported retained image reference"),"Root image URI reports the explicit unsupported-reference error.");}
        check(same(beforeBad,PresentationState.capture(context)),"Malformed archived reference changes no preferences.");
    }

    private static void originalBackup(File folder) throws Exception {
        Context context = new Context(folder);
        SavedSetupManager manager = new SavedSetupManager(context);
        prefs(context,"launcher_prefs").values.put("use_24h_clock",false);
        prefs(context,"launcher_prefs").values.put("color_back_color_argb",123);
        prefs(context,"launcher_prefs").values.put("folder_lock_pin","local-lock-fixture");
        prefs(context,"native_app_grid").values.put("columns",7);
        Map<String,PreferenceSnapshot> before = PresentationState.capture(context);
        Map<String,Object> incoming = new LinkedHashMap<>(); incoming.put("use_24h_clock",true);
        Map<String,PreferenceSnapshot> target = manager.prepareOriginalBackup(new PreferenceSnapshot(incoming));
        check(same(before,PresentationState.capture(context)),"Original review never writes current settings.");
        check(Boolean.TRUE.equals(target.get("launcher_prefs").values().get("use_24h_clock")),"Original included direct choice prepared.");
        check(Integer.valueOf(123).equals(target.get("launcher_prefs").values().get("color_back_color_argb")),"Original omitted local color retained.");
        check(Integer.valueOf(7).equals(target.get("native_app_grid").values().get("columns")),"Original omitted grid retained.");
        PresentationState.apply(context,target,true);
        check("local-lock-fixture".equals(prefs(context,"launcher_prefs").values.get("folder_lock_pin")),"Original apply preserves local lock.");
        incoming.clear(); incoming.put("overlay_mode","none");
        target = manager.prepareOriginalBackup(new PreferenceSnapshot(incoming));
        check("none".equals(target.get("launcher_prefs").values().get("overlay_mode")),"Original inactive overlay retained.");
        incoming.put("overlay_mode","auto");
        rejects(() -> manager.prepareOriginalBackup(new PreferenceSnapshot(incoming)),"Original active overlay refused.");
        String clock = "[[{\"id\":\"11111111-1111-1111-1111-111111111111\",\"type\":\"DigitalClock\",\"size\":\"Big\",\"data\":{}}]]";
        prefs(context,"native_layout").values.put("hidden","widget:world-clocks");
        prefs(context,"native_layout").values.put("card_width_dp",400);
        incoming.clear(); incoming.put("widgets_config",clock);incoming.put("enable_widgets",true);incoming.put("hide_watch_next",false);
        target=manager.prepareOriginalBackup(new PreferenceSnapshot(incoming));
        check(clock.equals(target.get("native_layout").values().get("widget_groups")),"Original enabled grouping prepared exactly.");
        check("WIDGETS_ONLY".equals(target.get("native_layout").values().get("mode")),"Original enabled groups select original widget row mode.");
        check(((String)target.get("native_layout").values().get("hidden")).contains("widget:world-clocks"),"Original group merge retains unrelated hidden row.");
        check(Integer.valueOf(400).equals(target.get("native_layout").values().get("card_width_dp")),"Original grouping retains omitted card width.");
        prefs(context,"native_layout").values.put("widget_groups",clock);
        incoming.remove("widgets_config");
        target=manager.prepareOriginalBackup(new PreferenceSnapshot(incoming));
        check(clock.equals(target.get("native_layout").values().get("widget_groups")),"Original enable choice retains omitted existing grouping.");
        String weather="[[{\"id\":\"11111111-1111-1111-1111-111111111111\",\"type\":\"Weather\",\"size\":\"Big\",\"data\":{\"location\":\"Portland\",\"unit\":\"F\"}}]]";
        incoming.put("widgets_config",weather);incoming.put("enable_widgets",false);
        target=manager.prepareOriginalBackup(new PreferenceSnapshot(incoming));
        check(weather.equals(target.get("native_layout").values().get("widget_groups")),"Original disabled weather city and unit retained byte for byte.");
        check(weather.equals(target.get("launcher_prefs").values().get("widgets_config")),"Original weather source remains recoverable.");
        incoming.put("enable_widgets",true);
        rejects(() -> manager.prepareOriginalBackup(new PreferenceSnapshot(incoming)),"Original active populated weather stays gated.");
        incoming.remove("enable_widgets"); incoming.remove("hide_watch_next");
        prefs(context,"native_layout").values.put("mode","WIDGETS_ONLY");
        rejects(() -> manager.prepareOriginalBackup(new PreferenceSnapshot(incoming)),"Original partial weather cannot bypass current active widget gate.");

        incoming.clear(); incoming.put("hide_watch_next",false);
        target=manager.prepareOriginalBackup(new PreferenceSnapshot(incoming));
        check("WIDGETS_ONLY".equals(target.get("native_layout").values().get("mode")),"Original partial hide choice retains current enabled widget semantics.");
        check(clock.equals(target.get("native_layout").values().get("widget_groups")),"Original partial hide choice preserves omitted grouping.");
        incoming.clear(); incoming.put("fire_tv_ui_sleep",60);
        rejects(() -> manager.prepareOriginalBackup(new PreferenceSnapshot(incoming)),"Original unsupported device key refused.");
        incoming.clear(); incoming.put("custom_wallpaper_uri","file:/unselected/private-fixture.png");
        rejects(() -> manager.prepareOriginalBackup(new PreferenceSnapshot(incoming)),"Original unselected personal URI refused before opening.");
        incoming.clear(); incoming.put("app_visibility","{\"com.example.fixture\":{\"customIconUri\":\"content://unselected/icon\"}}");
        rejects(() -> manager.prepareOriginalBackup(new PreferenceSnapshot(incoming)),"Original new icon grant refused.");
        Map<String,Object> retained = new LinkedHashMap<>();retained.put("icon:com.example.unavailable","theme");
        String pending=com.jeremykenedy.firetv.ui.nativeapp.appgrid.RetainedHomeChoices.write("native_icons",retained);
        prefs(context,"native_icons").values.put("retained_choices",pending);
        prefs(context,"native_icons").values.put("edited:com.example.other/com.example.other.Main",true);
        incoming.clear();incoming.put("use_24h_clock",false);
        target=manager.prepareOriginalBackup(new PreferenceSnapshot(incoming));
        check(pending.equals(target.get("native_icons").values().get("retained_choices")),"Original review preserves deferred icon metadata.");
        String metadata=manager.save("Icon metadata",null);
        manager.apply(metadata,true);
        check(pending.equals(prefs(context,"native_icons").values.get("retained_choices")),"Named setup captures and restores deferred icon metadata.");
        check(Boolean.TRUE.equals(prefs(context,"native_icons").values.get("edited:com.example.other/com.example.other.Main")),"Named setup preserves explicit icon edit marker without casting it as artwork.");
        String selected="image:11111111-1111-1111-1111-111111111111.png";
        File managed=new File(new File(context.getFilesDir(),"icons"),selected.substring(6));managed.getParentFile().mkdirs();
        Files.write(managed.toPath(),new byte[]{1});
        prefs(context,"native_icons").values.put("com.example.installed/com.example.installed.Main",selected);
        String historical="{\"com.example.installed\":{\"customIconUri\":\"file:/missing/historical.png\"}}";
        prefs(context,"launcher_prefs").values.put("app_visibility",historical);
        incoming.clear();incoming.put("apps_per_row",4);
        target=manager.prepareOriginalBackup(new PreferenceSnapshot(incoming));
        check(historical.equals(target.get("launcher_prefs").values().get("app_visibility")),"Original partial import retains omitted historical source without opening it.");
        check(selected.equals(target.get("native_icons").values().get("com.example.installed/com.example.installed.Main")),"Original partial import retains current managed icon after source removal.");
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
