package com.jeremykenedy.firetv.ui.nativeapp.device;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.AtomicFile;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** No-backup recovery journal containing only the six Home booleans and their absence. */
final class HomeCommandStore implements HomeCommandPolicy.Store {
    private static final Set<String> KEYS = new HashSet<>(Arrays.asList(HomeSetupPolicy.LEGACY_HOME,
            "start_on_boot", "override_home_button", "better_home_detection",
            HomeSetupPolicy.HOME_KEYS, HomeSetupPolicy.HOME_WINDOWS));
    private final Context context;
    private final SharedPreferences preferences;
    private final AtomicFile journal;

    HomeCommandStore(Context context) {
        this.context = context;
        preferences = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE);
        journal = new AtomicFile(new File(context.getNoBackupFilesDir(), "source-home-command-recovery"));
    }
    @Override public Map<String, ?> preferences() { return preferences.getAll(); }
    @Override public boolean write(Map<String, Boolean> changes) {
        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, Boolean> entry : changes.entrySet()) {
            if (!KEYS.contains(entry.getKey())) throw new IllegalArgumentException("Unknown Home setting.");
            if (entry.getValue() == null) editor.remove(entry.getKey());
            else editor.putBoolean(entry.getKey(), entry.getValue());
        }
        return editor.commit();
    }
    @Override public boolean routeMatches(boolean enabled) {
        String value = Settings.Secure.getString(context.getContentResolver(), "enabled_accessibility_services");
        boolean keys = false;
        boolean windows = false;
        if (value != null) for (String item : value.split(":")) {
            ComponentName component = ComponentName.unflattenFromString(item);
            if (new ComponentName(HomeRoutingPolicy.PRODUCTION_PACKAGE, HomeRoutingPolicy.PRODUCTION_PACKAGE + ".Hra").equals(component)) keys = true;
            if (new ComponentName(HomeRoutingPolicy.PRODUCTION_PACKAGE, HomeRoutingPolicy.PRODUCTION_PACKAGE + ".HomeRedirectService").equals(component)) windows = true;
        }
        return enabled ? keys && windows && Settings.Secure.getInt(context.getContentResolver(), "accessibility_enabled", 0) == 1
                : !keys && !windows;
    }
    @Override public Map<String, Boolean> pending() {
        if (!journal.getBaseFile().exists() && !new File(journal.getBaseFile().getPath() + ".bak").exists()) return null;
        try (DataInputStream input = new DataInputStream(journal.openRead())) {
            if (!"SOURCE-HOME-1".equals(input.readUTF())) throw new IOException("Invalid recovery header.");
            int count = input.readInt();
            if (count < 1 || count > KEYS.size()) throw new IOException("Invalid recovery size.");
            Map<String, Boolean> before = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                String key = input.readUTF();
                int state = input.readByte();
                if (!KEYS.contains(key) || before.containsKey(key) || state < 0 || state > 2) {
                    throw new IOException("Invalid recovery value.");
                }
                before.put(key, state == 0 ? null : state == 2);
            }
            if (input.read() != -1) throw new IOException("Unexpected recovery data.");
            return before;
        } catch (IOException failed) {
            throw new IllegalStateException("Home recovery state could not be read.", failed);
        }
    }
    @Override public boolean journal(Map<String, Boolean> before) {
        FileOutputStream output = null;
        try {
            output = journal.startWrite();
            DataOutputStream data = new DataOutputStream(output);
            data.writeUTF("SOURCE-HOME-1"); data.writeInt(before.size());
            for (Map.Entry<String, Boolean> entry : before.entrySet()) {
                data.writeUTF(entry.getKey());
                data.writeByte(entry.getValue() == null ? 0 : entry.getValue() ? 2 : 1);
            }
            data.flush(); journal.finishWrite(output);
            return before.equals(pending());
        } catch (IOException | RuntimeException failed) {
            if (output != null) journal.failWrite(output);
            return false;
        }
    }
    @Override public boolean clearJournal() {
        journal.delete();
        return !journal.getBaseFile().exists() && !new File(journal.getBaseFile().getPath() + ".bak").exists();
    }
}
