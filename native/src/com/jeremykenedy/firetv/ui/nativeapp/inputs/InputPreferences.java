package com.jeremykenedy.firetv.ui.nativeapp.inputs;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;

public final class InputPreferences {
    public static final String FILE = "native_inputs";
    private final SharedPreferences values;
    public InputPreferences(Context context) { values = context.getSharedPreferences(FILE, Context.MODE_PRIVATE); }
    public Set<String> knownIds() {
        Set<String> result = new LinkedHashSet<>();
        for (String key : values.getAll().keySet()) {
            for (String prefix : new String[]{"known:", "factory:", "name:", "hidden:"}) {
                if (key.startsWith(prefix) && InputEntry.validId(key.substring(prefix.length())))
                    result.add(key.substring(prefix.length()));
            }
        }
        result.addAll(InputOptions.parseOrder(values.getString("order", "")));
        return result;
    }
    public String factoryName(String id) { return values.getString("factory:" + id, "TV input"); }
    public String customName(String id) { return values.getString("name:" + id, null); }
    public InputOptions options() {
        Set<String> hidden = new LinkedHashSet<>();
        for (Map.Entry<String, ?> entry : values.getAll().entrySet()) {
            if (!entry.getKey().startsWith("hidden:")) continue;
            String id = entry.getKey().substring(7);
            if (!InputEntry.validId(id) || !(entry.getValue() instanceof Boolean))
                throw new IllegalArgumentException("Saved TV input visibility is invalid.");
            if ((Boolean) entry.getValue()) hidden.add(id);
        }
        return new InputOptions(InputOptions.parseOrder(values.getString("order", "")), hidden);
    }
    public void saveOptions(InputOptions options) throws IOException {
        // Keep names, factory labels and unknown keys independent of organization changes.
        options();
        Map<String, ?> previous = values.getAll();
        SharedPreferences.Editor edit = values.edit();
        for (String key : previous.keySet()) if (key.startsWith("hidden:"))
            edit.putBoolean(key, options.hidden.contains(key.substring(7)));
        if (options.order.isEmpty()) edit.remove("order"); else edit.putString("order", options.savedOrder());
        for (String id : options.hidden) edit.putBoolean("hidden:" + id, true);
        if (!edit.commit()) {
            SharedPreferences.Editor restore = values.edit().remove("order");
            for (String key : values.getAll().keySet()) if (key.startsWith("hidden:")) restore.remove(key);
            if (previous.containsKey("order")) restore.putString("order", (String) previous.get("order"));
            for (Map.Entry<String, ?> entry : previous.entrySet())
                if (entry.getKey().startsWith("hidden:")) restore.putBoolean(entry.getKey(), (Boolean) entry.getValue());
            restore.commit();
            throw new IOException("Input organization could not be saved. Try again.");
        }
    }
    public void remember(String id, String label) throws IOException {
        if (!InputEntry.validId(id)) return;
        Set<String> known = knownIds();
        if (known.contains(id) && factoryName(id).equals(label)) return;
        if (!known.contains(id) && known.size() >= InputOptions.MAX_INPUTS) return;
        if (!values.edit().putBoolean("known:" + id, true).putString("factory:" + id, label).commit())
            throw new IOException("Input history could not be saved.");
    }
    public void rename(String id, String name) throws IOException {
        if (!InputEntry.validId(id)) throw new IllegalArgumentException("Select a TV input.");
        String previous = customName(id);
        SharedPreferences.Editor edit = values.edit();
        if (name == null) edit.remove("name:" + id); else edit.putString("name:" + id, InputEntry.name(name));
        if (!edit.commit()) {
            SharedPreferences.Editor restore = values.edit();
            if (previous == null) restore.remove("name:" + id); else restore.putString("name:" + id, previous);
            restore.commit();
            throw new IOException("The input name could not be saved. Try again.");
        }
    }
}
