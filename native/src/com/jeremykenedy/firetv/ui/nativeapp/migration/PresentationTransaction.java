package com.jeremykenedy.firetv.ui.nativeapp.migration;

import java.io.IOException;
import java.util.*;

/** Journaled multi-file write. The integration owner must quiesce ordinary preference writers. */
public final class PresentationTransaction {
    public interface Store {
        Object lock();
        PresentationSnapshot read() throws IOException;
        /** Persist and verify before any preference write; retain until explicitly resolved. */
        void saveRecovery(PresentationSnapshot snapshot) throws IOException;
        boolean replace(String file, PreferenceSnapshot snapshot) throws IOException;
    }
    public static final class CommitFailure extends IOException {
        private static final long serialVersionUID = 1L;
        public final boolean rollbackVerified;
        private CommitFailure(boolean restored) {
            super(restored ? "Presentation migration failed. All previous settings were restored."
                    : "Presentation migration failed. Private recovery must be completed before another attempt.");
            rollbackVerified = restored;
        }
    }
    private PresentationTransaction() {}

    public static boolean apply(Store store, LegacyPresentationMapper.Plan plan) throws IOException {
        return apply(store, new PresentationSnapshot(plan.before), new PresentationSnapshot(plan.after));
    }
    /** Also supports a separately validated saved-setup/portable-import plan without a legacy marker. */
    public static boolean apply(Store store, PresentationSnapshot before, PresentationSnapshot after) throws IOException {
        synchronized (store.lock()) {
            if (!before.sameValues(store.read())) throw new IOException("Presentation settings changed. Prepare the operation again.");
            if (before.sameValues(after)) return false;
            store.saveRecovery(before);
            if (!before.sameValues(store.read())) throw new IOException("Presentation settings changed before application. Nothing was applied.");
            List<String> attempted = new ArrayList<>();
            try {
                // Native choices commit first. The source marker cannot describe an unfinished native mapping.
                for (String file : writeOrder()) {
                    if (before.stores.get(file).values().equals(after.stores.get(file).values())) continue;
                    attempted.add(file);
                    if (!store.replace(file, after.stores.get(file))) throw new IOException("A presentation write failed.");
                }
                if (!after.sameValues(store.read())) throw new IOException("Presentation readback did not match.");
                return true;
            } catch (IOException | RuntimeException failed) {
                boolean restored = restore(store, before, attempted);
                throw new CommitFailure(restored);
            }
        }
    }
    public static void recover(Store store, PresentationSnapshot snapshot) throws IOException {
        synchronized (store.lock()) {
            if (!restore(store, snapshot, writeOrder())) throw new IOException("Private presentation recovery could not be verified.");
        }
    }
    private static boolean restore(Store store, PresentationSnapshot snapshot, List<String> files) {
        boolean committed = true;
        List<String> reverse = new ArrayList<>(files); Collections.reverse(reverse);
        for (String file : reverse) {
            if (!snapshot.stores.containsKey(file)) continue;
            try { if (!store.replace(file, snapshot.stores.get(file))) committed = false; }
            catch (IOException | RuntimeException failed) { committed = false; }
        }
        try { return committed && snapshot.matches(store.read()); }
        catch (IOException | RuntimeException failed) { return false; }
    }
    private static List<String> writeOrder() {
        List<String> files = new ArrayList<>(LegacyPresentationMapper.FILES);
        files.remove(LauncherMigration.PREFERENCES); files.add(LauncherMigration.PREFERENCES);
        return files;
    }
}
