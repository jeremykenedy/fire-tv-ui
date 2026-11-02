package com.jeremykenedy.firetv.ui.nativeapp.device;

import java.util.HashMap;
import java.util.Map;

public final class HomeSetupPolicyTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        Map<String, Object> values = new HashMap<>();
        check(HomeSetupPolicy.read(values).needsReview, "Absent legacy configuration needs review");
        check(!HomeSetupPolicy.read(values).keys, "Absent never enables Home");
        values.put("fire_tv_ui_home_enabled", false);
        check(!HomeSetupPolicy.read(values).keys && !HomeSetupPolicy.read(values).windows, "Explicit legacy off retained");
        values.put("fire_tv_ui_home_enabled", true);
        check(HomeSetupPolicy.read(values).keys && HomeSetupPolicy.read(values).windows, "Explicit legacy on retained");
        values.put("override_home_button", false);
        check(!HomeSetupPolicy.read(values).keys && HomeSetupPolicy.read(values).windows, "Explicit key opt-out retained");
        values.put(HomeSetupPolicy.HOME_KEYS, true);
        values.put(HomeSetupPolicy.HOME_WINDOWS, false);
        check(HomeSetupPolicy.read(values).keys && !HomeSetupPolicy.read(values).windows, "Source choices override legacy");
        values.put("fire_tv_ui_home_enabled", false);
        check(!HomeSetupPolicy.read(values).keys && !HomeSetupPolicy.read(values).windows,
                "Preference-only master off suppresses explicit source modes during Toolkit route switching");
        values.put("fire_tv_ui_home_enabled", "malformed-preserved-for-recovery");
        check(!HomeSetupPolicy.read(values).keys && HomeSetupPolicy.read(values).needsReview,
                "Malformed master state requires review without deleting saved source choices");
        values.put(HomeSetupPolicy.HOME_WINDOWS, "true");
        check(HomeSetupPolicy.read(values).needsReview && !HomeSetupPolicy.read(values).keys, "Malformed data fails closed");
        System.out.println("Home setup migration policy checks passed");
    }
}
