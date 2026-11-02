package com.jeremykenedy.firetv.ui.nativeapp.migration;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Run before any preference consumer or writer, including after a process restart. */
public final class PresentationUpgrade {
    public interface Store extends PresentationTransaction.Store {
        boolean hasRecovery();
        PresentationSnapshot readRecovery() throws IOException;
        /** Delete only the matching journal after verifying the complete current state. */
        void resolveRecovery(PresentationSnapshot original, PresentationSnapshot current) throws IOException;
    }
    public static final class Result {
        public final boolean migrated, recovered;
        public final Set<String> unmappedLegacyKeys;
        private Result(boolean migrated, boolean recovered, Set<String> unmapped) {
            this.migrated = migrated; this.recovered = recovered;
            unmappedLegacyKeys = Collections.unmodifiableSet(new LinkedHashSet<>(unmapped));
        }
        Result withRecovery() { return new Result(migrated, true, unmappedLegacyKeys); }
    }
    private PresentationUpgrade() {}

    public static Result apply(Store store, PresentationMappingContext identities) throws IOException {
        return apply(store, identities, null, false);
    }
    static Result apply(Store store, PresentationMappingContext identities,
            LegacyFolderMigration.Plan folders, boolean privateLocksVerified) throws IOException {
        if (store == null || identities == null) throw new IllegalArgumentException("Missing migration context.");
        synchronized (store.lock()) {
            boolean recovered = false;
            if (store.hasRecovery()) {
                PresentationSnapshot original = store.readRecovery();
                PresentationTransaction.recover(store, original);
                store.resolveRecovery(original, original);
                recovered = true;
            }
            PresentationSnapshot before = store.read();
            PreferenceSnapshot legacy = before.stores.get(LauncherMigration.PREFERENCES);
            Object version = legacy.values().get(LegacyPresentationMapper.VERSION_KEY);
            if (version != null && (!(version instanceof Integer) || (Integer) version < 0 || (Integer) version > 1))
                throw new IOException("Unsupported presentation migration version.");
            if (Integer.valueOf(1).equals(version) || legacy.values().isEmpty())
                return new Result(false, recovered, Collections.<String>emptySet());
            Map<String, PreferenceSnapshot> nativeBefore = new LinkedHashMap<>(before.stores);
            nativeBefore.remove(LauncherMigration.PREFERENCES);
            LegacyPresentationMapper.Plan plan = LegacyPresentationMapper.plan(legacy, identities, nativeBefore, folders, privateLocksVerified);
            PresentationSnapshot after = new PresentationSnapshot(plan.after);
            PresentationTransaction.apply(store, before, after);
            // No ordinary writer may run until this completes. Otherwise a later restart could
            // restore the upgrade journal over choices made after a successful migration.
            store.resolveRecovery(before, after);
            return new Result(true, recovered, plan.unmappedLegacyKeys);
        }
    }
}
