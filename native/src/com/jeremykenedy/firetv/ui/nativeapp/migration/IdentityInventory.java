package com.jeremykenedy.firetv.ui.nativeapp.migration;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Only unambiguous exact inventory relationships become migration aliases. */
public final class IdentityInventory {
    private IdentityInventory() {}

    public static Map<String, String> components(Collection<String> components) {
        Map<String, String> result = new LinkedHashMap<>();
        Map<String, Set<String>> packages = new LinkedHashMap<>();
        for (String component : components) {
            if (!PresentationMappingContext.validComponent(component))
                throw new IllegalArgumentException("Invalid launch activity inventory.");
            result.put(component, component);
            String name = component.substring(0, component.indexOf('/'));
            if (!packages.containsKey(name)) packages.put(name, new LinkedHashSet<String>());
            packages.get(name).add(component);
        }
        for (Map.Entry<String, Set<String>> entry : packages.entrySet())
            if (entry.getValue().size() == 1) result.put(entry.getKey(), entry.getValue().iterator().next());
        return result;
    }

    public static Map<String, String> inputs(Map<String, String> factoryLabelsById) {
        Map<String, String> result = new LinkedHashMap<>();
        Set<String> ambiguous = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : factoryLabelsById.entrySet()) {
            String label = entry.getValue();
            if (label == null || label.isEmpty()) continue;
            String alias = "tvinput_name:" + label;
            if (result.containsKey(alias)) ambiguous.add(alias);
            else result.put(alias, entry.getKey());
        }
        for (String alias : ambiguous) result.remove(alias);
        return result;
    }
}
