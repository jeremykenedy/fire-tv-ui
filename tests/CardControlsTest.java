package com.jeremykenedy.firetv.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.view.KeyEvent;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import org.json.JSONObject;

public final class CardControlsTest {
    public static void main(String[] args) {
        Context context = new Context();
        String key = String.format("%064x", 42);
        String source = "com.example.player";
        Uri originalIntent = new Uri();
        int[] opened = {0};
        Runnable open = new Runnable() { @Override public void run() { opened[0]++; } };
        check("Player".equals(FeaturedCardMenu.label("Player")), "Cards must not show the Menu instruction suffix.");
        for (int code : new int[]{KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER}) {
            int shown = AlertDialog.shown;
            check(FeaturedCardMenu.handle(context, new KeyEvent(0, code, false), source, originalIntent, 7, key, "||Current title", open),
                    "Action down is consumed before the inherited provider callback.");
            check(AlertDialog.shown == shown, "Action down cannot launch or open duplicate menus.");
            check(FeaturedCardMenu.handle(context, new KeyEvent(1, code, true), source, originalIntent, 7, key, "||Current title", open), "Canceled action consumed.");
            check(AlertDialog.shown == shown, "Canceled action cannot open menus.");
            check(FeaturedCardMenu.handle(context, new KeyEvent(1, code, false), source, originalIntent, 7, key, "||Current title", open), "Every action key opens controls.");
            check("||Current title".equals(AlertDialog.last.title), "Menu names the exact current title without prefix guessing.");
            check(Arrays.asList(AlertDialog.last.options).containsAll(Arrays.asList("Open", "Hide this title", "Hide all cards from Player")),
                    "Provider, one-title and whole-source actions are clearly separated.");
            check(opened[0] == 0, "Opening controls never invokes the original provider action.");
            AlertDialog.last.choose("Cancel");
        }
        check(!FeaturedCardMenu.handle(context, new KeyEvent(1, 21, false), source, originalIntent, 7, key, "Title", open), "Directional navigation remains delegated.");
        FeaturedCardMenu.show(context, source, originalIntent, 7, key, "Title", open);
        AlertDialog.last.choose("Open");
        check(opened[0] == 1, "Only explicit Open invokes the untouched original provider callback.");
        FeaturedCardMenu.show(context, source, originalIntent, 7, key, "Same route Beta", open);
        AlertDialog.last.choose("Hide this title");
        check(key.equals(HiddenCards.hidden), "Hide consumes the carried exact item key, not title or shared route.");
        FeaturedCardMenu.show(context, source, originalIntent, 7, null, "Unidentified item", open);
        check(!Arrays.asList(AlertDialog.last.options).contains("Hide this title"), "Missing identity never guesses an item hide.");
        AlertDialog.last.choose("Open");
        check(opened[0] == 2, "Missing identity preserves functional original Open.");
        check(FeaturedCardClock.paused == 0, "Closing every action restores card rotation.");

        JSONObject original = new JSONObject().put(source, new JSONObject().put("showIcon", false).put("custom", "keep"))
                .put("com.other.app", new JSONObject().put("hideWatchNext", false));
        String encoded = original.toString();
        context.prefs.values.put("app_visibility", encoded); context.prefs.values.put("other-setting", "keep");
        FeaturedCardMenu.hideSource(context, source);
        JSONObject changed = new JSONObject((String) context.prefs.values.get("app_visibility"));
        check(Boolean.TRUE.equals(changed.optJSONObject(source).get("hideWatchNext")), "Whole source hide writes the existing Boolean choice.");
        check(Boolean.FALSE.equals(changed.optJSONObject(source).get("showIcon")) && "keep".equals(changed.optJSONObject(source).get("custom"))
                && Boolean.FALSE.equals(changed.optJSONObject("com.other.app").get("hideWatchNext")), "Other app metadata and other sources are preserved.");
        context.prefs.values.put("app_visibility", encoded);
        Map<String,Object> before = new HashMap<>(context.prefs.values); context.prefs.failNext = true;
        try { FeaturedCardMenu.hideSource(context, source); throw new AssertionError("Failed write accepted."); }
        catch (IllegalStateException expected) { check(before.equals(context.prefs.values), "Failed commit restores exact prior preference, including in-memory update."); }
        for (Object invalid : new Object[]{42, "malformed", new JSONObject().put(source, "invalid app entry").toString()}) {
            context.prefs.values.put("app_visibility", invalid); before = new HashMap<>(context.prefs.values);
            try { FeaturedCardMenu.hideSource(context, source); throw new AssertionError("Invalid existing state overwritten."); }
            catch (IllegalStateException expected) { check(before.equals(context.prefs.values), "Malformed or typed existing state remains untouched."); }
        }
        context.prefs.values.remove("app_visibility"); before = new HashMap<>(context.prefs.values); context.prefs.failNext = true;
        try { FeaturedCardMenu.hideSource(context, source); throw new AssertionError("Failed new setting accepted."); }
        catch (IllegalStateException expected) { check(before.equals(context.prefs.values), "Failed first write restores prior absence."); }
        System.out.println("Select/Menu controls, explicit original Open, exact title hide, missing identity and source-setting recovery passed.");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
