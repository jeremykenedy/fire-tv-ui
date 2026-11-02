package com.jeremykenedy.firetv.ui.nativeapp.device.screensavers;

import java.util.Arrays;
import java.util.List;

public final class ScreensaverEntryTest {
    public static void main(String[] args) {
        check("pkg/pkg.Dream".equals(ScreensaverEntry.normalize("pkg/.Dream")), "short service names normalize");
        check("pkg/pkg.Dream".equals(ScreensaverEntry.firstActive("pkg/.Dream,other/.Dream")), "first configured service is active");
        check("".equals(ScreensaverEntry.firstActive(null)), "no selected saver remains empty");
        List<ScreensaverEntry> entries = ScreensaverEntry.sorted(Arrays.asList(
                new ScreensaverEntry("z/.Dream", "Zen"), new ScreensaverEntry("a/.Dream", "Aerial"),
                new ScreensaverEntry("z/z.Dream", "Zen")));
        check(entries.size() == 2, "query and installed-package results deduplicate");
        check("Aerial".equals(entries.get(0).label), "installed list sorts by label");
        check(!entries.get(0).component.equals(ScreensaverEntry.firstActive(null)), "inventory never selects its first saver");
        System.out.println("Screensaver inventory rules passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
