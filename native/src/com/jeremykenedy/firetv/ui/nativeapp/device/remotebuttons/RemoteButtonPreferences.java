package com.jeremykenedy.firetv.ui.nativeapp.device.remotebuttons;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RemoteButtonPreferences {
    public static final String FILE = "native_remote_buttons";
    private final SharedPreferences values;
    public RemoteButtonPreferences(Context context) { values = context.getSharedPreferences(FILE, Context.MODE_PRIVATE); }
    public List<Integer> learned() {
        List<Integer> result = new ArrayList<>();
        for (int key = 289; key <= 300; key++) if (values.contains("label:" + key)) result.add(key);
        return result;
    }
    public String label(int key) { return values.getString("label:" + key, "App button") + " (key " + key + ")"; }
    public String target(int key) { return values.getString("target:" + key, ""); }
    public boolean system(int key) { return values.getBoolean("system:" + key, false); }
    public boolean hasSystemMappings() {
        for (int key : learned()) if (system(key) && !target(key).isEmpty()) return true;
        return false;
    }
    public void learn(int key, String label) throws IOException {
        requireKey(key);
        save(values.edit().putString("label:" + key, label));
    }
    public void map(int key, String target, boolean system) throws IOException {
        requireKey(key);
        if (!learned().contains(key)) throw new IllegalArgumentException("Learn this button first.");
        if (!RemoteButtonDispatcher.validTarget(target)) throw new IllegalArgumentException("Select an app or action.");
        save(values.edit().putString("target:" + key, target).putBoolean("system:" + key, system));
    }
    public void restore(int key) throws IOException {
        requireKey(key);
        save(values.edit().remove("target:" + key).remove("system:" + key));
    }
    public void restoreAll() throws IOException {
        SharedPreferences.Editor edit = values.edit();
        for (int key = 289; key <= 300; key++) edit.remove("target:" + key).remove("system:" + key);
        save(edit);
    }
    private void requireKey(int key) {
        if (!RemoteButtonPolicy.supported(key)) throw new IllegalArgumentException("This is not a supported app shortcut key.");
    }
    private void save(SharedPreferences.Editor edit) throws IOException {
        Map<String, ?> previous = values.getAll();
        if (edit.commit()) return;
        SharedPreferences.Editor restore = values.edit();
        for (int key = 289; key <= 300; key++) {
            for (String prefix : new String[]{"label:", "target:", "system:"}) {
                String name = prefix + key;
                Object value = previous.get(name);
                if (value instanceof String) restore.putString(name, (String) value);
                else if (value instanceof Boolean) restore.putBoolean(name, (Boolean) value);
                else if (value == null) restore.remove(name);
            }
        }
        restore.commit();
        throw new IOException("Remote button settings could not be saved. Try again.");
    }
}
