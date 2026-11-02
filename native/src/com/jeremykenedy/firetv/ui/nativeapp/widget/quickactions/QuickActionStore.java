package com.jeremykenedy.firetv.ui.nativeapp.widget.quickactions;

import android.content.Context;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Content links are private local configuration and excluded from portable exports. */
public final class QuickActionStore {
    private static final Object LOCK = new Object();
    private final AtomicFile file;
    public QuickActionStore(Context context) {
        file = new AtomicFile(new File(context.getApplicationContext().getNoBackupFilesDir(), "quick-actions-v1.txt"));
    }

    public QuickActions read() throws IOException {
        synchronized (LOCK) {
            if (!file.getBaseFile().exists() && !new File(file.getBaseFile().getPath() + ".bak").exists()) return QuickActions.empty();
            try (FileInputStream input = file.openRead(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > QuickActions.MAX_BYTES) throw new IOException("Saved actions exceed their limit.");
                    bytes.write(buffer, 0, count);
                }
                try { return QuickActions.decode(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString()); }
                catch (IllegalArgumentException invalid) { throw new IOException("Saved actions could not be read."); }
            }
        }
    }

    public void put(QuickActions.Entry entry, boolean add) throws IOException {
        synchronized (LOCK) { write(read().put(entry, add)); }
    }
    public void remove(String id) throws IOException { synchronized (LOCK) { write(read().remove(id)); } }
    public void move(String id, int delta) throws IOException { synchronized (LOCK) { write(read().move(id, delta)); } }

    private void write(QuickActions actions) throws IOException {
        FileOutputStream output = null;
        try {
            output = file.startWrite(); output.write(actions.encode().getBytes(StandardCharsets.UTF_8)); file.finishWrite(output);
        } catch (IOException failed) { if (output != null) file.failWrite(output); throw failed; }
    }
}
