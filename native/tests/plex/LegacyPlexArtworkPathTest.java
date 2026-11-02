package com.jeremykenedy.firetv.ui.nativeapp.providers.plex;

public final class LegacyPlexArtworkPathTest {
    public static void main(String[] args) {
        String packageName = "fixture.launcher", file = new String(new char[64]).replace('\0', 'a') + ".jpg";
        String base = "content://" + packageName + ".plex.art/" + file;
        check(file.equals(LegacyPlexArtworkPath.name(base, packageName)));
        for (String bad : new String[]{base + "?secret=1", base + "#fragment", base + "/more",
                "content://other.plex.art/" + file, "content://" + packageName + ".plex.art:99/" + file,
                "content://user@" + packageName + ".plex.art/" + file, "content://" + packageName + ".plex.art/%61" + file.substring(1),
                "content://" + packageName + ".plex.art/../" + file, "content://" + packageName + ".plex.art/" + file.toUpperCase()})
            check(LegacyPlexArtworkPath.name(bad, packageName) == null);
        System.out.println("Retained Plex artwork authority and exact raw filename validation passed.");
    }
    private static void check(boolean value) { if (!value) throw new AssertionError("Legacy Plex art regression."); }
}
