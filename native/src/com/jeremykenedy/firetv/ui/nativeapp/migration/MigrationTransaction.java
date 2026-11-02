package com.jeremykenedy.firetv.ui.nativeapp.migration;

import java.io.IOException;

/** Opt-in operation. Callers must quiesce every writer of the supplied preference file. */
public final class MigrationTransaction {
    public interface Store {
        Object lock();
        PreferenceSnapshot read() throws IOException;
        /** Persist privately and verify readback before returning; do not overwrite an unresolved recovery. */
        void saveRecovery(PreferenceSnapshot before) throws IOException;
        /** Replace the complete file, including removal of keys absent in the supplied snapshot. */
        boolean replace(PreferenceSnapshot values) throws IOException;
    }

    public static final class CommitFailure extends IOException {
        private static final long serialVersionUID = 1L;
        public final boolean rollbackVerified;
        private CommitFailure(boolean rollbackVerified) {
            super(rollbackVerified ? "Migration failed. Previous preferences were restored."
                    : "Migration failed. Private recovery is required before another attempt.");
            this.rollbackVerified = rollbackVerified;
        }
    }

    private MigrationTransaction() {}

    public static boolean apply(Store store) throws IOException {
        synchronized (store.lock()) {
            PreferenceSnapshot before = store.read();
            PreferenceSnapshot next = LauncherMigration.prepare(before);
            if (before.values().equals(next.values())) return false;
            store.saveRecovery(before);
            if (!before.values().equals(store.read().values())) {
                throw new IOException("Preferences changed during migration preparation. Nothing was applied.");
            }
            try {
                if (store.replace(next) && next.values().equals(store.read().values())) return true;
            } catch (IOException | RuntimeException failed) {
                // A failed commit may already have changed the in-memory preferences.
            }
            boolean restored = false;
            try {
                restored = store.replace(before) && before.values().equals(store.read().values());
            } catch (IOException | RuntimeException failed) {
                restored = false;
            }
            throw new CommitFailure(restored);
        }
    }

    /** The recovery snapshot remains retained; the caller decides how to resolve its lifecycle. */
    public static void recover(Store store, PreferenceSnapshot recovery) throws IOException {
        synchronized (store.lock()) {
            if (!store.replace(recovery) || !recovery.values().equals(store.read().values())) {
                throw new IOException("Preference recovery could not be verified.");
            }
        }
    }
}
