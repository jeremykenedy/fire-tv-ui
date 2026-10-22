package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.os.Environment;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

public final class BackupMigrationTest {
    private static final String REMOTE = "fire_tv_ui_equipment_remote";

    private static void check(boolean value) {
        if (!value) throw new AssertionError("Device-local recognition was not preserved");
    }

    public static void main(String[] args) throws Exception {
        Environment.directory = new java.io.File(args[0]);
        Context context = new Context();
        context.prefs.values.put(REMOTE, "destination-identity");
        context.prefs.values.put("theme", "old");
        Files.writeString(BackupStore.file(true).toPath(), new JSONObject()
                .put(REMOTE, "source-identity").put("theme", "new").toString());
        BackupStore.restore(context, true);
        check("destination-identity".equals(context.prefs.values.get(REMOTE)));
        check("new".equals(context.prefs.values.get("theme")));
        BackupStore.save(context);
        JSONObject exported = new JSONObject(Files.readString(BackupStore.file(false).toPath()));
        check(!exported.has(REMOTE) && exported.getString("theme").equals("new"));
        context.prefs.values.remove(REMOTE);
        BackupStore.restore(context, true);
        check(!context.prefs.values.containsKey(REMOTE));
        context.prefs.values.put(REMOTE, "destination-identity");
        Map<String,Object> before = new HashMap<>(context.prefs.values);
        Files.writeString(BackupStore.file(true).toPath(), new JSONObject()
                .put(REMOTE, "source-identity").put("theme", 123).toString());
        try { BackupStore.restore(context, true); throw new AssertionError("Invalid backup accepted"); }
        catch (org.json.JSONException expected) { check(before.equals(context.prefs.values)); }
        Files.writeString(BackupStore.file(true).toPath(), new JSONObject().put(REMOTE, "source-identity").toString());
        try { BackupStore.restore(context, true); throw new AssertionError("Identity-only backup accepted"); }
        catch (org.json.JSONException expected) { check(before.equals(context.prefs.values)); }
        System.out.println("Device-local backup migration checks passed.");
    }
}
