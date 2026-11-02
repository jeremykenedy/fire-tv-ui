package com.jeremykenedy.firetv.ui.nativeapp.migration;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class IdentityInventoryTest {
    public static void main(String[] args) {
        Map<String, String> components = IdentityInventory.components(Arrays.asList(
                "fixture.one/fixture.one.Main", "fixture.two/fixture.two.Main", "fixture.two/fixture.two.Second"));
        check(components.get("fixture.one").equals("fixture.one/fixture.one.Main"), "Unique package identity retained.");
        check(!components.containsKey("fixture.two") && components.containsKey("fixture.two/fixture.two.Second"), "Ambiguous package never picks an arbitrary launch activity.");
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("fixture/.Input/one", "HDMI 1"); labels.put("fixture/.Input/two", "HDMI 1");
        labels.put("fixture/.Input/three", "HDMI 2"); labels.put("fixture/.Input/four", "hdmi 2");
        Map<String, String> inputs = IdentityInventory.inputs(labels);
        check(!inputs.containsKey("tvinput_name:HDMI 1"), "Duplicate factory labels never become guessed platform identities.");
        check(inputs.get("tvinput_name:HDMI 2").equals("fixture/.Input/three")
                && inputs.get("tvinput_name:hdmi 2").equals("fixture/.Input/four"), "Exact factory names remain distinct without normalization.");
        check(!inputs.containsKey("tvinput_name:Console"), "Custom display names never supply identity.");
        System.out.println("Exact package and input migration identity inventory passed.");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
