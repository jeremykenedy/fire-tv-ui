package com.jeremykenedy.firetv.ui.nativeapp.appgrid;

import com.jeremykenedy.firetv.ui.nativeapp.folders.FolderOptions;
import com.jeremykenedy.firetv.ui.nativeapp.inputs.InputEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class HomeTileOrder {
    public static final int MAX_TILES = 4096;
    public final List<String> order;

    public HomeTileOrder(List<String> order) {
        Set<String> unique = new LinkedHashSet<>();
        for (String key : order) {
            if (!validKey(key) || !unique.add(key)) throw new IllegalArgumentException("Invalid Home tile order.");
        }
        if (unique.size() > MAX_TILES) throw new IllegalArgumentException("Too many Home tiles.");
        this.order = Collections.unmodifiableList(new ArrayList<>(unique));
    }

    public static boolean validKey(String key) {
        if (key == null) return false;
        if (key.startsWith("input:")) return InputEntry.validId(key.substring(6));
        if (key.startsWith("folder:")) {
            String id = key.substring(7);
            if (id.isEmpty() || id.length() > 160) return false;
            for (int i = 0; i < id.length(); i++) if (Character.isISOControl(id.charAt(i))) return false;
            return true;
        }
        return FolderOptions.validComponent(key);
    }

    public List<String> visible(AppGridOptions apps, List<String> availableApps,
            FolderOptions folders, List<InputEntry> inputs) {
        Set<String> members = new LinkedHashSet<>();
        List<String> available = new ArrayList<>();
        for (FolderOptions.Folder folder : folders.folders) members.addAll(folder.components);
        for (String key : apps.visible(availableApps)) {
            int slash = key.indexOf('/');
            String canonical = key.charAt(slash + 1) == '.'
                    ? key.substring(0, slash + 1) + key.substring(0, slash) + key.substring(slash + 1) : key;
            if (!members.contains(canonical)) available.add(key);
        }
        for (FolderOptions.Folder folder : folders.folders) available.add("folder:" + folder.id);
        for (InputEntry input : inputs) if (!input.hidden) available.add(input.artworkKey());
        List<String> arranged = new ArrayList<>(order);
        for (String key : available) if (!arranged.contains(key)) arranged.add(key);
        List<String> result = new ArrayList<>();
        for (boolean favorite : new boolean[]{true, false}) {
            for (String key : arranged)
                if (available.contains(key) && apps.favorites.contains(key) == favorite) result.add(key);
        }
        return result;
    }

    public HomeTileOrder move(String key, int delta, List<String> visible, Set<String> favorites) {
        if (delta != -1 && delta != 1) throw new IllegalArgumentException("Move one tile at a time.");
        new HomeTileOrder(visible);
        int from = visible.indexOf(key), to = from + delta;
        if (from < 0 || to < 0 || to >= visible.size()) return this;
        String other = visible.get(to);
        if (favorites.contains(key) != favorites.contains(other)) return this;
        List<String> changed = new ArrayList<>(order);
        for (String value : visible) if (!changed.contains(value)) changed.add(value);
        Collections.swap(changed, changed.indexOf(key), changed.indexOf(other));
        return new HomeTileOrder(changed);
    }
}
