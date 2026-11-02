package com.jeremykenedy.firetv.ui.nativeapp.backup;

import com.jeremykenedy.firetv.ui.nativeapp.migration.LegacyJson;
import com.jeremykenedy.firetv.ui.nativeapp.migration.PreferenceSnapshot;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Optional private content is an explicit additional envelope, never an ordinary setup. */
public final class BackupPayload {
    private static final String FORMAT = "fire-tv-ui-household-presentation";
    public final Map<String,PreferenceSnapshot> stores;
    public final Map<String,String> household;
    public BackupPayload(Map<String,PreferenceSnapshot> stores, Map<String,String> household) {
        this.stores = Collections.unmodifiableMap(new LinkedHashMap<>(stores));
        this.household = HouseholdContent.sanitize(household);
    }
    public byte[] encode(PortableBackup.Validator validator) {
        byte[] presentation = PortableBackup.encode(stores, validator);
        if (household.isEmpty()) return presentation;
        Map<String,Object> root = new LinkedHashMap<>();
        root.put("format", FORMAT); root.put("version", 1);
        root.put("presentation", new String(presentation, StandardCharsets.UTF_8));
        root.put("household", household);
        byte[] bytes = PortableBackup.stringify(root).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > PortableBackup.MAX_BYTES) throw invalid();
        return bytes;
    }
    public static BackupPayload decode(byte[] bytes, PortableBackup.Validator validator) {
        if (bytes == null || bytes.length > PortableBackup.MAX_BYTES) throw invalid();
        final String text;
        try { text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (Exception bad) { throw invalid(); }
        Map<String,Object> root = LegacyJson.object(text);
        if (!FORMAT.equals(root.get("format"))) return new BackupPayload(PortableBackup.decode(bytes,validator), Collections.<String,String>emptyMap());
        if (!root.keySet().equals(new HashSet<>(Arrays.asList("format","version","presentation","household")))
                || !(root.get("version") instanceof BigDecimal) || ((BigDecimal)root.get("version")).compareTo(BigDecimal.ONE)!=0
                || !(root.get("presentation") instanceof String) || !(root.get("household") instanceof Map)) throw invalid();
        Map<String,String> documents = new LinkedHashMap<>();
        for (Map.Entry<?,?> entry : ((Map<?,?>)root.get("household")).entrySet()) {
            if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)) throw invalid();
            documents.put((String)entry.getKey(),(String)entry.getValue());
        }
        return new BackupPayload(PortableBackup.decode(((String)root.get("presentation")).getBytes(StandardCharsets.UTF_8),validator),documents);
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Unsupported or malformed household backup. Nothing was changed."); }
}
