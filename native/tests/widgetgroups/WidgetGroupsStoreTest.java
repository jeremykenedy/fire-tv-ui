package com.jeremykenedy.firetv.ui.nativeapp.widgetgroups;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WidgetGroupsStoreTest {
    public static void main(String[] args) {
        FakePrefs prefs = new FakePrefs(); WidgetGroupsStore store = new WidgetGroupsStore(new FakeContext(prefs));
        prefs.values.put("mode", "WIDGETS_ONLY");
        WidgetGroups groups = WidgetGroups.decode("[[]]");
        check(store.load() == null && store.save(null, groups), "New group saved.");
        String raw = store.raw();
        check("WIDGETS_ONLY".equals(prefs.values.get("mode")), "Other layout choices retained.");
        check(!store.save("stale", groups.appendEmptyGroup()) && raw.equals(store.raw()), "Stale edits cannot overwrite current groups.");
        prefs.failNext = true;
        check(!store.save(raw, groups.appendEmptyGroup()) && raw.equals(store.raw()), "Failed commit restores original in-memory value.");
        prefs.values.put(WidgetGroupMigration.KEY, 1);
        try { store.load(); throw new AssertionError("Wrong typed groups accepted."); }
        catch (IllegalStateException expected) { check(Integer.valueOf(1).equals(prefs.values.get(WidgetGroupMigration.KEY)), "Invalid data remains intact."); }
        System.out.println("Grouped widget store conflict and failed-write recovery passed.");
    }
    private static final class FakeContext extends Context {
        final FakePrefs prefs;
        FakeContext(FakePrefs prefs) { this.prefs = prefs; }
        @Override public SharedPreferences getSharedPreferences(String name, int mode) { return prefs; }
    }
    private static final class FakePrefs implements SharedPreferences {
        final Map<String, Object> values = new LinkedHashMap<>(); boolean failNext;
        @Override public Map<String, ?> getAll() { return new LinkedHashMap<>(values); }
        @Override public boolean contains(String key) { return values.containsKey(key); }
        @Override public Editor edit() { return new Editor() {
            final Map<String, Object> changes = new LinkedHashMap<>();
            @Override public Editor putString(String key, String value) { changes.put(key, value); return this; }
            @Override public Editor remove(String key) { changes.put(key, null); return this; }
            @Override public boolean commit() {
                for (Map.Entry<String, Object> item : changes.entrySet()) {
                    if (item.getValue() == null) values.remove(item.getKey()); else values.put(item.getKey(), item.getValue());
                }
                if (failNext) { failNext = false; return false; }
                return true;
            }
        }; }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
