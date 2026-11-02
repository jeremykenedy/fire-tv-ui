package com.jeremykenedy.firetv.ui.nativeapp.backup;

import com.jeremykenedy.firetv.ui.nativeapp.migration.LegacyJson;
import com.jeremykenedy.firetv.ui.nativeapp.migration.PreferenceSnapshot;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Portable JSON carries only reviewed presentation stores, never private recovery files. */
public final class PortableBackup {
    public static final String BACKUP_NAME = "fire-tv-ui-backup.txt";
    public static final String IMPORT_NAME = "fire-tv-ui-import.txt";
    public static final int MAX_BYTES = 1024 * 1024;
    public static final String FORMAT = "fire-tv-ui-presentation";
    private PortableBackup() {}

    public interface Validator { void validate(Map<String, PreferenceSnapshot> stores) throws Exception; }

    public static byte[] encode(Map<String, PreferenceSnapshot> stores, Validator validator) {
        validate(stores, validator);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", FORMAT); root.put("version", 1);
        Map<String, Object> encoded = new LinkedHashMap<>();
        for (Map.Entry<String, PreferenceSnapshot> store : stores.entrySet()) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : store.getValue().values().entrySet()) {
                Object value = entry.getValue();
                Map<String, Object> typed = new LinkedHashMap<>();
                typed.put("type", type(value)); typed.put("value", value);
                values.put(entry.getKey(), typed);
            }
            encoded.put(store.getKey(), values);
        }
        root.put("stores", encoded);
        StringBuilder json = new StringBuilder(); append(json, root);
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw invalid();
        return bytes;
    }

    public static Map<String, PreferenceSnapshot> decode(byte[] bytes, Validator validator) {
        if (bytes == null || bytes.length > MAX_BYTES) throw invalid();
        String contents;
        try {
            contents = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException malformed) { throw invalid(); }
        Map<String, Object> root = LegacyJson.object(contents);
        if (!root.containsKey("format") && !root.containsKey("stores")) {
            throw new IllegalArgumentException("This is a legacy backup. Its full settings and media need verified migration before import. Nothing was changed.");
        }
        if (!root.keySet().equals(new LinkedHashSet<>(Arrays.asList("format", "version", "stores")))
                || !FORMAT.equals(root.get("format")) || !(root.get("version") instanceof BigDecimal)
                || ((BigDecimal) root.get("version")).compareTo(BigDecimal.ONE) != 0) throw invalid();
        Map<String, Object> rawStores = object(root.get("stores"));
        if (rawStores.isEmpty() || rawStores.size() > 32) throw invalid();
        Map<String, PreferenceSnapshot> stores = new LinkedHashMap<>();
        for (Map.Entry<String, Object> store : rawStores.entrySet()) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : object(store.getValue()).entrySet()) {
                Map<String, Object> typed = object(entry.getValue());
                if (!typed.keySet().equals(new LinkedHashSet<>(Arrays.asList("type", "value")))) throw invalid();
                values.put(entry.getKey(), value(typed.get("type"), typed.get("value")));
            }
            stores.put(store.getKey(), new PreferenceSnapshot(values));
        }
        Map<String, PreferenceSnapshot> result = Collections.unmodifiableMap(stores);
        validate(result, validator);
        return result;
    }

    public static byte[] read(InputStream input) throws IOException {
        if (input == null) throw new IOException("The selected document could not be opened.");
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (result.size() + count > MAX_BYTES) throw new IOException("This backup exceeds the 1 MB portable document limit.");
            if (count == 0) {
                int next = input.read();
                if (next == -1) break;
                if (result.size() == MAX_BYTES) throw new IOException("This backup exceeds the portable document limit.");
                result.write(next);
            } else result.write(buffer, 0, count);
        }
        return result.toByteArray();
    }

    private static Object value(Object type, Object raw) {
        if ("String".equals(type) && raw instanceof String) return raw;
        if ("Boolean".equals(type) && raw instanceof Boolean) return raw;
        if ("StringSet".equals(type) && raw instanceof List) {
            List<?> list = (List<?>) raw;
            Set<String> result = new LinkedHashSet<>();
            if (list.size() > 2048) throw invalid();
            for (Object item : list) if (!(item instanceof String) || !result.add((String) item)) throw invalid();
            return result;
        }
        if (raw instanceof BigDecimal) {
            BigDecimal number = (BigDecimal) raw;
            try {
                if ("Int".equals(type)) return number.intValueExact();
                if ("Long".equals(type)) return number.longValueExact();
                if ("Float".equals(type)) {
                    float value = number.floatValue();
                    if (!Float.isNaN(value) && !Float.isInfinite(value)) return value;
                }
            } catch (ArithmeticException outsideBounds) { throw invalid(); }
        }
        throw invalid();
    }
    private static String type(Object value) {
        if (value instanceof String) return "String";
        if (value instanceof Boolean) return "Boolean";
        if (value instanceof Integer) return "Int";
        if (value instanceof Long) return "Long";
        if (value instanceof Float) return "Float";
        if (value instanceof Set) return "StringSet";
        throw invalid();
    }
    private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map)) throw invalid();
        @SuppressWarnings("unchecked") Map<String, Object> result = (Map<String, Object>) value;
        return result;
    }
    static String stringify(Object value) {
        StringBuilder output = new StringBuilder(); append(output, value); return output.toString();
    }
    private static void append(StringBuilder out, Object value) {
        if (out.length() > MAX_BYTES) throw invalid();
        if (value instanceof String) {
            out.append('"');
            String text = (String) value;
            for (int index = 0; index < text.length(); index++) {
                if (out.length() > MAX_BYTES) throw invalid();
                char item = text.charAt(index);
                if (item == '"' || item == '\\') out.append('\\').append(item);
                else if (item < 32 || Character.isSurrogate(item)) {
                    out.append("\\u"); String hex = Integer.toHexString(item);
                    for (int pad = hex.length(); pad < 4; pad++) out.append('0');
                    out.append(hex);
                } else out.append(item);
            }
            out.append('"');
        } else if (value instanceof Map) {
            out.append('{'); boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!first) out.append(','); first = false;
                append(out, entry.getKey()); out.append(':'); append(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof Set) {
            List<String> sorted = new ArrayList<>();
            for (Object item : (Set<?>) value) sorted.add((String) item);
            Collections.sort(sorted);
            out.append('['); boolean first = true;
            for (String item : sorted) { if (!first) out.append(','); first = false; append(out, item); }
            out.append(']');
        } else if (value instanceof Boolean || value instanceof Number) out.append(value);
        else throw invalid();
    }
    private static void validate(Map<String, PreferenceSnapshot> stores, Validator validator) {
        try { validator.validate(stores); }
        catch (Exception rejected) { throw invalid(); }
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Unsupported or malformed portable backup. Nothing was changed.");
    }
}
