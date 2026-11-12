package com.jeremykenedy.firetv.ui.nativeapp.migration;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Only unambiguous exact inventory relationships become migration aliases. */
public final class IdentityInventory {
    private IdentityInventory() {}

    public static Map<String, String> components(Collection<String> components) {
        return com.jeremykenedy.firetv.ui.nativeapp.appgrid.RetainedHomeChoices.appIdentities(components);
    }

    public static Map<String, String> inputs(Map<String, String> factoryLabelsById) {
        return com.jeremykenedy.firetv.ui.nativeapp.appgrid.RetainedHomeChoices.inputIdentities(factoryLabelsById);
    }

    public static Set<String> ambiguousInputs(Map<String, String> factoryLabelsById) {
        Set<String> seen = new LinkedHashSet<>(), ambiguous = new LinkedHashSet<>();
        for (String label : factoryLabelsById.values()) {
            if (label == null || label.isEmpty()) continue;
            String alias = "tvinput_name:" + label;
            if (!seen.add(alias)) ambiguous.add(alias);
        }
        return ambiguous;
    }
}
