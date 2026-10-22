package com.jeremykenedy.firetv.ui;

import java.util.Arrays;

public final class IdleDispatchTest {
    private static int checks;

    private static void expect(int sdk, boolean enabled, String selected, String registered,
            String preview, String... expected) {
        String[] actual = IdleDispatch.targets(sdk, enabled, selected, registered, preview);
        if (!Arrays.equals(actual, expected)) {
            throw new AssertionError(Arrays.toString(actual));
        }
        checks++;
    }

    public static void main(String[] args) {
        String aquarium = "com.jeremykenedy.firetv.aquarium/.AquariumDreamService";
        String slot = "com.jeremykenedy.reelatrium/.ReelAtriumDreamService";
        String preview = "com.jeremykenedy.reelatrium/.PreviewActivity";
        String expanded = "com.jeremykenedy.reelatrium/com.jeremykenedy.reelatrium.PreviewActivity";
        String hearth = "com.jeremykenedy.hearthpresence/.HearthDreamService";
        String firePreview = "com.jeremykenedy.hearthpresence/.PreviewActivity";
        expect(30, false, aquarium, "", "");
        expect(30, true, null, "", "");
        expect(34, true, "malformed", "", "");
        expect(30, true, aquarium, "", "", IdleDispatch.SYSTEM_STARTER);
        expect(34, true, aquarium, "", "");
        expect(33, true, slot, slot, preview, IdleDispatch.SYSTEM_STARTER, expanded);
        expect(34, true, slot, slot, preview, expanded);
        expect(36, true, hearth, hearth, firePreview,
                "com.jeremykenedy.hearthpresence/com.jeremykenedy.hearthpresence.PreviewActivity");
        expect(30, true, hearth, hearth, firePreview, IdleDispatch.SYSTEM_STARTER,
                "com.jeremykenedy.hearthpresence/com.jeremykenedy.hearthpresence.PreviewActivity");
        expect(34, true, slot, aquarium, preview);
        expect(34, true, slot, slot, "");
        expect(34, true, slot, slot, null);
        expect(30, true, slot, slot, "", IdleDispatch.SYSTEM_STARTER);
        expect(34, true, slot, slot, "com.neilturner.aerialviews/.ui.screensaver.TestActivity");
        expect(34, true, slot, slot, "com.jeremykenedy.reelatrium/invalid-name");
        System.out.println("Idle dispatch checks passed: " + checks);
    }
}
