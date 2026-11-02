package com.jeremykenedy.firetv.ui.nativeapp.device.screensavers;

public final class LegacyDreamSelectionTest {
    public static void main(String[] args) {
        Store store = new Store(); store.fail = true;
        try { LegacyDreamSelection.select(store, "fixture.new/.Dream"); throw new AssertionError(); }
        catch (IllegalStateException expected) { check("fixture.old/.Dream".equals(store.value)); }
        store.value = "fixture.old/.Dream"; store.compete = true;
        try { LegacyDreamSelection.select(store, "fixture.new/.Dream"); throw new AssertionError(); }
        catch (IllegalStateException expected) { check("fixture.other/.Dream".equals(store.value)); }
        System.out.println("Screensaver selection failure restores prior choice and preserves competing changes.");
    }
    static class Store implements LegacyDreamSelection.Store {
        String value = "fixture.old/.Dream"; boolean fail, compete;
        public String component() { return value; } public int enabled() { return 0; }
        public void component(String wanted) { value = wanted; }
        public void enable() { if (compete) { value = "fixture.other/.Dream"; throw new IllegalStateException(); }
            if (fail) throw new IllegalStateException(); }
    }
    static void check(boolean value) { if (!value) throw new AssertionError("Selection transaction regression."); }
}
