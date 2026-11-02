package com.jeremykenedy.firetv.ui.nativeapp.migration;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Checksummed private recovery for the complete reviewed set of preference files. */
public final class PresentationSnapshot {
    public static final int MAX_BYTES = 32 * 1024 * 1024;
    public final Map<String, PreferenceSnapshot> stores;

    public PresentationSnapshot(Map<String, PreferenceSnapshot> stores) {
        if (stores == null || stores.size() != LegacyPresentationMapper.FILES.size()
                || !stores.keySet().containsAll(LegacyPresentationMapper.FILES) || stores.containsValue(null)) throw invalid();
        this.stores = Collections.unmodifiableMap(new LinkedHashMap<>(stores));
    }
    public boolean sameValues(PresentationSnapshot other) {
        if (other == null) return false;
        for (String file : LegacyPresentationMapper.FILES) {
            if (!stores.get(file).values().equals(other.stores.get(file).values())) return false;
        }
        return true;
    }
    public byte[] encode() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(0x46545053); output.writeInt(1); output.writeInt(stores.size());
        for (String file : LegacyPresentationMapper.FILES) {
            byte[] values = stores.get(file).encode();
            if ((long) bytes.size() + values.length + file.length() + 6 > MAX_BYTES - 32) throw invalid();
            output.writeUTF(file); output.writeInt(values.length); output.write(values);
        }
        byte[] content = bytes.toByteArray();
        output.write(hash(content));
        return bytes.toByteArray();
    }
    public static PresentationSnapshot decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 44 || bytes.length > MAX_BYTES) throw malformed();
        byte[] content = Arrays.copyOf(bytes, bytes.length - 32);
        if (!MessageDigest.isEqual(hash(content), Arrays.copyOfRange(bytes, content.length, bytes.length))) throw malformed();
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(content));
        if (input.readInt() != 0x46545053 || input.readInt() != 1 || input.readInt() != LegacyPresentationMapper.FILES.size()) throw malformed();
        Map<String, PreferenceSnapshot> stores = new LinkedHashMap<>();
        for (int index = 0; index < LegacyPresentationMapper.FILES.size(); index++) {
            String file = input.readUTF();
            int length = input.readInt();
            if (!LegacyPresentationMapper.FILES.contains(file) || stores.containsKey(file)
                    || length < 0 || length > PreferenceSnapshot.MAX_BYTES || length > input.available()) throw malformed();
            byte[] values = new byte[length]; input.readFully(values);
            stores.put(file, PreferenceSnapshot.decode(values));
        }
        if (input.available() != 0) throw malformed();
        return new PresentationSnapshot(stores);
    }
    private static byte[] hash(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid private presentation snapshot."); }
    private static IOException malformed() { return new IOException("Invalid private presentation recovery."); }
}
