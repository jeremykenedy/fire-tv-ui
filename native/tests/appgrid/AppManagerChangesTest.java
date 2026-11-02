package com.jeremykenedy.firetv.ui.nativeapp.appgrid;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AppManagerChangesTest {
    public static void main(String[] args) {
        Map<String, Boolean> edits = new LinkedHashMap<>();
        edits.put("example.new/.Main", true);
        edits.put("example.old/.Main", false);
        String result = AppManagerChanges.hidden("example.old/.Main\nexample.keep/.Main", edits);
        check(result.equals("example.keep/.Main\nexample.new/.Main"), "Only touched app visibility changes");
        check(AppManagerChanges.hidden(null, Collections.<String, Boolean>emptyMap()).isEmpty(), "Empty state remains empty");
        try {
            AppManagerChanges.hidden("example.old/.Main\nexample.old/.Main", edits);
            throw new AssertionError("Malformed existing visibility accepted");
        } catch (IllegalArgumentException expected) { }
        System.out.println("App manager staged visibility and malformed-state preservation passed.");
    }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
}
