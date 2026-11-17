package com.jeremykenedy.firetv.ui.nativeapp.migration;

import com.jeremykenedy.firetv.ui.nativeapp.appgrid.RetainedHomeChoices;
import java.util.*;

public final class UnavailableChoicesMigrationTest {
    private static final String ANCHOR = "fixture.anchor/fixture.anchor.Main";
    private static final String RETURNING = "fixture.returning/fixture.returning.Main";
    private static final String INPUT = "fixture.input/.Service/HDMI1";
    private static final String URI = "file:///private/hdmi.png";
    private static final String IMAGE = "image:11111111-1111-4111-8111-111111111111.png";
    public static void main(String[] args) {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("display_order", "fixture.returning,tvinput_name:HDMI 1,fixture.anchor");
        original.put("app_visibility", "{\"fixture.returning\":{\"hidden\":true},\"tvinput_name:HDMI 1\":{\"hidden\":false,\"hideWatchNext\":false,\"customIconUri\":\"" + URI + "\"}}");
        original.put("input_names", "{\"tvinput_name:HDMI 1\":\"Console\"}");
        original.put("factory_icon_packages", Collections.singleton("fixture.returning"));
        PreferenceSnapshot legacy = new PreferenceSnapshot(original);
        PresentationMappingContext context = context(Arrays.asList(ANCHOR), Collections.<String, String>emptyMap());
        LegacyPresentationMapper.Plan plan = LegacyPresentationMapper.plan(legacy, context, Collections.<String, PreferenceSnapshot>emptyMap());
        for (Map.Entry<String, Object> entry : original.entrySet())
            check(entry.getValue().equals(plan.after.get(LauncherMigration.PREFERENCES).values().get(entry.getKey())), "Original typed setting changed: " + entry.getKey());
        check(plan.unmappedLegacyKeys.isEmpty(), "Supported unavailable choices must have an actual deferred consumer.");
        Map<String, Object> grid = plan.after.get(LegacyPresentationMapper.GRID).values();
        Map<String, Object> inputs = plan.after.get(LegacyPresentationMapper.INPUTS).values();
        Map<String, Object> icons = plan.after.get(LegacyPresentationMapper.ICONS).values();
        check(grid.get("order").equals(ANCHOR) && grid.get("home_order").equals(ANCHOR), "Only verified platform identities enter current native order.");
        check(!inputs.containsKey("known:tvinput_name:HDMI 1"), "An unavailable input must not become a fabricated native input.");
        check(RetainedHomeChoices.read(RetainedHomeChoices.ICONS, icons.get(RetainedHomeChoices.KEY)).get("icon:tvinput_name:HDMI 1").equals(IMAGE), "Prepared private icon choice remains attached to the exact original reference.");
        Map<String, String> returnedApps = RetainedHomeChoices.appIdentities(Arrays.asList(RETURNING, ANCHOR));
        Map<String, String> returnedInputs = Collections.singletonMap("tvinput_name:HDMI 1", "input:" + INPUT);
        grid = RetainedHomeChoices.promoteGrid(grid, returnedApps);
        grid = RetainedHomeChoices.promoteGrid(grid, returnedInputs);
        inputs = RetainedHomeChoices.promoteInputs(inputs, returnedInputs);
        icons = RetainedHomeChoices.promoteIcons(icons, returnedApps);
        icons = RetainedHomeChoices.promoteIcons(icons, returnedInputs);
        check(grid.get("home_order").equals(RETURNING + "\ninput:" + INPUT + "\n" + ANCHOR), "Returning identities consume the original mixed ordering.");
        check(grid.get("hidden").equals(RETURNING), "Returning app consumes retained visibility.");
        check(inputs.get("name:" + INPUT).equals("Console") && Boolean.FALSE.equals(inputs.get("hidden:" + INPUT)), "Returning input consumes its original name and explicit visibility.");
        check(icons.get(RETURNING).equals("factory") && icons.get("input:" + INPUT).equals(IMAGE), "App and input icon choices are consumed by real identities.");
        check(!grid.containsKey(RetainedHomeChoices.KEY) && !inputs.containsKey(RetainedHomeChoices.KEY) && !icons.containsKey(RetainedHomeChoices.KEY), "Fully consumed records do not replay later.");

        try {
            LegacyPresentationMapper.plan(legacy, context(Arrays.asList(ANCHOR, RETURNING, "fixture.returning/fixture.returning.Second"), Collections.<String, String>emptyMap()), Collections.<String, PreferenceSnapshot>emptyMap());
            throw new AssertionError("Ambiguous installed app accepted.");
        } catch (IllegalArgumentException expected) {}
        Map<String, String> ambiguousInputs = new LinkedHashMap<>();
        ambiguousInputs.put(INPUT, "HDMI 1"); ambiguousInputs.put("fixture.input/.Second", "HDMI 1");
        try {
            LegacyPresentationMapper.plan(legacy, context(Arrays.asList(ANCHOR), ambiguousInputs), Collections.<String, PreferenceSnapshot>emptyMap());
            throw new AssertionError("Ambiguous platform input accepted.");
        } catch (IllegalArgumentException expected) {}
        check(context(Arrays.asList(ANCHOR), Collections.singletonMap(INPUT, "hdmi 1")).availableInput("tvinput_name:HDMI 1") == null, "Input names are never matched by case folding or labels guessed from IDs.");
        System.out.println("Unavailable legacy app/input choices retain exact references, promote through native consumers and refuse ambiguous inventories.");
    }
    private static PresentationMappingContext context(List<String> components, Map<String, String> labels) {
        return new PresentationMappingContext(IdentityInventory.components(components),
                Collections.<String, PresentationMappingContext.Feed>emptyMap(), Collections.<String, PresentationMappingContext.Item>emptyMap(),
                Collections.singletonMap(URI, IMAGE), IdentityInventory.inputs(labels), Collections.<String>emptySet(),
                Collections.<PrivateMediaReference>emptyList(), IdentityInventory.ambiguousInputs(labels));
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
