package com.jeremykenedy.firetv.ui.nativeapp.migration;

import com.jeremykenedy.firetv.ui.nativeapp.folders.FolderOptions;
import java.util.*;

/** Decoder for the independently observed 1.17 native backup contract. Does not apply settings. */
public final class LegacyFolderMigration {
    private LegacyFolderMigration() {}

    public static final class Plan {
        public final FolderOptions folders;
        public final Set<String> lockedTargets;
        public final boolean pinPresent;
        private final Map<String, Object> source;
        private Plan(FolderOptions folders, Set<String> targets, boolean pinPresent, PreferenceSnapshot source) {
            this.folders = folders;
            this.lockedTargets = Collections.unmodifiableSet(new LinkedHashSet<>(targets));
            this.pinPresent = pinPresent;
            this.source = source.values();
        }
        boolean matches(PreferenceSnapshot other) { return source.equals(other.values()); }
    }

    /** Each package entry must contain its complete verified native launchable component inventory. */
    public static Plan decode(PreferenceSnapshot source, Map<String, List<String>> completePackages) {
        if (source == null || completePackages == null) throw invalid();
        Object encoded = source.values().get("folders");
        if (!(encoded instanceof String)) throw invalid();
        Map<String, Object> raw = LegacyJson.object((String) encoded);
        if (raw.size() > FolderOptions.MAX_FOLDERS) throw invalid();
        List<FolderOptions.Folder> folders = new ArrayList<>();
        Set<String> targets = new LinkedHashSet<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String id = entry.getKey();
            // Only numeric identities have been observed through the supported native workflow.
            if (!id.matches("[0-9]{1,128}") || !(entry.getValue() instanceof Map)) throw invalid();
            Map<?, ?> folder = (Map<?, ?>) entry.getValue();
            if (!folder.keySet().equals(new HashSet<String>(Arrays.asList("name", "isLocked", "apps")))
                    || !(folder.get("name") instanceof String) || !(folder.get("isLocked") instanceof Boolean)
                    || !(folder.get("apps") instanceof List)) throw invalid();
            String name = (String) folder.get("name");
            if (!name.equals(name.trim())) throw invalid();
            List<?> packages = (List<?>) folder.get("apps");
            if (packages.size() > FolderOptions.MAX_APPS) throw invalid();
            Set<String> seenPackages = new LinkedHashSet<>(), seenComponents = new LinkedHashSet<>();
            for (Object value : packages) {
                if (!(value instanceof String)) throw invalid();
                String packageName = (String) value;
                if (!packageName.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+") || !seenPackages.add(packageName)) throw invalid();
                List<String> components = completePackages.get(packageName);
                // The old folder contains one package tile. Multiple launchable activities cannot
                // be published as multiple tiles or selected without a verified relationship.
                if (components == null || components.size() != 1) throw invalid();
                for (String component : components) {
                    if (!PresentationMappingContext.validComponent(component) || !component.startsWith(packageName + "/")) throw invalid();
                    String canonical = new FolderOptions.Folder(id, name, Collections.singletonList(component)).components.get(0);
                    if (!seenComponents.add(canonical) || seenComponents.size() > FolderOptions.MAX_APPS) throw invalid();
                }
            }
            FolderOptions.Folder converted = new FolderOptions.Folder(id, name, new ArrayList<>(seenComponents));
            folders.add(converted);
            if ((Boolean) folder.get("isLocked")) {
                targets.add("folder:" + id);
                // Guard every launchable alias so the global app grid cannot bypass a folder lock.
                targets.addAll(converted.components);
                for (String packageName : seenPackages) targets.add("package:" + packageName);
            }
        }
        boolean pinPresent = source.values().containsKey("folder_lock_pin");
        if (pinPresent) validatePin(source.values().get("folder_lock_pin"));
        if (!targets.isEmpty() && !pinPresent) throw invalid();
        if (targets.size() > 512) throw invalid();
        return new Plan(new FolderOptions(folders), targets, pinPresent, source);
    }

    /** Caller must clear the returned buffer; never include it in portable/setup patches. */
    public static char[] privatePin(PreferenceSnapshot source) {
        Object pin = source.values().get("folder_lock_pin");
        validatePin(pin);
        return ((String) pin).toCharArray();
    }
    /** Encode the native folder store without changing the original legacy data. */
    public static String encode(FolderOptions folders) {
        StringBuilder json = new StringBuilder("[");
        for (FolderOptions.Folder folder : folders.folders) {
            if (json.length() > 1) json.append(',');
            json.append("{\"id\":"); appendString(json, folder.id);
            json.append(",\"name\":"); appendString(json, folder.name);
            json.append(",\"components\":[");
            boolean first = true;
            for (String component : folder.components) {
                if (!first) json.append(',');
                appendString(json, component); first = false;
            }
            json.append("]}");
        }
        json.append(']');
        if (json.length() > 262144) throw invalid();
        return json.toString();
    }
    private static void appendString(StringBuilder json, String value) {
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') json.append('\\');
            json.append(c);
        }
        json.append('"');
    }
    private static void validatePin(Object value) {
        if (!(value instanceof String) || !((String) value).matches("[0-9]{4}")) throw invalid();
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Unsupported or unresolved legacy folder data. Original private data has been kept.");
    }
}
