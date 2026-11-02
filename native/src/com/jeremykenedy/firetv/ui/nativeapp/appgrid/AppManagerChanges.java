package com.jeremykenedy.firetv.ui.nativeapp.appgrid;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Applies only staged visibility edits and retains other saved app choices. */
final class AppManagerChanges {
    private AppManagerChanges() {}

    static String hidden(String saved, Map<String, Boolean> changes) {
        Set<String> values = new LinkedHashSet<>();
        if (saved != null && !saved.isEmpty()) {
            for (String key : saved.split("\\n", -1))
                if (!values.add(key)) throw new IllegalArgumentException("Saved app visibility contains duplicates.");
        }
        validate(values);
        for (Map.Entry<String, Boolean> change : changes.entrySet()) {
            if (change.getValue() == null) throw new IllegalArgumentException("Choose whether to hide an app.");
            if (change.getValue()) values.add(change.getKey()); else values.remove(change.getKey());
        }
        validate(values);
        return String.join("\n", values);
    }

    private static void validate(Set<String> values) {
        new AppGridOptions(6, 1, false, Collections.<String>emptyList(), values, Collections.<String>emptySet());
    }
}
