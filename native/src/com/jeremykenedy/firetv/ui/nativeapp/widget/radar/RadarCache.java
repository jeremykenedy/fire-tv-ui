package com.jeremykenedy.firetv.ui.nativeapp.widget.radar;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;

final class RadarCache {
    static final int MAX_BYTES = 1024 * 1024;
    static final long REFRESH_MILLIS = 5 * 60000L;
    static final long RETRY_MILLIS = 60000L;
    static final long MAX_AGE_MILLIS = 6 * 3600000L;
    private final File directory;
    private final LinkedHashMap<String, Entry> memory = new LinkedHashMap<String, Entry>(2, .75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, RadarCache.Entry> eldest) { return size() > 2; }
    };

    static final class Entry {
        final RadarSource source;
        final byte[] bytes;
        final long fetchedAt, modifiedAt, freshUntil;
        final boolean cacheable;
        final String etag;
        Entry(RadarSource source, byte[] bytes, long fetchedAt, long modifiedAt, String etag) {
            this(source, bytes, fetchedAt, modifiedAt, etag, fetchedAt + REFRESH_MILLIS, true);
        }
        Entry(RadarSource source, byte[] bytes, long fetchedAt, long modifiedAt, String etag, long freshUntil, boolean cacheable) {
            if (source == null || bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Invalid radar cache entry");
            this.source = source; this.bytes = bytes; this.fetchedAt = fetchedAt; this.modifiedAt = modifiedAt;
            this.freshUntil = Math.max(fetchedAt, Math.min(fetchedAt + REFRESH_MILLIS, freshUntil)); this.cacheable = cacheable;
            this.etag = etag == null || etag.length() > 1024 || etag.indexOf('\r') >= 0 || etag.indexOf('\n') >= 0 ? "" : etag;
        }
    }

    RadarCache(File directory) { this.directory = directory; }
    synchronized Entry read(RadarSource source) {
        Entry remembered = memory.get(source.id);
        if (remembered != null) return remembered;
        File file = file(source);
        if (file.length() > MAX_BYTES + 4096) return null;
        try (DataInputStream input = new DataInputStream(new FileInputStream(file))) {
            if (input.readInt() != 2 || !input.readUTF().equals(source.id)) return null;
            long fetched = input.readLong(), modified = input.readLong(), freshUntil = input.readLong(); String etag = input.readUTF();
            int length = input.readInt();
            if (length <= 0 || length > MAX_BYTES) return null;
            byte[] bytes = new byte[length]; input.readFully(bytes);
            if (input.read() != -1) return null;
            Entry entry = new Entry(source, bytes, fetched, modified, etag, freshUntil, true);
            memory.put(source.id, entry); return entry;
        } catch (IOException | IllegalArgumentException invalid) { return null; }
    }

    synchronized void write(Entry entry) {
        if (!entry.cacheable) { remove(entry.source); return; }
        memory.put(entry.source.id, entry);
        if (!directory.isDirectory() && !directory.mkdirs()) return;
        File temporary = new File(directory, entry.source.stem + ".tmp");
        try {
            try (DataOutputStream output = new DataOutputStream(new FileOutputStream(temporary))) {
                output.writeInt(2); output.writeUTF(entry.source.id); output.writeLong(entry.fetchedAt);
                output.writeLong(entry.modifiedAt); output.writeLong(entry.freshUntil); output.writeUTF(entry.etag);
                output.writeInt(entry.bytes.length); output.write(entry.bytes);
            }
            Files.move(temporary.toPath(), file(entry.source).toPath(), StandardCopyOption.REPLACE_EXISTING);
            File[] files = directory.listFiles();
            if (files != null) {
                java.util.List<File> images = new java.util.ArrayList<>();
                for (File file : files) {
                    if (file.getName().endsWith(".tmp")) file.delete();
                    else if (file.getName().endsWith(".radar")) images.add(file);
                }
                java.util.Collections.sort(images, new java.util.Comparator<File>() {
                    @Override public int compare(File first, File second) { return Long.compare(second.lastModified(), first.lastModified()); }
                });
                for (int i = 2; i < images.size(); i++) images.get(i).delete();
            }
        } catch (IOException unavailable) { temporary.delete(); }
    }

    synchronized void remove(RadarSource source) { memory.remove(source.id); file(source).delete(); }

    private File file(RadarSource source) { return new File(directory, source.stem + ".radar"); }
    static boolean within(long timestamp, long now, long maximum) { return timestamp > 0 && timestamp <= now && now - timestamp < maximum; }
}
