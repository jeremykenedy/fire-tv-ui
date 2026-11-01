package com.jeremykenedy.firetv.ui.nativeapp.appgrid;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class AppGridTest {
    public static void main(String[] args) {
        String a = "a.app/.Main", b = "b.app/.Main", c = "c.app/.Main";
        List<String> available = Arrays.asList(a, b, c);
        AppGridOptions options = AppGridOptions.defaults().discover(available);
        check(options.columns == 5 && options.rows == 0 && !options.horizontal, "Current default grid retained");
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
        for (int columns : new int[] {-1, 0, 1, 13}) {
            try { options.grid(columns, 0, false); throw new AssertionError("Invalid column accepted"); } catch (IllegalArgumentException expected) {}
        }
        try { options.discover(Arrays.asList("bad\nkey")); throw new AssertionError("Invalid identity accepted"); } catch (IllegalArgumentException expected) {}
        check(options.move(a, -1, available) == options, "First item cannot move outside list");
        System.out.println("App order, missing app retention, favorites, hide/show, custom grid and input bounds passed.");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
