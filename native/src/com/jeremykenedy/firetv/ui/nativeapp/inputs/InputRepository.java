package com.jeremykenedy.firetv.ui.nativeapp.inputs;

import android.content.Context;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.RetainedHomePreferences;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;

/** Read-only platform discovery; remembered inputs survive removal and service denial. */
public final class InputRepository {
    public enum Capability { READY, EMPTY, UNAVAILABLE }
    public static final class Snapshot {
        public final List<InputEntry> inputs;
        public final List<InputEntry> visibleInputs;
        public final Capability capability;
        public final String message;
        Snapshot(List<InputEntry> inputs, Capability capability, String message) {
            this.inputs = Collections.unmodifiableList(inputs);
            List<InputEntry> shown = new ArrayList<>();
            for (InputEntry input : inputs) if (!input.hidden) shown.add(input);
            this.visibleInputs = Collections.unmodifiableList(shown);
            this.capability = capability;
            this.message = capability == Capability.READY && !inputs.isEmpty() && shown.isEmpty()
                    ? "All inputs are hidden from Home. Show them from TV inputs settings." : message;
        }
    }
    private InputRepository() {}
    public static Snapshot load(Context context) {
        InputPreferences saved = new InputPreferences(context);
        List<InputEntry> result = new ArrayList<>();
        TvInputManager manager = null;
        List<TvInputInfo> discovered = Collections.emptyList();
        Map<String, String> labels = new LinkedHashMap<>();
        boolean available = false;
        String message = "TV inputs are unavailable on this device.";
        try {
            manager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
            if (manager != null) {
                discovered = manager.getTvInputList();
                for (TvInputInfo input : discovered) {
                    if (!InputEntry.validId(input.getId())) continue;
                    CharSequence label = input.loadLabel(context);
                    labels.put(input.getId(), label == null || label.length() == 0 ? "TV input" : label.toString());
                }
                RetainedHomePreferences.inputs(context, labels);
                available = true;
            }
        } catch (RuntimeException unavailable) {
            discovered = Collections.emptyList();
            message = "This device does not allow TV input discovery or saved choices could not be prepared. Saved choices are kept.";
        }
        final InputOptions options;
        Set<String> retained;
        try {
            options = saved.options();
            retained = saved.knownIds();
        } catch (RuntimeException invalid) {
            return new Snapshot(result, Capability.UNAVAILABLE, "Saved input organization could not be read. Your choices are kept.");
        }
        if (available) {
            for (TvInputInfo input : discovered) {
                String id = input.getId();
                if (!InputEntry.validId(id)) continue;
                String factory = labels.get(id);
                int state;
                try { state = manager.getInputState(id); } catch (RuntimeException unknown) { state = -1; }
                InputEntry.State status = state == TvInputManager.INPUT_STATE_CONNECTED ? InputEntry.State.CONNECTED
                        : state == TvInputManager.INPUT_STATE_CONNECTED_STANDBY ? InputEntry.State.STANDBY
                        : state == TvInputManager.INPUT_STATE_DISCONNECTED ? InputEntry.State.DISCONNECTED : InputEntry.State.UNAVAILABLE;
                result.add(new InputEntry(id, factory, saved.customName(id), status, input.isPassthroughInput(), options.hidden.contains(id)));
                retained.remove(id);
                try { saved.remember(id, factory); } catch (java.io.IOException failure) { message = failure.getMessage(); }
            }
            if (result.isEmpty()) message = "No TV inputs are exposed by this device.";
            else if (message.startsWith("TV inputs")) message = "Select an input to open it or change its name and artwork.";
        }
        for (String id : retained) result.add(new InputEntry(id, saved.factoryName(id), saved.customName(id),
                available ? InputEntry.State.MISSING : InputEntry.State.UNAVAILABLE, false, options.hidden.contains(id)));
        Collections.sort(result, new Comparator<InputEntry>() {
            @Override public int compare(InputEntry a, InputEntry b) {
                int left = options.order.indexOf(a.id), right = options.order.indexOf(b.id);
                if (left < 0) left = Integer.MAX_VALUE;
                if (right < 0) right = Integer.MAX_VALUE;
                return left == right ? a.id.compareTo(b.id) : Integer.compare(left, right);
            }
        });
        return new Snapshot(result, !available ? Capability.UNAVAILABLE : result.isEmpty() ? Capability.EMPTY : Capability.READY, message);
    }
}
