package com.jeremykenedy.firetv.ui.nativeapp.folders;

import java.util.Arrays;
import java.util.Collections;

public final class FolderOptionsTest {
    private static final String A = "example.player/example.player.Main";
    private static final String B = "example.game/example.game.Play";
    private static final String MISSING = "example.removed/example.removed.Main";
    public static void main(String[] args) {
        FolderOptions original = FolderOptions.empty().create("stable-a", "Movies").create("stable-b", "Games")
                .addApp("stable-a", A).addApp("stable-a", MISSING).addApp("stable-a", B);
        FolderOptions renamed = original.rename("stable-a", "  Watch  ");
        check("Watch".equals(renamed.find("stable-a").name), "Rename keeps identity");
        check("Movies".equals(original.find("stable-a").name), "Immutable original");
        check(renamed.find("stable-a").components.equals(Arrays.asList(A, MISSING, B)), "Missing app retained");
        check(renamed.addApp("stable-a", A).find("stable-a").components.size() == 3, "No duplicate apps");
        FolderOptions moved = renamed.moveApp("stable-a", B, -1).move("stable-b", -1);
        check(moved.folders.get(0).id.equals("stable-b"), "Folder order saved");
        check(moved.find("stable-a").components.equals(Arrays.asList(A, B, MISSING)), "App order includes missing entries");
        check(moved.move("stable-b", -1).folders.get(0).id.equals("stable-b"), "Boundary stable");
        check(moved.removeApp("stable-a", MISSING).find("stable-a").components.equals(Arrays.asList(A, B)), "Explicit missing removal");
        check(moved.remove("stable-a").folders.size() == 1, "Only requested folder removed");
        check(original.find("stable-a").components.contains(A), "Removing folder cannot mutate original");
        FolderOptions multiple = original.addApp("stable-b", A);
        check(multiple.find("stable-a").components.contains(A) && multiple.find("stable-b").components.contains(A), "App may belong to multiple folders");
        expect(new Runnable() { public void run() { original.create("stable-a", "Duplicate"); } });
        expect(new Runnable() { public void run() { original.rename("stable-a", " \n "); } });
        expect(new Runnable() { public void run() { original.rename("absent", "Name"); } });
        expect(new Runnable() { public void run() { original.addApp("stable-a", "https://example.com"); } });
        expect(new Runnable() { public void run() { original.moveApp("stable-a", A, 8); } });
        check(FolderOptions.validComponent("example.app/.Main$Inner"), "Short component supported");
        FolderOptions aliases = FolderOptions.empty().create("alias", "Alias")
                .addApp("alias", "example.app/.Main$Inner")
                .addApp("alias", "example.app/example.app.Main$Inner");
        check(aliases.find("alias").components.equals(Arrays.asList("example.app/example.app.Main$Inner")),
                "Short and full component aliases share the inventory and lock identity");
        check(aliases.removeApp("alias", "example.app/.Main$Inner").find("alias").components.isEmpty(),
                "Short component alias removes the canonical member");
        FolderOptions aliasOrder = aliases.addApp("alias", A).moveApp("alias", "example.app/.Main$Inner", 1);
        check(aliasOrder.find("alias").components.equals(Arrays.asList(A, "example.app/example.app.Main$Inner")),
                "Short component alias moves the canonical member");
        check(!FolderOptions.validComponent("example.app/."), "Invalid class rejected");
        check(!FolderOptions.validComponent("example.app/.Main\n"), "Control text rejected");
        boolean immutable = false;
        try { original.find("stable-a").components.add(B); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "Component list immutable");
        String first = FolderOptions.empty().create("Same name").folders.get(0).id;
        String second = FolderOptions.empty().create("Same name").folders.get(0).id;
        check(!first.equals(second), "Identity does not depend on label");
        FolderOptions full = FolderOptions.empty();
        for (int i = 0; i < FolderOptions.MAX_FOLDERS; i++) full = full.create("id-" + i, "Folder " + i);
        final FolderOptions limit = full;
        expect(new Runnable() { public void run() { limit.create("Overflow"); } });
        System.out.println("Folder behavior checks passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void expect(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected invalid operation rejection");
    }
}
