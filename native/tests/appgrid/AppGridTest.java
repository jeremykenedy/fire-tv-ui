package com.jeremykenedy.firetv.ui.nativeapp.appgrid;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AppGridTest {
    public static void main(String[] args) {
        String a = "a.app/.Main", b = "b.app/.Main", c = "c.app/.Main";
        List<String> available = Arrays.asList(a, b, c);
        AppGridOptions options = AppGridOptions.defaults().discover(available);
        check(options.columns == 6 && options.rows == 1 && options.horizontal, "Existing launcher defaults to six apps in a horizontal tray");
        options = options.move(c, -1, available);
        check(options.visible(available).equals(Arrays.asList(a, c, b)), "Move changes selected position");
        check(options.visible(Arrays.asList(a, b)).equals(Arrays.asList(a, b)), "Missing app disappears without fabricated tile");
        check(options.discover(Arrays.asList(a, b)).visible(available).equals(Arrays.asList(a, c, b)), "Returning app retains order");
        options = options.toggleFavorite(b);
        check(options.visible(available).equals(Arrays.asList(b, a, c)), "Favorites lead while ordinary order remains");
        options = options.toggleHidden(b);
        check(options.visible(available).equals(Arrays.asList(a, c)), "Hide overrides favorite without deleting it");
        options = options.toggleHidden(b).toggleFavorite(b);
        check(options.visible(available).equals(Arrays.asList(a, c, b)), "Show and unfavorite restore original position");
        options = options.grid(9, 2, true);
        check(options.columns == 9 && options.rows == 2 && options.horizontal && options.visible(available).equals(Arrays.asList(a, c, b)), "Custom dimensions preserve app choices");
        for (int columns : new int[] {1, 2, 12, 13, 16, 20}) {
            AppGridOptions retained = options.grid(columns, 2, true);
            check(retained.columns == columns && retained.rows == 2 && retained.horizontal
                    && retained.visible(available).equals(options.visible(available)),
                    "Saved legacy dimensions preserve exact columns and app choices");
        }
        for (int columns : new int[] {-1, 0, 21}) {
            try { options.grid(columns, 0, false); throw new AssertionError("Invalid column accepted"); } catch (IllegalArgumentException expected) {}
        }
        try { options.discover(Arrays.asList("bad\nkey")); throw new AssertionError("Invalid identity accepted"); } catch (IllegalArgumentException expected) {}
        check(options.move(a, -1, available) == options, "First item cannot move outside list");
        capacityPreservation();
        System.out.println("App order, missing app retention, favorites, hide/show, custom grid and input bounds passed.");
    }
    private static void capacityPreservation() {
        List<String> historical = new ArrayList<>();
        for (int i = 0; i < AppGridOptions.MAX_IDENTITIES; i++) historical.add("old.app" + i + "/.Main");
        String fresh = "new.app/.Main", newer = "newer.app/.Main", retained = historical.get(10);
        List<String> available = Arrays.asList(retained, fresh, newer);
        AppGridOptions full = new AppGridOptions(7, 2, true, historical,
                Collections.<String>emptySet(), Collections.<String>emptySet());
        AppGridOptions discovered = full.discover(available);
        check(discovered.order.equals(historical), "Full historical order retains every missing identity without cap crash");
        check(discovered.visible(available).equals(available), "Fresh installed apps remain visible when remembered order is full");
        check(discovered.unrememberedCount(available) == 2, "Capacity state reports unremembered installed apps");
        check(discovered.move(fresh, -1, available) == discovered, "Moving an overflow app cannot use a missing saved index");
        check(discovered.move(retained, 1, available) == discovered, "Moving across overflow boundary cannot discard saved identity");
        AppGridOptions favorite = discovered.toggleFavorite(newer);
        check(favorite.visible(available).equals(Arrays.asList(newer, retained, fresh)), "Overflow apps still honor favorites");
        check(favorite.toggleHidden(newer).visible(available).equals(Arrays.asList(retained, fresh)), "Overflow apps still honor hide");
        AppGridOptions partial = new AppGridOptions(5, 0, false, historical.subList(0, historical.size() - 1),
                Collections.<String>emptySet(), Collections.<String>emptySet()).discover(available);
        check(partial.order.size() == AppGridOptions.MAX_IDENTITIES && partial.order.get(partial.order.size() - 1).equals(fresh),
                "Remaining remembered capacity fills deterministically");
        check(partial.unrememberedCount(available) == 1 && partial.visible(available).equals(available),
                "Overflow after partial discovery remains visible and reported");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
