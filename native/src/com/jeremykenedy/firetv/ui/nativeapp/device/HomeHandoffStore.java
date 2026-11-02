package com.jeremykenedy.firetv.ui.nativeapp.device;

import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** Private recovery state, deliberately outside portable settings and backups. */
final class HomeHandoffStore implements HomeRoutingPolicy.HandoffPersistence {
    private final AtomicFile file;
    HomeHandoffStore(Context context) {
        file = new AtomicFile(new File(context.getNoBackupFilesDir(), "native-home-handoff"));
    }
    @Override public synchronized boolean read() {
        if (!file.getBaseFile().exists() && !new File(file.getBaseFile().getPath() + ".bak").exists()) return false;
        try (FileInputStream input = file.openRead()) {
            int value = input.read();
            // Corrupt or unexpected state protects the native screen until explicit recovery.
            return value != '0' || input.read() != -1;
        } catch (IOException failed) {
            return true;
        }
    }
    @Override public synchronized boolean write(boolean active) {
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(active ? '1' : '0');
            file.finishWrite(output);
            return read() == active;
        } catch (IOException failed) {
            if (output != null) file.failWrite(output);
            return false;
        }
    }
}
