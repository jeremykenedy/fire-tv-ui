package com.jeremykenedy.firetv.ui.nativeapp.widgetgroups;

import com.jeremykenedy.firetv.ui.nativeapp.migration.LegacyPresentationMapper;
import com.jeremykenedy.firetv.ui.nativeapp.migration.LauncherMigration;
import com.jeremykenedy.firetv.ui.nativeapp.migration.PreferenceSnapshot;
import com.jeremykenedy.firetv.ui.nativeapp.migration.PresentationSnapshot;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Prepares widget groups as part of the presentation journal target. */
public final class WidgetGroupPresentationPlan {
    public final PresentationSnapshot before;
    public final PresentationSnapshot target;
    public final Set<String> unmappedLegacyKeys;

    private WidgetGroupPresentationPlan(PresentationSnapshot before, PresentationSnapshot target, Set<String> unmapped) {
        this.before = before;
        this.target = target;
        this.unmappedLegacyKeys = Collections.unmodifiableSet(new LinkedHashSet<>(unmapped));
    }

    public static WidgetGroupPresentationPlan prepare(LegacyPresentationMapper.Plan base) {
        if (base == null) throw new IllegalArgumentException("Missing presentation plan.");
        Map<String, Object> legacy = base.before.get(LauncherMigration.PREFERENCES).values();
        Map<String, Object> layout = new LinkedHashMap<>(base.after.get(LegacyPresentationMapper.LAYOUT).values());
        Map<String, Object> patch = WidgetGroupMigration.supportedPatch(legacy, layout);
        layout.putAll(patch);
        Map<String, PreferenceSnapshot> after = new LinkedHashMap<>(base.after);
        after.put(LegacyPresentationMapper.LAYOUT, new PreferenceSnapshot(layout));
        Set<String> unmapped = new LinkedHashSet<>(base.unmappedLegacyKeys);
        if (!patch.isEmpty()) unmapped.remove("widgets_config");
        return new WidgetGroupPresentationPlan(new PresentationSnapshot(base.before), new PresentationSnapshot(after), unmapped);
    }
}
