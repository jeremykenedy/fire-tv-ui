package com.jeremykenedy.firetv.ui.nativeapp.updates;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** App-private journal for verified staged bytes; never an external metadata trust source. */
public final class StagedUpdateStore {
    private final File directory;
    private final File journal;
    public StagedUpdateStore(File directory) {
        this.directory = directory;
        journal = new File(directory, "verified-update-state");
    }
    public synchronized void save(UpdateCoordinator.Verified verified) throws IOException {
        File pending = new File(directory, "verified-update-state.pending");
        try {
            try (FileOutputStream file = new FileOutputStream(pending); DataOutputStream output = new DataOutputStream(file)) {
                UpdateCoordinator.Release release = verified.release;
                output.writeInt(1); output.writeLong(System.currentTimeMillis());
                output.writeUTF(verified.file.getName());
                output.writeUTF(release.repository); output.writeUTF(release.tag);
                output.writeUTF(release.packageName); output.writeUTF(release.url); output.writeUTF(release.sha256);
                output.writeLong(release.bytes); output.writeLong(release.version);
                output.writeUTF(release.versionName); output.writeUTF(release.notes);
                output.flush(); file.getFD().sync();
            }
            if (!pending.renameTo(journal)) throw new IOException("Could not retain the verified staged update.");
        } finally { pending.delete(); }
    }
    public synchronized UpdateCoordinator.Verified restore(UpdateCoordinator coordinator,
            UpdateCoordinator.Cancellation cancellation) throws Exception {
        if (!journal.isFile()) return null;
        if (journal.length() > 65536) { clear(); throw new IOException("Invalid staged update journal."); }
        try (DataInputStream input = new DataInputStream(new FileInputStream(journal))) {
            if (input.readInt() != 1) throw new IOException("Unknown staged update journal.");
            long checkedAt = input.readLong();
            long now = System.currentTimeMillis();
            if (checkedAt <= 0 || checkedAt > now || now - checkedAt >= 6 * 60 * 60 * 1000L) throw new IOException("The staged release needs to be checked again.");
            String name = input.readUTF();
            UpdateCoordinator.Release release = new UpdateCoordinator.Release(input.readUTF(), input.readUTF(),
                    input.readUTF(), input.readUTF(), input.readUTF(), input.readLong(), input.readLong(), input.readUTF(), input.readUTF());
            if (input.read() != -1) throw new IOException("Unexpected staged update journal contents.");
            return coordinator.recover(new File(directory, name), release, cancellation);
        } catch (InterruptedException cancelled) { throw cancelled; }
        catch (Exception invalid) { clear(); throw invalid; }
    }
    public synchronized boolean hasPending() { return journal.isFile(); }
    public synchronized void cleanup(UpdateCoordinator.Verified keep) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.getName().matches("verified-update-[A-Za-z0-9_-]+\\.apk")
                    && (keep == null || !file.equals(keep.file))) file.delete();
        }
    }
    public synchronized void clear() { journal.delete(); new File(directory, "verified-update-state.pending").delete(); }
}
