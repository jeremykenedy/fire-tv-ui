package com.jeremykenedy.firetv.ui.nativeapp.widget.quickactions;

import java.util.Arrays;

public final class QuickActionsTest {
    public static void main(String[] args) {
        QuickActions.Entry app = QuickActions.Entry.create("Video", "com.example.video/.Main", "");
        QuickActions.Entry link = QuickActions.Entry.create("Watchlist", "com.example.video/.Main", "https://video.example/watchlist");
        QuickActions actions = QuickActions.empty().put(app, true).put(link, true);
        QuickActions restored = QuickActions.decode(actions.encode());
        check(restored.entries.get(0).id.equals(app.id) && restored.entries.get(1).id.equals(link.id), "Storage reload must retain exact identity and order.");
        QuickActions edited = restored.put(link.edit("Saved titles", "https://video.example/saved"), false).move(link.id, -1);
        check(edited.entries.get(0).id.equals(link.id) && edited.entries.get(0).component.equals(link.component), "Editing and moving must retain source/identity.");
        check(edited.remove(app.id).entries.size() == 1, "Removing one action must retain the other.");
        check(actions.entries.get(0).label.equals("Video"), "Edits must not mutate a prior snapshot.");
        rejects(new Runnable() { public void run() { new QuickActions(Arrays.asList(app, app)); } });
        rejects(new Runnable() { public void run() { QuickActions.empty().put(app, false); } });
        rejects(new Runnable() { public void run() { app.edit("\tName", ""); } });
        rejects(new Runnable() { public void run() { QuickActions.Entry.create("Name", "app label", ""); } });
        String[] unsafe = {"http://video.example/title", "intent://video#Intent;end", "file:///private/data", "content://private/item",
            "javascript:alert(1)", "android.resource://app/1", "data:text/html,test", "package:com.example", "custom://power/off",
            "https://user:password@video.example/a", "https://video.example/a#token", "https:/missing-host", "https://video.example:99999/a", "https://video.example/a\nother"};
        for (final String url : unsafe) rejects(new Runnable() { public void run() { app.edit("Name", url); } });
        QuickActions.validateUrl("https://video.example/title?id=42");
        QuickActions full = QuickActions.empty();
        for (int i = 0; i < QuickActions.MAX_ACTIONS; i++) full = full.put(QuickActions.Entry.create("App " + i, app.component, ""), true);
        final QuickActions maximum = full;
        rejects(new Runnable() { public void run() { maximum.put(link, true); } });
        rejects(new Runnable() { public void run() { QuickActions.decode("v2"); } });
        rejects(new Runnable() { public void run() { QuickActions.decode("v1\nmalformed"); } });
        System.out.println("Quick action stable identity/order/edit/remove, bounds and URI safety tests passed.");
    }

    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("Invalid action must be rejected."); }
        catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
