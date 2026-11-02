package com.jeremykenedy.firetv.ui.nativeapp.backup;

import com.jeremykenedy.firetv.ui.nativeapp.migration.PreferenceSnapshot;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class PortableBackupTest {
    private static int checks;
    private static final PortableBackup.Validator VALIDATOR = stores -> {
        if (!stores.keySet().equals(Collections.singleton("native_test"))) throw new IllegalArgumentException();
        if (!new HashSet<>(Arrays.asList("text", "explicit", "count", "long", "float", "set"))
                .containsAll(stores.get("native_test").values().keySet())) throw new IllegalArgumentException();
    };
    public static void main(String[] args) throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("text", "Unicode 🌟 and quote \" \\ \n"); values.put("explicit", false);
        values.put("count", 4); values.put("long", Long.MAX_VALUE); values.put("float", 0.5f);
        values.put("set", new LinkedHashSet<>(Arrays.asList("one", "two")));
        Map<String, PreferenceSnapshot> original = Collections.singletonMap("native_test", new PreferenceSnapshot(values));
        byte[] bytes = PortableBackup.encode(original, VALIDATOR);
        Map<String, PreferenceSnapshot> decoded = PortableBackup.decode(bytes, VALIDATOR);
        check(decoded.get("native_test").values().equals(values), "Exact preference types and unicode round trip");
        Map<String, PreferenceSnapshot> absent = Collections.singletonMap("native_test", new PreferenceSnapshot(Collections.emptyMap()));
        check(PortableBackup.decode(PortableBackup.encode(absent, VALIDATOR), VALIDATOR).get("native_test").values().isEmpty(), "Absent choices remain absent");
        check(decoded.get("native_test").values().containsKey("explicit"), "Explicit default retained");
        String valid = new String(bytes, StandardCharsets.UTF_8);
        reject(valid.replace("\"version\":1", "\"version\":2"));
        reject(valid.replace("\"version\":1", "\"version\":1,\"version\":1"));
        reject(valid.replace("\"stores\":", "\"unknown\":true,\"stores\":"));
        reject(valid.replace("native_test", "launcher_prefs"));
        reject(valid.replace("\"count\":", "\"plex_token\":"));
        reject(valid.replace("\"value\":4", "\"value\":4.5"));
        reject(valid.replace("\"value\":4", "\"value\":2147483648"));
        reject(valid.replace("\"value\":9223372036854775807", "\"value\":9223372036854775808"));
        reject(valid.replace("\"value\":0.5", "\"value\":1e1000"));
        reject(valid.replace("[\"one\",\"two\"]", "[\"one\",\"one\"]"));
        reject(valid.replace("[\"one\",\"two\"]", "[\"one\",7]"));
        reject(valid.replace("\"Boolean\"", "\"Unknown\""));
        reject(valid.replace("\"stores\":", "\"stores\":null,\"discarded\":"));
        String nested = "{}";
        for (int i = 0; i < 40; i++) nested = "{\"nested\":" + nested + "}";
        reject(nested);
        reject(valid + "{}"); reject("{}"); reject("{\"apps_per_row\":6}");
        try { PortableBackup.decode(new byte[] {(byte) 0xc3, (byte) 0x28}, VALIDATOR); throw new AssertionError("Bad UTF-8 accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        check(Arrays.equals(PortableBackup.read(new ByteArrayInputStream(bytes)), bytes), "Document reads exact bytes");
        try { PortableBackup.read(new ByteArrayInputStream(new byte[PortableBackup.MAX_BYTES + 1])); throw new AssertionError("Oversized document accepted"); }
        catch (IOException expected) { checks++; }
        try { PortableBackup.decode(new byte[PortableBackup.MAX_BYTES + 1], VALIDATOR); throw new AssertionError("Oversized JSON accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        try {
            PortableBackup.encode(Collections.singletonMap("native_test", new PreferenceSnapshot(
                    Collections.singletonMap("text", String.join("", Collections.nCopies(1024 * 1024, "x"))))), VALIDATOR);
            throw new AssertionError("Oversized serialized document accepted");
        } catch (IllegalArgumentException expected) { checks++; }
        check(!valid.contains("plex") && !valid.contains("noBackup") && !valid.contains("lock_pin"), "No private files or account keys serialized");
        System.out.println("Portable backup checks passed: " + checks);
    }
    private static void reject(String value) {
        try { PortableBackup.decode(value.getBytes(StandardCharsets.UTF_8), VALIDATOR); throw new AssertionError("Malformed input accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
