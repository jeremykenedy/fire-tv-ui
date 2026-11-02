package com.jeremykenedy.firetv.ui.nativeapp.migration;

import android.content.Context;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Coordinates bounded migration before any preference consumer or writer starts. */
public final class AndroidProductionMigration {
    private AndroidProductionMigration() {}

    public static void apply(Context context) throws IOException, GeneralSecurityException {
        apply(context, false);
    }

    /** Fresh-install proof must be established independently before any store is opened. */
    public static void apply(Context context, boolean verifiedFreshInstallation) throws IOException, GeneralSecurityException {
        AndroidPresentationStore store = new AndroidPresentationStore(context);
        synchronized (store.lock()) {
            if (store.hasRecovery()) {
                PresentationSnapshot journal = store.readRecovery();
                PresentationTransaction.recover(store, journal);
                store.resolveRecovery(journal, journal);
            }
            PresentationSnapshot before = store.read();
            if (ProductionMigrationGate.completed(before)) return;
            PreferenceSnapshot legacy = before.stores.get(LauncherMigration.PREFERENCES);
            if (legacy.values().isEmpty()) {
                if (verifiedFreshInstallation) {
                    ProductionMigrationGate.markFresh(store, before);
                    return;
                }
                throw new IOException("Legacy installation state is not established. Existing data was kept.");
            }
            PresentationMappingContext inventory = AndroidPresentationInventory.read(context, legacy);
            Map<String, List<String>> packages = new LinkedHashMap<>();
            for (String component : AndroidPresentationInventory.launchActivities(context)) {
                String name = component.substring(0, component.indexOf('/'));
                if (!packages.containsKey(name)) packages.put(name, new ArrayList<String>());
                packages.get(name).add(component);
            }
            LegacyFolderMigration.Plan folders = legacy.values().containsKey("folders")
                    ? LegacyFolderMigration.decode(legacy, packages) : null;
            Set<String> references = iconReferences(legacy);
            Map<String, String> placeholders = new LinkedHashMap<>();
            for (String uri : references) placeholders.put(uri, "image:00000000-0000-0000-0000-000000000000.png");
            ProductionMigrationGate.preflight(before, withIcons(inventory, placeholders), folders);
            Map<String, String> icons = AndroidLegacyIconMigration.prepare(context, references);
            PresentationMappingContext identities = withIcons(inventory, icons);
            ProductionMigrationGate.preflight(before, identities, folders);
            if (folders != null) AndroidLegacyFolderMigration.transferPrivatePin(context, legacy, packages);
            ProductionMigrationGate.commit(store, before, identities, folders);
        }
    }

    private static PresentationMappingContext withIcons(PresentationMappingContext source, Map<String, String> icons) {
        return new PresentationMappingContext(source.components, source.feeds, source.items, icons, source.inputs,
                source.advertisedIconPacks, source.privateMedia.values());
    }

    private static Set<String> iconReferences(PreferenceSnapshot legacy) throws IOException {
        Object raw = legacy.values().get("app_visibility");
        if (raw == null) return Collections.emptySet();
        if (!(raw instanceof String)) throw new IOException("Invalid saved app visibility.");
        Set<String> result = new LinkedHashSet<>();
        try {
            for (Object value : LegacyJson.object((String) raw).values()) {
                if (!(value instanceof Map)) throw new IllegalArgumentException();
                Object icon = ((Map<?, ?>) value).get("customIconUri");
                if (icon != null) {
                    if (!(icon instanceof String) || ((String) icon).isEmpty()) throw new IllegalArgumentException();
                    result.add((String) icon);
                }
            }
        } catch (IllegalArgumentException invalid) { throw new IOException("Invalid saved icon references.", invalid); }
        return result;
    }
}
