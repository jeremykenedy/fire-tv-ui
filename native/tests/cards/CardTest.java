package com.jeremykenedy.firetv.ui.nativeapp.cards;

import android.content.Context;
import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class CardTest {
    public static void main(String[] args) {
        CardOptions value = CardOptions.defaults();
        check(value.globalSeconds == 8 && value.timerSeconds("tv:one") == 8, "Preserve existing eight second default.");
        value = value.withFeedSeconds("tv:one", 0).withFeedSeconds("tv:two", 86400).withGlobalSeconds(1);
        check(value.timerSeconds("tv:one") == 0 && value.timerSeconds("tv:two") == 86400, "Overrides include manual and full custom bounds.");
        value = value.useGlobal("tv:one");
        check(value.timerSeconds("tv:one") == 1 && value.timerSeconds("tv:two") == 86400, "Use global removes only chosen override.");
        value = value.withGlobalSeconds(0);
        check(value.timerSeconds("tv:one") == 0, "Global manual applies to inherited timers.");
        value = value.hide("com.first.app", "item-1", "Episode 1\nSpecial edition 日本語")
                .hide("com.first.app", "item-2", "Same title").hide("com.other.app", "item-1", "Same title");
        check(value.isHidden("com.first.app", "item-1") && !value.isHidden("com.third.app", "item-1"), "Hidden keys are source scoped.");
        check(!value.isHidden("com.first.app", "item-3"), "Titles never substitute for provider identity.");
        value = value.restore("com.first.app", "item-1");
        check(!value.isHidden("com.first.app", "item-1") && value.isHidden("com.other.app", "item-1")
                && value.isHidden("com.first.app", "item-2"), "Restore preserves independent hidden items and sources.");
        check(CardOptions.fromBackup(value.backupValues()).backupValues().equals(value.backupValues()), "Settings round trip.");
        CardOptions unicode = value.hide("com.first.app", "unicode", "Title\twith\nseparators 日本語");
        check(CardOptions.fromBackup(unicode.backupValues()).backupValues().equals(unicode.backupValues()), "Unicode and display separators cannot alter item identity.");
        CardOptions grouped = value.hideSeries("com.first.app", "series:one", "A series")
                .hide("com.first.app", "episode:one", "Episode one");
        check(grouped.isHidden("com.first.app", "episode:future", "series:one"), "Series rule covers future episodes without matching titles.");
        check(!grouped.isHidden("com.other.app", "episode:future", "series:one"), "Series rules cannot cross sources.");
        check(!grouped.isHidden("com.first.app", "episode:future", null), "Missing trusted identity never infers a series.");
        grouped = CardOptions.fromBackup(grouped.backupValues());
        check(grouped.isHidden("com.first.app", "episode:future", "series:one"), "Series rule survives serialization.");
        grouped = grouped.restoreSeries("com.first.app", "series:one");
        check(!grouped.isHidden("com.first.app", "episode:future", "series:one")
                && grouped.isHidden("com.first.app", "episode:one", "series:one"), "Restoring series retains independent episode rule.");
        CardOptions ordered = grouped.discoverItems("com.first.app", "tv:one", Arrays.asList("a", "b", "c"))
                .moveItemBefore("com.first.app", "tv:one", "c", "a");
        check(ordered.orderedKeys("com.first.app", "tv:one", Arrays.asList("a", "b", "c")).equals(Arrays.asList("c", "a", "b")), "Custom feed title order is applied.");
        check(ordered.orderedKeys("com.other.app", "tv:one", Arrays.asList("a", "b", "c")).equals(Arrays.asList("a", "b", "c")), "Title order is source scoped.");
        ordered = ordered.discoverItems("com.first.app", "tv:one", Arrays.asList("b", "new"));
        check(ordered.orderedKeys("com.first.app", "tv:one", Arrays.asList("b", "new")).equals(Arrays.asList("b", "new")), "New items append and missing items do not render.");
        check(ordered.orderedKeys("com.first.app", "tv:one", Arrays.asList("a", "b", "c", "new")).equals(Arrays.asList("c", "a", "b", "new")), "Unavailable titles return to their saved positions.");
        check(CardOptions.fromBackup(ordered.backupValues()).backupValues().equals(ordered.backupValues()), "Order and hidden entries round trip together.");
        check(ordered.resetItemOrder("com.first.app", "tv:one").orderedKeys("com.first.app", "tv:one", Arrays.asList("a", "b", "c")).equals(Arrays.asList("a", "b", "c")), "Reset restores source order only.");
        TvRecommendations.Program a = new TvRecommendations.Program("a", "com.first.app", "Episode", null, "", "series:one", "Series");
        TvRecommendations.Program b = new TvRecommendations.Program("b", "com.first.app", "Episode", null, "", null, null);
        TvRecommendations.Program c = new TvRecommendations.Program("c", "com.first.app", "Movie", null, "", null, null);
        TvRecommendations.Feed feed = new TvRecommendations.Feed("tv:one", "Channel", "com.first.app", Arrays.asList(a,b,c));
        check(CardPreferences.visiblePrograms(ordered.hideSeries("com.first.app", "series:one", "Series"), feed).equals(Arrays.asList(c,b)), "Render helper applies title order then trusted series filtering.");
        Map<String,Object> legacy = new HashMap<>(value.backupValues());
        legacy.remove("schema_version"); legacy.remove("hidden_series"); legacy.remove("item_order");
        check(CardOptions.fromBackup(legacy).globalSeconds == value.globalSeconds && CardOptions.fromBackup(legacy).isHidden("com.first.app", "item-2"), "Existing v1 settings retain timers and item hides.");
        Context context = new Context();
        context.prefs.values.put("unrelated", "keep");
        CardPreferences.save(context, value);
        check(CardPreferences.load(context).backupValues().equals(value.backupValues()), "Settings survive reload.");
        Map<String, Object> before = new HashMap<>(context.prefs.values);
        for (int seconds : new int[]{-1, 86401, Integer.MAX_VALUE}) {
            try { value.withGlobalSeconds(seconds); throw new AssertionError("Invalid timer accepted."); }
            catch (IllegalArgumentException expected) {}
        }
        for (Object invalid : new Object[]{"8", 8L, -1, 86401}) {
            Map<String, Object> backup = new HashMap<>(value.backupValues());
            backup.put("global_seconds", invalid);
            reject(context, backup, before);
        }
        for (String invalid : new String[]{"tv:one\t1\ntv:one\t2", "tv:one\t86401", "tv:one\t+5", "tv:one\t1\n"}) {
            Map<String, Object> backup = new HashMap<>(value.backupValues());
            backup.put("feed_seconds", invalid);
            reject(context, backup, before);
        }
        for (String invalid : new String[]{"bad-source\tkey\tVGl0bGU=", "com.first.app\tkey\t%%%", "com.first.app\tkey\t\ncom.first.app\tkey\t"}) {
            Map<String, Object> backup = new HashMap<>(value.backupValues());
            backup.put("hidden_items", invalid);
            reject(context, backup, before);
        }
        for (String invalid : new String[]{"com.first.app\ttv:one\ta\ta", "com.first.app\ttv:one\ta\ncom.first.app\ttv:one\tb", "bad-source\ttv:one\ta"}) {
            Map<String, Object> backup = new HashMap<>(value.backupValues()); backup.put("item_order", invalid); reject(context, backup, before);
        }
        Map<String,Object> wrongVersion = new HashMap<>(value.backupValues()); wrongVersion.put("schema_version", 3); reject(context, wrongVersion, before);
        CardOptions bounded = CardOptions.defaults();
        for (int i = 0; i < 512; i++) bounded = bounded.hide("com.first.app", "item" + i, "Title");
        try { bounded.hide("com.first.app", "extra", "Title"); throw new AssertionError("Unbounded hides accepted."); }
        catch (IllegalArgumentException expected) {}
        CardOptions timers = CardOptions.defaults();
        for (int i = 0; i < 256; i++) timers = timers.withFeedSeconds("tv:" + i, 1);
        try { timers.withFeedSeconds("tv:extra", 1); throw new AssertionError("Unbounded overrides accepted."); }
        catch (IllegalArgumentException expected) {}
        context.prefs.fail = true;
        try { CardPreferences.save(context, value.withGlobalSeconds(30)); throw new AssertionError("Failed write accepted."); }
        catch (IllegalStateException expected) { check(before.equals(context.prefs.values), "Failed write restores prior settings."); }
        context.prefs.fail = false;
        context.prefs.values.put("hidden_items", "malformed");
        check(CardPreferences.load(context).globalSeconds == 8, "Malformed persisted data falls back safely.");
        System.out.println("Card timers, trusted series rules, independent episodes, source-scoped item order, v1 migration, persistence and atomic validation passed.");
    }

    private static void reject(Context context, Map<String, Object> values, Map<String, Object> before) {
        try { CardPreferences.restore(context, values); throw new AssertionError("Malformed import accepted."); }
        catch (IllegalArgumentException expected) { check(before.equals(context.prefs.values), "Invalid import cannot mutate settings."); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
