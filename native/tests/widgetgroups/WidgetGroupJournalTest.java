package com.jeremykenedy.firetv.ui.nativeapp.widgetgroups;

import com.jeremykenedy.firetv.ui.nativeapp.migration.LauncherMigration;
import com.jeremykenedy.firetv.ui.nativeapp.migration.LegacyPresentationMapper;
import com.jeremykenedy.firetv.ui.nativeapp.migration.PreferenceSnapshot;
import com.jeremykenedy.firetv.ui.nativeapp.migration.PresentationSnapshot;
import com.jeremykenedy.firetv.ui.nativeapp.migration.PresentationTransaction;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WidgetGroupJournalTest {
    public static void main(String[] args) throws Exception {
        String raw = "[[],[{\"id\":\"00000000-0000-4000-8000-000000000001\",\"type\":\"Future\",\"size\":\"Big\",\"data\":{\"x\":true}}]]";
        Map<String, Object> source = new LinkedHashMap<>(); source.put("widgets_config", raw); source.put("enable_widgets", false);
        Map<String, PreferenceSnapshot> files = new LinkedHashMap<>();
        for (String name : LegacyPresentationMapper.FILES) files.put(name, new PreferenceSnapshot(Collections.emptyMap()));
        files.put(LauncherMigration.PREFERENCES, new PreferenceSnapshot(source));
        PresentationSnapshot before = new PresentationSnapshot(files);
        Map<String, PreferenceSnapshot> afterFiles = new LinkedHashMap<>(files);
        afterFiles.put(LegacyPresentationMapper.LAYOUT, new PreferenceSnapshot(WidgetGroupMigration.patch(source, Collections.emptyMap())));
        PresentationSnapshot after = new PresentationSnapshot(afterFiles);
        Store store = new Store(before); store.interrupt = true;
        try { PresentationTransaction.apply(store, before, after); throw new AssertionError("No interruption."); }
        catch (Death expected) { }
        check(store.journal != null && !before.sameValues(store.read()), "Interrupted write retains original journal.");
        PresentationTransaction.recover(store, store.journal);
        check(before.sameValues(store.read()), "Recovery restores exact legacy and native values.");
        store.journal = null; store.interrupt = false;
        PresentationTransaction.apply(store, before, after);
        check(after.sameValues(store.read()), "Retry publishes original JSON without clearing legacy data.");
        check(raw.equals(store.read().stores.get(LegacyPresentationMapper.LAYOUT).values().get(WidgetGroupMigration.KEY)), "Exact source bytes retained.");
        check(Boolean.FALSE.equals(store.read().stores.get(LauncherMigration.PREFERENCES).values().get("enable_widgets")), "Disabled choice remains unchanged.");
        enabledPlan();
        System.out.println("Grouped widget journal interruption and retry passed.");
    }
    private static void enabledPlan() throws Exception {
        String raw = "[[],[{\"id\":\"00000000-0000-4000-8000-000000000001\",\"type\":\"YearProgress\",\"size\":\"Big\",\"data\":{}}],[],[]]";
        for (boolean hide : new boolean[]{false, true}) {
            Map<String, Object> legacy = new LinkedHashMap<>();
            legacy.put("widgets_config", raw); legacy.put("enable_widgets", true); legacy.put("hide_watch_next", hide);
            PreferenceSnapshot source = new PreferenceSnapshot(legacy);
            LegacyPresentationMapper.Plan base = LegacyPresentationMapper.plan(source,
                    com.jeremykenedy.firetv.ui.nativeapp.migration.PresentationMappingContext.empty(), Collections.emptyMap());
            WidgetGroupPresentationPlan plan = WidgetGroupPresentationPlan.prepare(base);
            check(plan.unmappedLegacyKeys.isEmpty(), "Enabled groups and explicit content flags are resolved together.");
            for (Map.Entry<String, Object> entry : legacy.entrySet())
                check(entry.getValue().equals(plan.target.stores.get(LauncherMigration.PREFERENCES).values().get(entry.getKey())),
                        "Original enabled settings retain their exact values.");
            check((hide ? "NONE" : "WIDGETS_ONLY").equals(plan.target.stores.get(LegacyPresentationMapper.LAYOUT).values().get("mode")),
                    "Journal target carries the source layout semantics.");
            Store store = new Store(plan.before); store.interrupt = true;
            try { PresentationTransaction.apply(store, plan.before, plan.target); throw new AssertionError("No interruption."); }
            catch (Death expected) { }
            PresentationTransaction.recover(store, store.journal);
            check(plan.before.sameValues(store.read()), "Interrupted enabled publication restores all source and native settings.");
            store.journal = null;
            PresentationTransaction.apply(store, plan.before, plan.target);
            check(plan.target.sameValues(store.read()), "Retry publishes enabled groups and flags as one target.");
        }
    }
    private static final class Store implements PresentationTransaction.Store {
        Map<String, PreferenceSnapshot> values;
        PresentationSnapshot journal;
        boolean interrupt;
        Store(PresentationSnapshot initial) { values = new LinkedHashMap<>(initial.stores); }
        @Override public Object lock() { return this; }
        @Override public PresentationSnapshot read() { return new PresentationSnapshot(values); }
        @Override public void saveRecovery(PresentationSnapshot snapshot) { journal = snapshot; }
        @Override public boolean replace(String name, PreferenceSnapshot snapshot) {
            values.put(name, snapshot);
            if (interrupt) { interrupt = false; throw new Death(); }
            return true;
        }
    }
    private static final class Death extends Error { private static final long serialVersionUID = 1L; }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
