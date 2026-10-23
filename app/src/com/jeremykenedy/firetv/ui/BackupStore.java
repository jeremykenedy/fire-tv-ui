package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** Public TV backups remain accessible after the app is removed. */
public final class BackupStore {
    public static final String BACKUP_NAME = "fire-tv-ui-backup.txt";
    public static final String IMPORT_NAME = "fire-tv-ui-import.txt";
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    private BackupStore() {
    }

    public static File file(boolean incoming) {
        return new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), incoming ? IMPORT_NAME : BACKUP_NAME);
    }

    public static boolean hasAccess() {
        return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
    }

    public static void save(Context context) throws IOException, JSONException {
        requireAccess();
        JSONObject data = new JSONObject();
        for (Map.Entry<String, ?> entry : preferences(context).getAll().entrySet()) {
            if (isPortable(entry.getKey())) {
                Object value = entry.getValue();
                data.put(entry.getKey(), value instanceof Set
                        ? new JSONArray((Set<?>) value) : value);
            }
        }
        File target = file(false);
        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("The TV Downloads folder could not be created.");
        }
        byte[] bytes = data.toString(2).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) {
            throw new IOException("This backup exceeds the 8 MB limit.");
        }
        AtomicFile backup = new AtomicFile(target);
        FileOutputStream stream = null;
        try {
            stream = backup.startWrite();
            stream.write(bytes);
            backup.finishWrite(stream);
        } catch (IOException error) {
            if (stream != null) {
                backup.failWrite(stream);
            }
            throw error;
        }
    }

    public static void restore(Context context, boolean incoming) throws IOException, JSONException {
        requireAccess();
        try (FileInputStream stream = new AtomicFile(file(incoming)).openRead()) {
            apply(context, read(stream));
        }
    }

    public static void simpleSetup(Context context) throws IOException, JSONException {
        try (java.io.InputStream stream = context.getAssets().open("simple-setup.json")) {
            apply(context, inputIcons(context, read(stream)));
        }
        if (DeviceSettings.canWrite(context)) DeviceSettings.repair(context);
    }

    public static void freshSetup(Context context) throws IOException, JSONException {
        try {
            android.content.pm.PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            if (info.firstInstallTime != info.lastUpdateTime) return;
        } catch (android.content.pm.PackageManager.NameNotFoundException unavailable) {
            throw new IOException("The initial installation could not be identified.", unavailable);
        }
        Map<String, ?> existing = preferences(context).getAll();
        for (String key : new String[] {"app_visibility", "display_order", "apps_per_row",
                "mode", "custom_gradient", "widgets_config"}) {
            if (existing.containsKey(key)) return;
        }
        try (java.io.InputStream stream = context.getAssets().open("simple-setup.json")) {
            apply(context, inputIcons(context, read(stream)), false);
        }
    }

    private static String inputIcons(Context context, String contents) throws IOException, JSONException {
        JSONObject data = new JSONObject(contents);
        if (!data.has("app_visibility")) return contents;
        JSONObject apps = new JSONObject(data.getString("app_visibility"));
        for (String name : new String[] {"xbox-one.png", "wii-u.png"}) {
            File directory = new File(context.getFilesDir(), "input-icons");
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("The input icon folder could not be created.");
            }
            File icon = new File(directory, name);
            try (java.io.InputStream stream = context.getAssets().open("input-icons/" + name);
                    FileOutputStream output = new FileOutputStream(icon)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
            }
            Iterator<String> keys = apps.keys();
            while (keys.hasNext()) {
                JSONObject entry = apps.optJSONObject(keys.next());
                if (entry != null && ("asset:input-icons/" + name)
                        .equals(entry.optString("customIconUri"))) {
                    entry.put("customIconUri", android.net.Uri.fromFile(icon).toString());
                }
            }
        }
        return data.put("app_visibility", apps.toString()).toString();
    }

    private static String read(java.io.InputStream stream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            if (result.size() + count > MAX_BYTES) {
                throw new IOException("This backup exceeds the 8 MB limit.");
            }
            result.write(buffer, 0, count);
        }
        return new String(result.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void apply(Context context, String contents) throws JSONException, IOException {
        apply(context, contents, true);
    }

    private static void apply(Context context, String contents, boolean replace) throws JSONException, IOException {
        JSONObject data = new JSONObject(contents);
        if (data.length() == 0) {
            throw new JSONException("This backup contains no settings.");
        }
        SharedPreferences prefs = preferences(context);
        String localRemote = prefs.getString(EquipmentControl.REMOTE, null);
        SharedPreferences.Editor editor = prefs.edit();
        if (replace) editor.clear();
        JSONObject schema;
        try (java.io.InputStream stream = context.getAssets().open("preference-types.json")) {
            schema = new JSONObject(read(stream));
        }
        int restored = 0;
        Iterator<String> keys = data.keys();
        // Validate every value before committing any of the settings.
        while (keys.hasNext()) {
            String key = keys.next();
            if (!isPortable(key) || !replace && prefs.getAll().containsKey(key)) {
                continue;
            }
            Object value = data.get(key);
            if ("hidden_featured_cards".equals(key) && !(value instanceof JSONArray)) {
                throw new JSONException("Wrong value type in setting: " + key);
            }
            String expected = schema.optString(key, "");
            if ("Float".equals(expected) && value instanceof Number) {
                value = ((Number) value).floatValue();
            } else if ("Long".equals(expected) && value instanceof Number) {
                Number number = (Number) value;
                if (number.doubleValue() != number.longValue()) {
                    throw new JSONException("Invalid whole number in setting: " + key);
                }
                value = number.longValue();
            }
            boolean valid = expected.isEmpty()
                    || "Boolean".equals(expected) && value instanceof Boolean
                    || "String".equals(expected) && value instanceof String
                    || "StringSet".equals(expected) && value instanceof JSONArray
                    || "Int".equals(expected) && value instanceof Integer
                    || "Long".equals(expected) && value instanceof Long
                    || "Float".equals(expected) && value instanceof Number;
            if (!valid) {
                throw new JSONException("Wrong value type in setting: " + key);
            }
            if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(key, (Long) value);
            } else if (value instanceof Number) {
                float number = ((Number) value).floatValue();
                if (Float.isInfinite(number) || Float.isNaN(number)) {
                    throw new JSONException("Invalid number in setting: " + key);
                }
                editor.putFloat(key, number);
            } else if (value instanceof String) {
                editor.putString(key, (String) value);
            } else if (value instanceof JSONArray) {
                JSONArray array = (JSONArray) value;
                if ("hidden_featured_cards".equals(key) && array.length() > 2048) {
                    throw new JSONException("Too many hidden featured cards.");
                }
                Set<String> strings = new HashSet<>();
                for (int i = 0; i < array.length(); i++) {
                    if (!(array.get(i) instanceof String)) {
                        throw new JSONException("Invalid list in setting: " + key);
                    }
                    if ("hidden_featured_cards".equals(key)
                            && !array.getString(i).matches("[a-f0-9]{64}")) {
                        throw new JSONException("Invalid hidden featured card identity.");
                    }
                    strings.add(array.getString(i));
                }
                editor.putStringSet(key, strings);
            } else {
                throw new JSONException("Invalid value in setting: " + key);
            }
            restored++;
        }
        if (restored == 0) {
            throw new JSONException("This backup contains no restorable settings.");
        }
        if (localRemote != null) {
            editor.putString(EquipmentControl.REMOTE, localRemote);
        }
        editor.putBoolean("is_premium", true);
        if (!editor.commit()) {
            throw new IOException("The TV could not save the restored settings.");
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE);
    }

    private static boolean isPortable(String key) {
        return !key.equals("is_premium") && !key.equals("PREF_IS_PREMIUM")
                && !key.equals(EquipmentControl.REMOTE)
                && !key.startsWith("fire_tv_ui_preview_")
                && !key.startsWith("fire_tv_ui_tv_sleep_")
                && !key.equals("app_launch_count") && !key.equals("rate_app_shown")
                && !key.equals("aerial_json_cache") && !key.equals("subreddit_wallpaper_urls");
    }

    private static void requireAccess() throws IOException {
        if (!hasAccess()) {
            throw new IOException("Allow TV backup access using the Fire TV CLI installer.");
        }
    }
}
