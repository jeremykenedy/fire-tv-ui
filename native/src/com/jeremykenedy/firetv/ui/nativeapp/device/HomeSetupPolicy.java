package com.jeremykenedy.firetv.ui.nativeapp.device;

import java.util.Map;

/** Preserves explicit legacy Home choices without enabling any system service. */
public final class HomeSetupPolicy {
    public static final String HOME_KEYS = "source_home_keys_enabled";
    public static final String HOME_WINDOWS = "source_home_window_fallback_enabled";
    public static final String LEGACY_HOME = "fire_tv_ui_home_enabled";
    public final boolean keys;
    public final boolean windows;
    public final boolean needsReview;

    private HomeSetupPolicy(boolean keys, boolean windows, boolean needsReview) {
        this.keys = keys;
        this.windows = windows;
        this.needsReview = needsReview;
    }

    public static HomeSetupPolicy read(Map<String, ?> values) {
        boolean legacy = Boolean.TRUE.equals(values.get(LEGACY_HOME));
        boolean keys = value(values, HOME_KEYS, legacy && !Boolean.FALSE.equals(values.get("override_home_button")));
        boolean windows = value(values, HOME_WINDOWS, legacy && !Boolean.FALSE.equals(values.get("better_home_detection")));
        boolean review = !values.containsKey(HOME_KEYS) && !values.containsKey(HOME_WINDOWS)
                && !values.containsKey(LEGACY_HOME);
        for (String key : new String[] {HOME_KEYS, HOME_WINDOWS}) {
            if (values.containsKey(key) && !(values.get(key) instanceof Boolean)) review = true;
        }
        if (values.containsKey(LEGACY_HOME) && !(values.get(LEGACY_HOME) instanceof Boolean)) review = true;
        if (!values.containsKey(HOME_KEYS) && legacy && values.containsKey("override_home_button")
                && !(values.get("override_home_button") instanceof Boolean)) review = true;
        if (!values.containsKey(HOME_WINDOWS) && legacy && values.containsKey("better_home_detection")
                && !(values.get("better_home_detection") instanceof Boolean)) review = true;
        // Malformed legacy values must not turn a Home implementation on by inference.
        boolean masterOff = Boolean.FALSE.equals(values.get(LEGACY_HOME));
        return new HomeSetupPolicy(!review && !masterOff && keys, !review && !masterOff && windows, review);
    }

    public static boolean launchOnBoot(String applicationId, Map<String, ?> values) {
        if (!HomeRoutingPolicy.PRODUCTION_PACKAGE.equals(applicationId)
                || !Boolean.TRUE.equals(values.get("start_on_boot"))) return false;
        HomeSetupPolicy choices = read(values);
        return !choices.needsReview && (choices.keys || choices.windows);
    }

    private static boolean value(Map<String, ?> values, String key, boolean fallback) {
        return values.containsKey(key) ? Boolean.TRUE.equals(values.get(key)) : fallback;
    }
}
