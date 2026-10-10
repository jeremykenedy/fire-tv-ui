package com.jeremykenedy.firetv.ui;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ScreensaversTest {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        check(Screensavers.fullComponent(null).equals(""), "No active setting is supported");
        check(Screensavers.fullComponent("com.tv/.Dream").equals("com.tv/com.tv.Dream"),
                "Fire OS abbreviated component names expand correctly");
        check(Screensavers.fullComponent("com.tv/com.tv.Dream").equals("com.tv/com.tv.Dream"),
                "Fully qualified component names remain intact");
        check(Screensavers.isScreensaver("android.permission.BIND_DREAM_SERVICE"),
                "Dream services are selectable");
        check(!Screensavers.isScreensaver(null), "Unprotected ordinary services are excluded");
        check(!Screensavers.isScreensaver("android.permission.BIND_ACCESSIBILITY_SERVICE"),
                "Accessibility services are excluded");
        check(Screensavers.isHidden("com.amazon.tv.quicksettings"), "Store demo is excluded");
        check(!Screensavers.isHidden("com.amazon.ftv.screensaver"), "Amazon screensaver is available");
        check(Screensavers.previewFile("com.tv/.Dream").equals(
                Screensavers.previewFile("com.tv/com.tv.Dream")),
                "Preview filenames match both component forms");
        check(!Screensavers.previewFile("com.tv/.Dream").equals(
                Screensavers.previewFile("com.tv/.OtherDream")),
                "Different dreams in one package have separate screenshots");
        List<Screensavers.Choice> input = Arrays.asList(
                new Screensavers.Choice("com.z/.Dream", "Snoozy"),
                new Screensavers.Choice("com.a/.Dream", "aerial Views"),
                new Screensavers.Choice("com.a/com.a.Dream", "Duplicate"));
        List<Screensavers.Choice> choices = Screensavers.sorted(input);
        check(choices.size() == 2, "Equivalent service declarations do not duplicate rows");
        check(choices.get(0).label.equals("aerial Views"), "Labels sort regardless of case");
        check(input.size() == 3, "Loading the picker does not mutate discovered services");
        check(Screensavers.indexOfActive(choices, "com.a/com.a.Dream") == 0,
                "The persisted full component matches the short picker form");
        check(Screensavers.indexOfActive(choices, "com.z/.Dream,com.a/.Dream") == 1,
                "An Android dream list identifies its first selected component");
        check(Screensavers.indexOfActive(choices, null) == -1,
                "An unset screensaver does not select an unrelated app");
        check(Screensavers.indexOfActive(choices, "com.removed/.Dream") == -1,
                "An uninstalled selection does not select the first app");
        check(Screensavers.indexOfActive(Collections.<Screensavers.Choice>emptyList(), null) == -1,
                "An empty TV has no active row");
        check(TimeoutValues.milliseconds("1") == 60000, "Minute values use milliseconds");
        check(TimeoutValues.milliseconds("35791") == 2147460000, "The maximum timer does not overflow");
        for (String invalid : Arrays.asList("0", "-1", "35792", "1.5", "", "2147483647")) {
            boolean rejected = false;
            try {
                TimeoutValues.milliseconds(invalid);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            check(rejected, "Invalid timer is rejected: " + invalid);
        }
        check(TimeoutValues.label(0).equals("Disabled"), "A disabled timer is shown accurately");
        check(TimeoutValues.label(15000).equals("15 seconds"), "Existing sub-minute delays are not rounded");
        check(TimeoutValues.deepSleepAutoWakeValue(false) == 1,
                "Deep sleep off keeps Alexa auto-wake available");
        check(TimeoutValues.deepSleepAutoWakeValue(true) == 0,
                "Deep sleep on disables Alexa auto-wake");
        check(TimeoutValues.activeDeepSleepTimeout(1800000) == 1800000,
                "The deep sleep timer remains independent of the deep sleep toggle");
        check(TimeoutValues.activeDeepSleepTimeout(0) == 0,
                "A disabled deep sleep timer stays disabled");
        boolean rejectedDeepSleepTimer = false;
        try {
            TimeoutValues.activeDeepSleepTimeout(-1);
        } catch (IllegalArgumentException expected) {
            rejectedDeepSleepTimer = true;
        }
        check(rejectedDeepSleepTimer, "Negative deep sleep timers are rejected");
        System.out.println(checks + " screensaver checks passed.");
    }
}
