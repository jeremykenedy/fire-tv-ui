package com.jeremykenedy.firetv.ui.nativeapp.migration;

import java.io.*;
import java.util.*;

/** Private recovery data, never a portable theme or a diagnostic payload. */
public final class PreferenceSnapshot {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAGIC = 0x46545549;
    private final Map<String, Object> values;

    public PreferenceSnapshot(Map<String, ?> source) {
        if (source == null || source.size() > 4096) throw invalid();
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isEmpty() || key.length() > 512) throw invalid();
            Object value = entry.getValue();
            if (value instanceof Set) {
                Set<?> items = (Set<?>) value;
                if (items.size() > 2048) throw invalid();
                Set<String> strings = new LinkedHashSet<>();
                for (Object item : items) {
                    if (!(item instanceof String) || ((String) item).length() > 4096) throw invalid();
                    strings.add((String) item);
                }
                value = Collections.unmodifiableSet(strings);
            } else if (value instanceof String) {
                if (((String) value).length() > 1024 * 1024) throw invalid();
            } else if (!(value instanceof Boolean || value instanceof Integer || value instanceof Long
                    || value instanceof Float && !Float.isNaN((Float) value) && !Float.isInfinite((Float) value))) {
                throw invalid();
            }
            copy.put(key, value);
        }
        values = Collections.unmodifiableMap(copy);
        if (encodedSize() > MAX_BYTES) throw invalid();
    }

    public Map<String, Object> values() { return values; }

    public byte[] encode() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(MAGIC);
        output.writeInt(1);
        output.writeInt(values.size());
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            writeText(output, entry.getKey());
            Object value = entry.getValue();
            if (value instanceof String) { output.writeByte(1); writeText(output, (String) value); }
            else if (value instanceof Boolean) { output.writeByte(2); output.writeBoolean((Boolean) value); }
            else if (value instanceof Integer) { output.writeByte(3); output.writeInt((Integer) value); }
            else if (value instanceof Long) { output.writeByte(4); output.writeLong((Long) value); }
            else if (value instanceof Float) { output.writeByte(5); output.writeFloat((Float) value); }
            else {
                output.writeByte(6);
                Set<?> strings = (Set<?>) value;
                output.writeInt(strings.size());
                for (Object item : strings) writeText(output, (String) item);
            }
        }
        return bytes.toByteArray();
    }

    public static PreferenceSnapshot decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length > MAX_BYTES) throw malformed();
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
            if (input.readInt() != MAGIC || input.readInt() != 1) throw malformed();
            int count = bounded(input.readInt(), 4096);
            Map<String, Object> values = new LinkedHashMap<>();
            for (int index = 0; index < count; index++) {
                String key = readText(input, 512);
                if (values.containsKey(key)) throw malformed();
                Object value;
                switch (input.readUnsignedByte()) {
                    case 1: value = readText(input, 1024 * 1024); break;
                    case 2:
                        int flag = input.readUnsignedByte();
                        if (flag > 1) throw malformed();
                        value = flag == 1; break;
                    case 3: value = input.readInt(); break;
                    case 4: value = input.readLong(); break;
                    case 5: value = input.readFloat(); break;
                    case 6:
                        int size = bounded(input.readInt(), 2048);
                        Set<String> items = new LinkedHashSet<>();
                        for (int item = 0; item < size; item++) {
                            if (!items.add(readText(input, 4096))) throw malformed();
                        }
                        value = items; break;
                    default: throw malformed();
                }
                values.put(key, value);
            }
            if (input.available() != 0) throw malformed();
            return new PreferenceSnapshot(values);
        } catch (IllegalArgumentException invalid) {
            throw malformed();
        }
    }

    private long encodedSize() {
        long size = 12;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            size += 5L + 2L * entry.getKey().length();
            Object value = entry.getValue();
            if (value instanceof String) size += 4L + 2L * ((String) value).length();
            else if (value instanceof Boolean) size++;
            else if (value instanceof Long) size += 8;
            else if (value instanceof Set) {
                size += 4;
                for (Object item : (Set<?>) value) size += 4L + 2L * ((String) item).length();
            } else size += 4;
        }
        return size;
    }

    private static void writeText(DataOutputStream output, String value) throws IOException {
        output.writeInt(value.length());
        for (int index = 0; index < value.length(); index++) output.writeChar(value.charAt(index));
    }

    private static String readText(DataInputStream input, int max) throws IOException {
        int count = bounded(input.readInt(), max);
        if ((long) count * 2 > input.available()) throw malformed();
        char[] text = new char[count];
        for (int index = 0; index < count; index++) text[index] = input.readChar();
        return new String(text);
    }

    private static int bounded(int count, int max) throws IOException {
        if (count < 0 || count > max) throw malformed();
        return count;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Unsupported or oversized preference snapshot.");
    }
    private static IOException malformed() { return new IOException("Invalid private recovery snapshot."); }
}
