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
        System.out.println("Grouped widget journal interruption and retry passed.");
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
