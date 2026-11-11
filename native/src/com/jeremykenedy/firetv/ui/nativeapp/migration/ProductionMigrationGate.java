package com.jeremykenedy.firetv.ui.nativeapp.migration;

import com.jeremykenedy.firetv.ui.nativeapp.device.protection.LegacyDeviceProtection;
import com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroupPresentationPlan;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

/** Validates and commits only completely mapped saved settings. */
public final class ProductionMigrationGate {
    private static final String COMPLETE = "source_ui_strict_migration_version";
    private ProductionMigrationGate() {}

    static WidgetGroupPresentationPlan preflight(PresentationSnapshot before, PresentationMappingContext identities,
            LegacyFolderMigration.Plan folders) throws IOException {
        PreferenceSnapshot legacy = before.stores.get(LauncherMigration.PREFERENCES);
        if (legacy.values().containsKey(LegacyPresentationMapper.VERSION_KEY))
            throw new IOException("An earlier partial presentation migration requires recovery before startup.");
        Map<String, PreferenceSnapshot> nativeBefore = new LinkedHashMap<>(before.stores);
        nativeBefore.remove(LauncherMigration.PREFERENCES);
        try {
            LegacyPresentationMapper.Plan plan = LegacyPresentationMapper.plan(legacy, identities, nativeBefore, folders, true, true);
            WidgetGroupPresentationPlan prepared = WidgetGroupPresentationPlan.prepare(plan);
            Set<String> unresolved = new LinkedHashSet<>(prepared.unmappedLegacyKeys);
            LegacyDeviceProtection.validateMigration(legacy.values());
            for (String key : new String[]{"fire_tv_ui_protect_settings", "fire_tv_ui_keep_adb",
                    "fire_tv_ui_deep_sleep_enabled", "fire_tv_ui_dream_unlocked", "fire_tv_ui_preview_pending",
                    "fire_tv_ui_dream_enabled", "fire_tv_ui_sleep", "fire_tv_ui_sleep_saved",
                    "fire_tv_ui_auto_wake", "fire_tv_ui_idle", "fire_tv_ui_dream"}) unresolved.remove(key);
            for (String key : new String[]{"fire_tv_ui_home_enabled", "start_on_boot", "override_home_button", "better_home_detection"}) {
                if (legacy.values().get(key) instanceof Boolean) unresolved.remove(key);
            }
            unresolved.removeAll(LegacyPresentationMapper.directlyConsumedPresentationKeys(legacy.values()));
            if (legacy.values().get("source_status_bar_off") instanceof Boolean) unresolved.remove("source_status_bar_off");
            // The maintained legacy launcher forces this compatibility flag on; the source UI
            // has no premium gate. Keep its original typed value for recovery.
            if (Boolean.TRUE.equals(legacy.values().get("is_premium"))) unresolved.remove("is_premium");
            for (String key : new String[]{"fire_tv_ui_block_updates", "fire_tv_ui_block_appstore"})
                if (Boolean.FALSE.equals(legacy.values().get(key))) unresolved.remove(key);
            if (Boolean.TRUE.equals(legacy.values().get("fire_tv_ui_block_appstore"))
                    && (!legacy.values().containsKey("fire_tv_ui_block_updates")
                    || Boolean.FALSE.equals(legacy.values().get("fire_tv_ui_block_updates"))))
                unresolved.remove("fire_tv_ui_block_appstore");
            // The maintained legacy initializer removes both obsolete rating counters.
            unresolved.remove("app_launch_count");
            unresolved.remove("rate_app_shown");
            if (!unresolved.isEmpty())
                throw new IOException("Some saved settings do not yet have a verified migration. Original settings were kept.");
            return prepared;
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Saved settings could not be migrated safely. Original settings were kept.", invalid);
        }
    }

    static boolean completed(PresentationSnapshot snapshot) throws IOException {
        Object value = snapshot.stores.get(LauncherMigration.PREFERENCES).values().get(COMPLETE);
        if (value == null) return false;
        if (!Integer.valueOf(1).equals(value)) throw new IOException("Unsupported completed migration version.");
        return true;
    }

    static void commit(PresentationUpgrade.Store store, PresentationSnapshot before,
            PresentationMappingContext identities, LegacyFolderMigration.Plan folders) throws IOException {
        commit(store, before, preflight(before, identities, folders));
    }

    static void commit(PresentationUpgrade.Store store, PresentationSnapshot before,
            WidgetGroupPresentationPlan plan) throws IOException {
        if (!plan.before.sameValues(before)) throw new IOException("Prepared settings no longer match the original state.");
        Map<String, PreferenceSnapshot> after = new LinkedHashMap<>(plan.target.stores);
        Map<String, Object> legacy = new LinkedHashMap<>(after.get(LauncherMigration.PREFERENCES).values());
        legacy.put(COMPLETE, 1);
        after.put(LauncherMigration.PREFERENCES, new PreferenceSnapshot(legacy));
        PresentationSnapshot target = new PresentationSnapshot(after);
        PresentationTransaction.apply(store, before, target);
        store.resolveRecovery(before, target);
    }

    static void markFresh(PresentationUpgrade.Store store, PresentationSnapshot before) throws IOException {
        for (PreferenceSnapshot values : before.stores.values())
            if (!values.values().isEmpty()) throw new IOException("Fresh installation proof conflicts with existing settings.");
        Map<String, PreferenceSnapshot> after = new LinkedHashMap<>(before.stores);
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put(COMPLETE, 1);
        after.put(LauncherMigration.PREFERENCES, new PreferenceSnapshot(legacy));
        PresentationSnapshot target = new PresentationSnapshot(after);
        PresentationTransaction.apply(store, before, target);
        store.resolveRecovery(before, target);
    }
}
