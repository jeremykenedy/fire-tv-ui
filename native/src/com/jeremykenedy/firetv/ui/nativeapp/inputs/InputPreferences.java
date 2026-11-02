package com.jeremykenedy.firetv.ui.nativeapp.inputs;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

public final class InputPreferences {
    public static final String FILE = "native_inputs";
    private final SharedPreferences values;
    public InputPreferences(Context context) { values = context.getSharedPreferences(FILE, Context.MODE_PRIVATE); }
    public Set<String> knownIds() {
        Set<String> result = new LinkedHashSet<>();
        for (String key : values.getAll().keySet()) {
            if (key.startsWith("known:") && InputEntry.validId(key.substring(6))) result.add(key.substring(6));
        }
        return result;
    }
    public String factoryName(String id) { return values.getString("factory:" + id, "TV input"); }
    public String customName(String id) { return values.getString("name:" + id, null); }
    public void remember(String id, String label) throws IOException {
        if (!InputEntry.validId(id)) return;
        Set<String> known = knownIds();
        if (known.contains(id) && factoryName(id).equals(label)) return;
        if (!known.contains(id) && known.size() >= 256) return;
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
