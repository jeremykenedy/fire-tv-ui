package com.jeremykenedy.firetv.ui.nativeapp.widget.weather;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

final class WeatherCache {
    static final int MAX_BODY = 1024 * 1024;
    static final int MAX_FILE = MAX_BODY + 64000;
    private final File directory;
    static final class Entry {
        final String body; final Map<String, String> headers; final long savedAt; final long freshUntil; final boolean revalidate;
        Entry(String body, Map<String, String> headers, long savedAt, long maxFresh) {
            this.body = body; this.headers = new LinkedHashMap<>(headers); this.savedAt = savedAt;
            WeatherCachePolicy policy = new WeatherCachePolicy(headers, savedAt, maxFresh);
            freshUntil = policy.freshUntil; revalidate = policy.revalidate;
        }
        boolean fresh(long now) { return now >= savedAt && now < freshUntil; }
        boolean staleAllowed(long now) { return !revalidate && now >= savedAt && now - savedAt <= 86400000L; }
    }
    WeatherCache(File directory) { this.directory = directory; }
    synchronized Entry read(String key, long maxFresh) {
        File file = file(key);
        if (!file.isFile() || file.length() > MAX_FILE) return null;
        try (DataInputStream input = new DataInputStream(new FileInputStream(file))) {
            if (input.readInt() != 1) return null;
            long saved = input.readLong(); if (saved < 0) return null; int count = input.readInt();
            if (count < 0 || count > 12) return null;
            Map<String, String> headers = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) headers.put(input.readUTF(), input.readUTF());
            int length = input.readInt(); if (length < 0 || length > MAX_BODY) return null;
            byte[] body = new byte[length]; input.readFully(body); if (input.read() != -1) return null;
            return new Entry(new String(body, StandardCharsets.UTF_8), headers, saved, maxFresh);
        } catch (IOException | RuntimeException invalid) { return null; }
    }
    synchronized void write(String key, Entry entry) {
        File file = file(key);
        if (new WeatherCachePolicy(entry.headers, entry.savedAt, 86400000).noStore) { file.delete(); return; }
        byte[] body = entry.body.getBytes(StandardCharsets.UTF_8);
        if (body.length > MAX_BODY || entry.headers.size() > 12) return;
        if (!directory.isDirectory() && !directory.mkdirs()) return;
        File temporary = new File(file.getPath() + ".new");
        try (DataOutputStream output = new DataOutputStream(new FileOutputStream(temporary))) {
            output.writeInt(1); output.writeLong(entry.savedAt); output.writeInt(entry.headers.size());
            for (Map.Entry<String, String> header : entry.headers.entrySet()) { output.writeUTF(header.getKey()); output.writeUTF(header.getValue()); }
            output.writeInt(body.length); output.write(body); output.flush();
        } catch (IOException failed) { temporary.delete(); return; }
        if (!temporary.renameTo(file)) { temporary.delete(); return; }
        File[] files = directory.listFiles();
        if (files != null && files.length > 4) {
            Arrays.sort(files, new Comparator<File>() { @Override public int compare(File a, File b) { return Long.compare(a.lastModified(), b.lastModified()); } });
            for (int i = 0; i < files.length - 4; i++) files[i].delete();
        }
    }
    synchronized void remove(String key) { file(key).delete(); }
    private File file(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder(); for (byte value : digest) name.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return new File(directory, name + ".cache");
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
