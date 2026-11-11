package com.jeremykenedy.firetv.ui.nativeapp.appgrid;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

public final class RetainedHomeChoicesTest {
    private static final String ANCHOR = "fixture.anchor/fixture.anchor.Main";
    private static final String RETURNING = "fixture.returning/fixture.returning.Main";
    private static final String INPUT = "fixture.input/.Service/HDMI1";
    private static final String IMAGE = "image:11111111-1111-4111-8111-111111111111.png";
    public static void main(String[] args) {
        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("order", "legacy-app:fixture.returning\n" + ANCHOR);
        pending.put("home_order", "legacy-app:fixture.returning\nlegacy-input:HDMI 1\n" + ANCHOR);
        pending.put("hidden:fixture.returning", true);
        Map<String, Object> grid = new LinkedHashMap<>();
        grid.put("order", ANCHOR); grid.put("home_order", ANCHOR);
        grid.put(RetainedHomeChoices.KEY, RetainedHomeChoices.write(RetainedHomeChoices.GRID, pending));
        check(RetainedHomeChoices.promoteGrid(grid, Collections.<String, String>emptyMap()).equals(grid), "An absent app is retained without new defaults or invented components.");
        Map<String, String> apps = RetainedHomeChoices.appIdentities(Arrays.asList(RETURNING, ANCHOR));
        Map<String, Object> arrived = RetainedHomeChoices.promoteGrid(grid, apps);
        check(arrived.get("order").equals(RETURNING + "\n" + ANCHOR), "A returning app occupies its original place.");
        check(arrived.get("hidden").equals(RETURNING), "A returning app consumes its saved hidden choice.");
        check(RetainedHomeChoices.read(RetainedHomeChoices.GRID, arrived.get(RetainedHomeChoices.KEY)).size() == 1, "Unrelated absent input anchors remain retained.");
        Map<String, String> inputs = Collections.singletonMap("tvinput_name:HDMI 1", "input:" + INPUT);
        Map<String, Object> connected = RetainedHomeChoices.promoteGrid(arrived, inputs);
        check(connected.get("home_order").equals(RETURNING + "\ninput:" + INPUT + "\n" + ANCHOR), "An exact returning input restores mixed Home order.");
        check(!connected.containsKey(RetainedHomeChoices.KEY), "Resolved references are consumed once.");
        connected.put("hidden", "");
        check(RetainedHomeChoices.promoteGrid(connected, apps).get("hidden").equals(""), "A later native unhide is not overridden.");

        Map<String, Object> changed = new LinkedHashMap<>(grid);
        changed.put("order", RETURNING + "\n" + ANCHOR); changed.put("home_order", RETURNING + "\n" + ANCHOR);
        changed.put("hidden", "");
        check(RetainedHomeChoices.promoteGrid(changed, apps).get("hidden").equals(""), "An existing native visibility choice wins over deferred defaults.");
        Map<String, String> ambiguousApps = RetainedHomeChoices.appIdentities(Arrays.asList(RETURNING, "fixture.returning/fixture.returning.Second"));
        check(!ambiguousApps.containsKey("fixture.returning"), "Multiple launch activities never produce a guessed package alias.");
        check(RetainedHomeChoices.promoteGrid(grid, ambiguousApps).equals(grid), "Ambiguous returning packages remain unconsumed.");
        Map<String, String> labels = new LinkedHashMap<>(); labels.put(INPUT, "HDMI 1"); labels.put("fixture.input/.Other", "HDMI 1");
        check(!RetainedHomeChoices.inputIdentities(labels).containsKey("tvinput_name:HDMI 1"), "Ambiguous factory labels never pick an input.");

        Map<String, Object> inputPending = new LinkedHashMap<>();
        inputPending.put("order", "legacy-input:HDMI 1"); inputPending.put("hidden:tvinput_name:HDMI 1", false);
        inputPending.put("name:tvinput_name:HDMI 1", "Console");
        Map<String, Object> inputStore = new LinkedHashMap<>();
        inputStore.put(RetainedHomeChoices.KEY, RetainedHomeChoices.write(RetainedHomeChoices.INPUTS, inputPending));
        Map<String, Object> inputArrived = RetainedHomeChoices.promoteInputs(inputStore, inputs);
        check(inputArrived.get("name:" + INPUT).equals("Console") && Boolean.FALSE.equals(inputArrived.get("hidden:" + INPUT)), "Input label and explicit visible choice are consumed when the real input appears.");
        check(!inputArrived.containsKey(RetainedHomeChoices.KEY), "Input choices do not reapply after native edits.");
        inputStore.put("known:" + INPUT, true); inputStore.put("name:" + INPUT, "My new name");
        check(RetainedHomeChoices.promoteInputs(inputStore, inputs).get("name:" + INPUT).equals("My new name"), "Native input renames remain authoritative.");

        Map<String, Object> iconPending = Collections.<String, Object>singletonMap("icon:fixture.returning", IMAGE);
        Map<String, Object> icons = new LinkedHashMap<>();
        icons.put(RetainedHomeChoices.KEY, RetainedHomeChoices.write(RetainedHomeChoices.ICONS, iconPending));
        check(RetainedHomeChoices.promoteIcons(icons, apps).get(RETURNING).equals(IMAGE), "A retained private icon is consumed by the real returning app.");
        icons.put("edited:" + RETURNING, true);
        check(!RetainedHomeChoices.promoteIcons(icons, apps).containsKey(RETURNING), "An explicit native theme reset is not replaced by an older icon.");

        Context context = new Context(); context.prefs.values.putAll(grid);
        SharedPreferences storedIcons = context.getSharedPreferences(RetainedHomeChoices.ICONS, 0);
        storedIcons.values.put(RetainedHomeChoices.KEY, RetainedHomeChoices.write(RetainedHomeChoices.ICONS, iconPending));
        storedIcons.failures = 1;
        try { RetainedHomePreferences.apps(context, Arrays.asList(RETURNING, ANCHOR)); throw new AssertionError("Failed icon write accepted"); }
        catch (IllegalStateException expected) {}
        check(storedIcons.values.containsKey(RetainedHomeChoices.KEY) && !storedIcons.values.containsKey(RETURNING), "A failed icon promotion restores its retained record.");
        RetainedHomePreferences.apps(context, Arrays.asList(RETURNING, ANCHOR));
        check(storedIcons.values.get(RETURNING).equals(IMAGE), "A retry still applies the icon after app order was already promoted.");
        check(!storedIcons.values.containsKey(RetainedHomeChoices.KEY), "Verified icon write consumes the record atomically.");
        context.prefs.values.put("hidden", "");
        RetainedHomePreferences.apps(context, Arrays.asList(RETURNING, ANCHOR));
        check(context.prefs.values.get("hidden").equals(""), "Discovery does not replay consumed legacy visibility.");
        System.out.println("Unavailable app/input order, visibility and icons promote exactly once; ambiguous identities, native edits and failed writes retain choices.");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
