package com.jeremykenedy.firetv.ui.nativeapp.widget.timers;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.util.Xml;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

/** Reads fixed owned files without loading SharedPreferences or starting an Activity. */
public final class TimerRecoveryInspectionInstrumentation extends Instrumentation {
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final String[] STORES = {"native_layout", "native_local_timers"};
    private static final String[] JOURNALS = {"saved-setup-apply-v1.json", "saved-setup-apply-v1.json.bak",
            "saved-setup-apply-v1.json.new", "portable-household-import-v1.snapshot", "portable-household-import-v1.snapshot.bak"};
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        String stage = "create private snapshot directory";
        try {
            File noBackup = getTargetContext().getNoBackupFilesDir();
            File snapshot = new File(new File(noBackup, "test-state-inspection"), System.currentTimeMillis() + "-" + UUID.randomUUID());
            if (!snapshot.mkdirs()) throw new java.io.IOException();
            privateFile(snapshot);
            File preferences = new File(getTargetContext().getDataDir(), "shared_prefs");
            JSONObject manifest = new JSONObject();
            JSONArray files = new JSONArray();
            stage = "copy exact current owned XML and backup files";
            for (String store : STORES) {
                for (String suffix : new String[]{".xml", ".xml.bak"}) {
                    String name = store + suffix;
                    File source = new File(preferences, name);
                    JSONObject metadata = metadata(source, name);
                    if (source.exists()) {
                        File copy = new File(snapshot, name);
                        copy(source, copy);
                        if (!metadata.getString("sha256").equals(hash(copy)) || source.length() != copy.length()) throw new java.io.IOException();
                        metadata.put("copyVerified", true);
                    }
                    files.put(metadata);
                }
            }
            manifest.put("files", files);
            manifest.put("rawValuesEmitted", false);
            manifest.put("sourceStoresUnchangedByHelper", true);
            byte[] manifestBytes = manifest.toString(2).getBytes(StandardCharsets.UTF_8);
            File manifestFile = new File(snapshot, "manifest.json");
            write(manifestFile, manifestBytes);
            result.putString("snapshot_relative_path", "test-state-inspection/" + snapshot.getName());
            result.putString("snapshot_manifest_sha256", hash(manifestFile));
            result.putString("captured_files", files.toString());
            stage = "parse private copies without SharedPreferences side effects";
            JSONObject stores = new JSONObject();
            for (String store : STORES) {
                File copy = new File(snapshot, store + ".xml");
                stores.put(store, copy.exists() ? summarize(copy, store) : new JSONArray());
            }
            result.putString("owned_keys", stores.toString());
            stage = "inspect fixed private recovery path metadata";
            JSONArray journals = new JSONArray();
            for (String journal : JOURNALS) journals.put(metadata(new File(noBackup, journal), journal));
            result.putString("recovery_files", journals.toString());
            result.putString("result", "PASS: exact private current-state copies preserved; fixed owned files inspected without preference writes or Activity launch");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("error_stage", stage);
            result.putString("error_type", error.getClass().getName());
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private static JSONObject metadata(File file, String name) throws Exception {
        JSONObject result = new JSONObject(); result.put("name", name); result.put("exists", file.exists());
        if (file.exists()) {
            if (!file.isFile() || file.length() > MAX_BYTES) throw new java.io.IOException();
            result.put("bytes", file.length()); result.put("sha256", hash(file));
        }
        return result;
    }

    private static JSONArray summarize(File file, String store) throws Exception {
        JSONArray entries = new JSONArray();
        try (FileInputStream input = new FileInputStream(file)) {
            XmlPullParser parser = Xml.newPullParser(); parser.setInput(input, "UTF-8");
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG || parser.getDepth() != 2) continue;
                String type = parser.getName(); String key = parser.getAttributeValue(null, "name");
                JSONObject entry = new JSONObject(); entry.put("key", key); entry.put("type", type);
                String value = "string".equals(type) ? parser.nextText() : parser.getAttributeValue(null, "value");
                if (value != null) {
                    entry.put("valueSha256", hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))));
                    if (knownFixture(store, key, value)) entry.put("exactKnownFixtureValue", value);
                }
                entries.put(entry);
            }
        }
        return entries;
    }

    private static boolean knownFixture(String store, String key, String value) {
        if ("native_layout".equals(store)) {
            return "card_width_dp".equals(key) && "320".equals(value)
                    || "card_height_dp".equals(key) && "180".equals(value)
                    || "mode".equals(key) && "WIDGETS_ONLY".equals(value)
                    || "order".equals(key) && "widget:countdown\nwidget:timer".equals(value);
        }
        return "countdown_label".equals(key) && "Timezone proof".equals(value)
                || "countdown_zone".equals(key) && "America/Los_Angeles".equals(value)
                || "timer_label".equals(key) && "Tea proof".equals(value)
                || "timer_duration".equals(key) && "60000".equals(value)
                || "timer_sound".equals(key) && "false".equals(value);
    }

    private static void copy(File source, File target) throws Exception {
        try (FileInputStream input = new FileInputStream(source); FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192]; int count; int total = 0;
            while ((count = input.read(buffer)) != -1) {
                total += count; if (total > MAX_BYTES) throw new java.io.IOException(); output.write(buffer, 0, count);
            }
            output.flush(); output.getFD().sync();
        }
        privateFile(target);
    }
    private static void write(File file, byte[] bytes) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) { output.write(bytes); output.flush(); output.getFD().sync(); }
        privateFile(file);
    }
    private static void privateFile(File file) throws Exception {
        if (!file.setReadable(false, false) || !file.setWritable(false, false)
                || !file.setReadable(true, true) || !file.setWritable(true, true)) throw new java.io.IOException();
        if (file.isDirectory() && (!file.setExecutable(false, false) || !file.setExecutable(true, true))) throw new java.io.IOException();
    }
    private static String hash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192]; int count; int total = 0;
            while ((count = input.read(buffer)) != -1) {
                total += count; if (total > MAX_BYTES) throw new java.io.IOException(); digest.update(buffer, 0, count);
            }
        }
        return hex(digest.digest());
    }
    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
}
