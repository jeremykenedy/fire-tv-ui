package com.jeremykenedy.firetv.ui.nativeapp.appgrid;

import android.content.Context;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class AppGridPreferencesTest {
    public static void main(String[] args) {
        Context context = new Context();
        AppGridPreferences.saveDiscoveredOrder(context, Collections.<String>emptyList());
        check(context.prefs.values.isEmpty(), "Empty discovery preserves every absent preference");
        AppGridPreferences.saveDiscoveredOrder(context, Arrays.asList("example.one/.Main"));
        check(context.prefs.values.size() == 1 && context.prefs.values.containsKey("order"),
                "Discovery writes only order without materializing default grid controls");
        context.prefs.values.put("columns", 16);
        context.prefs.values.put("horizontal", false);
        context.prefs.values.put("hidden", "example.one/.Main");
        context.prefs.values.put("future", "preserved");
        check(AppGridPreferences.load(context).columns == 16
                && AppGridPreferences.load(context).hidden.contains("example.one/.Main"),
                "A saved sixteen column grid loads without resetting app choices");
        Map<String, Object> before = new HashMap<>(context.prefs.values);
        AppGridPreferences.saveDiscoveredOrder(context, Arrays.asList("example.two/.Main", "example.one/.Main"));
        check("example.one/.Main\nexample.two/.Main".equals(context.prefs.values.get("order")),
                "Existing and missing component order retained while new apps append");
        for (String key : Arrays.asList("columns", "horizontal", "hidden", "future"))
            check(before.get(key).equals(context.prefs.values.get(key)), "Discovery preserves explicit " + key);
        check(!context.prefs.values.containsKey("rows") && !context.prefs.values.containsKey("favorites"),
                "Discovery preserves absent rows and favorites");
        before = new HashMap<>(context.prefs.values);
        int commits = context.prefs.commits;
        AppGridPreferences.saveDiscoveredOrder(context, Collections.<String>emptyList());
        check(before.equals(context.prefs.values) && commits == context.prefs.commits, "No rewrite when inventory loses apps");
        context.prefs.fail = true;
        reject(context, Arrays.asList("example.three/.Main"));
        check(before.equals(context.prefs.values), "Failed discovery commit restores previous exact order");
        context.prefs.fail = false;
        context.prefs.values.clear(); context.prefs.fail = true;
        reject(context, Arrays.asList("example.one/.Main"));
        check(context.prefs.values.isEmpty(), "Failed first discovery restores absent order");
        context.prefs.fail = false;
        java.util.List<String> retained = new java.util.ArrayList<>();
        for (int i = 0; i < AppGridOptions.MAX_IDENTITIES; i++) retained.add("old.app" + i + "/.Main");
        context.prefs.values.put("order", String.join("\n", retained));
        before = new HashMap<>(context.prefs.values);
        commits = context.prefs.commits;
        AppGridPreferences.saveDiscoveredOrder(context, Arrays.asList("new.app/.Main"));
        check(before.equals(context.prefs.values) && commits == context.prefs.commits,
                "Full order discovery neither overwrites historical identities nor writes unrelated defaults");
        context.prefs.values.put("order", "unsupported legacy sentinel");
        before = new HashMap<>(context.prefs.values);
        reject(context, Arrays.asList("example.one/.Main"));
        check(before.equals(context.prefs.values), "Malformed order retained without reset");
        context.prefs.values.put("order", 123);
        before = new HashMap<>(context.prefs.values);
        reject(context, Arrays.asList("example.one/.Main"));
        check(before.equals(context.prefs.values), "Wrong-typed order retained without reset");
        Context saved = new Context();
        AppGridOptions wide = AppGridOptions.defaults().discover(Arrays.asList("example.one/.Main"))
                .toggleFavorite("example.one/.Main").grid(16, 2, true);
        AppGridPreferences.save(saved, wide);
        AppGridOptions restored = AppGridPreferences.load(saved);
        check(restored.columns == 16 && restored.rows == 2 && restored.horizontal
                && restored.order.equals(wide.order) && restored.favorites.equals(wide.favorites),
                "Sixteen columns and app choices survive preference save and reload");
        System.out.println("Discovery preserves absent controls, explicit values, missing order and failed writes");
    }
    private static void reject(Context context, java.util.List<String> apps) {
        try { AppGridPreferences.saveDiscoveredOrder(context, apps); }
        catch (IllegalStateException expected) { return; }
        throw new AssertionError("Expected discovery to fail without resetting saved preferences");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
