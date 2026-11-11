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
        legacy.remove("schema_version"); legacy.remove("hidden_series"); legacy.remove("item_order"); legacy.remove("hidden_packages");
        check(CardOptions.fromBackup(legacy).globalSeconds == value.globalSeconds && CardOptions.fromBackup(legacy).isHidden("com.first.app", "item-2"), "Existing v1 settings retain timers and item hides.");
        Map<String, Object> version2 = new HashMap<>(value.backupValues());
        version2.put("schema_version", 2); version2.remove("hidden_packages");
        check(CardOptions.fromBackup(version2).globalSeconds == value.globalSeconds, "Version two card snapshots remain readable.");
        Context packageContext = new Context();
        packageContext.prefs.values.put("unknown_future", "keep");
        check(CardPreferences.hiddenPackages(packageContext).isEmpty() && packageContext.prefs.values.size() == 1,
                "Reading absent package choices does not materialize defaults.");
        CardPreferences.setPackageHidden(packageContext, "com.first.app", true);
        check(packageContext.prefs.values.size() == 2 && "keep".equals(packageContext.prefs.values.get("unknown_future")),
                "Package visibility writes only its own preference.");
        TvRecommendations.Feed one = new TvRecommendations.Feed("tv:one", "One", "com.first.app", Collections.emptyList());
        TvRecommendations.Feed future = new TvRecommendations.Feed("notify:future", "Future", "com.first.app", Collections.emptyList());
        TvRecommendations.Feed other = new TvRecommendations.Feed("plex:other", "Other", "com.other.app", Collections.emptyList());
        check(CardPreferences.hiddenFeedKeys(packageContext, Arrays.asList(one, future, other))
                .equals(new java.util.LinkedHashSet<>(Arrays.asList("channel:tv:one", "channel:notify:future"))),
                "A hidden package suppresses existing and future feeds while other apps remain visible.");
        Map<String, Boolean> packageChanges = new HashMap<>(); packageChanges.put("com.other.app", true);
        CardPreferences.setPackagesHidden(packageContext, packageChanges);
        check(CardPreferences.isPackageHidden(packageContext, "com.first.app") && CardPreferences.isPackageHidden(packageContext, "com.other.app"),
                "A batch update preserves packages missing from the manager changes.");
        CardPreferences.setPackageHidden(packageContext, "com.first.app", false);
        check(!CardPreferences.isPackageHidden(packageContext, "com.first.app") && CardPreferences.isPackageHidden(packageContext, "com.other.app"),
                "Showing one package preserves independent choices.");
        CardOptions packageSnapshot = CardPreferences.load(packageContext);
        check(packageSnapshot.hiddenPackages.contains("com.other.app")
                && CardOptions.fromBackup(packageSnapshot.withGlobalSeconds(15).backupValues()).hiddenPackages.contains("com.other.app"),
                "Card edits and backup round trip preserve package visibility.");
        Map<String, Object> packageBefore = new HashMap<>(packageContext.prefs.values);
        packageContext.prefs.fail = true;
        try { CardPreferences.setPackageHidden(packageContext, "com.first.app", true); throw new AssertionError("Failed package save accepted"); }
        catch (IllegalStateException expected) { check(packageBefore.equals(packageContext.prefs.values), "Failed package save restores exact previous choices."); }
        packageContext.prefs.fail = false;
        try { CardPreferences.setPackageHidden(packageContext, "com.first.app/.Main", true); throw new AssertionError("Component accepted as package"); }
        catch (IllegalArgumentException expected) { check(packageBefore.equals(packageContext.prefs.values), "Invalid package cannot change saved choices."); }
        com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction.writeBlocked = true;
        try { CardPreferences.setPackageHidden(packageContext, "com.first.app", true); throw new AssertionError("Pending recovery write accepted"); }
        catch (IllegalStateException expected) { check(packageBefore.equals(packageContext.prefs.values), "Pending recovery leaves package choices untouched."); }
        com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction.writeBlocked = false;
        com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction.readBlocked = true;
        try { CardPreferences.hiddenPackages(packageContext); throw new AssertionError("Pending recovery read accepted"); }
        catch (IllegalStateException expected) {}
        check(CardPreferences.hiddenFeedKeys(packageContext, Arrays.asList(one, other)).size() == 2,
                "Blocked renderer reads suppress cards without exposing partial settings or throwing.");
        com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction.readBlocked = false;
        Context context = new Context();
        check(CardPreferences.artworkFits(context), "Absent artwork choice preserves Fit default.");
        CardPreferences.setArtworkFit(context, false);
        check(!CardPreferences.artworkFits(context), "Saved Fill choice survives reload.");
        context.prefs.fail = true;
        try { CardPreferences.setArtworkFit(context, true); throw new AssertionError("Failed artwork write accepted."); }
        catch (IllegalStateException expected) { check(!CardPreferences.artworkFits(context), "Failed artwork save retains Fill."); }
        context.prefs.fail = false;
        CardPreferences.setArtworkFit(context, true);
        check(CardPreferences.artworkFits(context), "Saved Fit choice survives reload.");
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
        for (String invalid : new String[]{"com.first.app\ncom.first.app", "com.first.app\n", "com.first.app/.Main"}) {
            Map<String, Object> backup = new HashMap<>(value.backupValues()); backup.put("hidden_packages", invalid); reject(context, backup, before);
        }
        Map<String,Object> wrongVersion = new HashMap<>(value.backupValues()); wrongVersion.put("schema_version", 4); reject(context, wrongVersion, before);
        CardOptions bounded = CardOptions.defaults();
        for (int i = 0; i < 512; i++) bounded = bounded.hide("com.first.app", "item" + i, "Title");
        try { bounded.hide("com.first.app", "extra", "Title"); throw new AssertionError("Unbounded hides accepted."); }
        catch (IllegalArgumentException expected) {}
        CardOptions preserved = bounded;
        for (int i = 0; i < 2048; i++) preserved = preserved.hideLegacy(String.format("%064x", i));
        check(CardOptions.fromBackup(preserved.backupValues()).hiddenItems().size() == 2560,
                "All 2048 original choices coexist with 512 native hides and survive backup.");
        try { preserved.hideLegacy(String.format("%064x", 2048)); throw new AssertionError("Unbounded legacy hides accepted"); }
        catch (IllegalArgumentException expected) {}
        for (String malformed : new String[]{"abc", String.format("%064x", 1).toUpperCase() + "A", "../item"}) {
            try { CardOptions.defaults().hideLegacy(malformed); throw new AssertionError("Malformed legacy hash accepted"); }
            catch (IllegalArgumentException expected) {}
        }
        String legacyKey = String.format("%064x", 42);
        TvRecommendations.Program legacyItem = new TvRecommendations.Program("native:first", "com.first.app", "First", null, "fixture://shared", null, null);
        TvRecommendations.Program legacyOther = new TvRecommendations.Program("native:second", "com.first.app", "Second", null, "fixture://shared", null, null);
        legacyItem.legacyKey = legacyKey; legacyOther.legacyKey = String.format("%064x", 43);
        CardOptions oldChoice = CardOptions.defaults().hideLegacy(legacyKey);
        check(CardPreferences.itemHidden(oldChoice, legacyItem) && !CardPreferences.itemHidden(oldChoice, legacyOther),
                "Exact old hash hides only its item despite shared route.");
        check(!CardPreferences.itemHidden(CardPreferences.restoreItem(oldChoice, legacyItem), legacyItem),
                "Explicit restore removes the preserved choice.");
        check(CardOptions.fromBackup(oldChoice.backupValues()).restore(CardOptions.LEGACY_RECOMMENDATIONS, legacyKey).hiddenItems().isEmpty(),
                "Unavailable choice can be restored without a current provider or title.");
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
