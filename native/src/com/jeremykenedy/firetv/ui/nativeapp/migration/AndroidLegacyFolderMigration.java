package com.jeremykenedy.firetv.ui.nativeapp.migration;

import android.content.Context;
import com.jeremykenedy.firetv.ui.nativeapp.locks.LockStore;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/** Explicit private transfer step. Does not publish folders, erase source values or run at startup. */
public final class AndroidLegacyFolderMigration {
    private AndroidLegacyFolderMigration() {}

    /** Before any ordinary writer, recover, validate, retain existing locks, then publish folders. */
    public static PresentationUpgrade.Result apply(Context context, PresentationMappingContext identities)
            throws IOException, GeneralSecurityException {
        AndroidPresentationStore store = new AndroidPresentationStore(context);
        synchronized (store.lock()) {
            boolean recovered = false;
            if (store.hasRecovery()) {
                PresentationSnapshot original = store.readRecovery();
                PresentationTransaction.recover(store, original);
                store.resolveRecovery(original, original);
                recovered = true;
            }
            PresentationSnapshot before = store.read();
            PreferenceSnapshot source = before.stores.get(LauncherMigration.PREFERENCES);
            if (source.values().containsKey("folders") && Integer.valueOf(1).equals(source.values().get(LegacyPresentationMapper.VERSION_KEY))
                    && !Integer.valueOf(1).equals(before.stores.get(LegacyPresentationMapper.FOLDERS).values().get("legacy_migration_version")))
                throw new IOException("Earlier presentation mapping did not publish the saved folders. Original data was kept.");
            if (!source.values().containsKey("folders") || Integer.valueOf(1).equals(source.values().get(LegacyPresentationMapper.VERSION_KEY)))
                return withRecovery(PresentationUpgrade.apply(store, identities), recovered);
            Map<String, List<String>> packages = new LinkedHashMap<>();
            for (String component : AndroidPresentationInventory.launchActivities(context)) {
                String name = component.substring(0, component.indexOf('/'));
                if (!packages.containsKey(name)) packages.put(name, new ArrayList<String>());
                packages.get(name).add(component);
            }
            LegacyFolderMigration.Plan plan = LegacyFolderMigration.decode(source, packages);
            Map<String, PreferenceSnapshot> nativeBefore = new LinkedHashMap<>(before.stores);
            nativeBefore.remove(LauncherMigration.PREFERENCES);
            // Validate every presentation target and conflict before importing any existing lock.
            LegacyPresentationMapper.plan(source, identities, nativeBefore, plan, true);
            transferPrivatePin(context, source, packages);
            return withRecovery(PresentationUpgrade.apply(store, identities, plan, true), recovered);
        }
    }

    private static PresentationUpgrade.Result withRecovery(PresentationUpgrade.Result result, boolean recovered) {
        return recovered ? result.withRecovery() : result;
    }

    public static LegacyFolderMigration.Plan transferPrivatePin(Context context, PreferenceSnapshot source,
            Map<String, List<String>> completePackages) throws IOException, GeneralSecurityException {
        LegacyFolderMigration.Plan plan = LegacyFolderMigration.decode(source, completePackages);
        if (plan.pinPresent) {
            char[] pin = LegacyFolderMigration.privatePin(source);
            try { new LockStore(context).importLegacyPin(pin, plan.lockedTargets); }
            finally { Arrays.fill(pin, '\0'); }
        }
        // Folder publication must follow a durable presentation recovery snapshot. A failure
        // after this step deliberately retains imported guards rather than exposing locked apps.
        return plan;
    }
}
