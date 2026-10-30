package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.net.Uri;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Private, bounded UI presets. Account connections and TV controls are never captured. */
final class SavedSetupStore {
    static final int MAX_SETUPS = 24;
    private static final int MAX_JSON = 4 * 1024 * 1024;
    private static final long MAX_ASSETS = 96L * 1024 * 1024;
    private static final Object LOCK = new Object();
    private final Context context;
    private final File directory;
    private final AtomicFile file;

    SavedSetupStore(Context context) {
        this.context = context;
        directory = new File(context.getFilesDir(), "saved-setups");
        file = new AtomicFile(new File(directory, "setups.json"));
    }

    List<JSONObject> list() throws Exception {
        synchronized (LOCK) {
            JSONArray values = read().getJSONArray("setups");
            List<JSONObject> result = new ArrayList<>();
            for (int i = 0; i < values.length(); i++) result.add(values.getJSONObject(i));
            return result;
        }
    }

    String save(String name, String replaceId) throws Exception {
        synchronized (LOCK) {
            JSONObject data = read();
            JSONArray items = data.getJSONArray("setups");
            name = checkedName(name);
            checkDuplicate(items, name, replaceId);
            int index = replaceId == null ? -1 : index(items, replaceId);
            if (replaceId != null && index < 0) throw new IOException("This setup no longer exists.");
            if (index < 0 && items.length() >= MAX_SETUPS) throw new IOException("You can save up to 24 setups. Delete one before saving another.");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Saved setup storage is unavailable.");
            JSONObject snapshot = SavedSetupSettings.capture(DeviceSettings.prefs(context).getAll());
            List<File> created = new ArrayList<>();
            try {
                preserveImages(snapshot, created);
                String id = replaceId == null ? UUID.randomUUID().toString() : replaceId;
                JSONObject item = new JSONObject().put("id", id).put("name", name)
                        .put("savedAt", System.currentTimeMillis()).put("settings", snapshot);
                if (index < 0) items.put(item); else items.put(index, item);
                write(data);
                prune(data);
                return id;
            } catch (Exception failed) {
                for (File asset : created) asset.delete();
                throw failed;
            }
        }
    }

    void rename(String id, String name) throws Exception {
        synchronized (LOCK) {
            JSONObject data = read();
            JSONArray items = data.getJSONArray("setups");
            name = checkedName(name);
            checkDuplicate(items, name, id);
            int index = index(items, id);
            if (index < 0) throw new IOException("This setup no longer exists.");
            items.getJSONObject(index).put("name", name);
            write(data);
        }
    }

    void delete(String id) throws Exception {
        synchronized (LOCK) {
            JSONObject data = read();
            JSONArray before = data.getJSONArray("setups"), after = new JSONArray();
            if (index(before, id) < 0) throw new IOException("This setup no longer exists.");
            for (int i = 0; i < before.length(); i++) {
                JSONObject item = before.getJSONObject(i);
                if (!id.equals(item.getString("id"))) after.put(item);
            }
            data.put("setups", after);
            write(data);
            prune(data);
        }
    }

    void apply(String id, boolean icons) throws Exception {
        synchronized (LOCK) {
            JSONObject data = read();
            JSONArray items = data.getJSONArray("setups");
            int index = index(items, id);
            if (index < 0) throw new IOException("This setup no longer exists.");
            Map<String, Object> values = SavedSetupSettings.toApply(items.getJSONObject(index).getJSONObject("settings"),
                    DeviceSettings.prefs(context).getAll(), icons);
            Map<String, Object> restoredImages = new LinkedHashMap<>(values);
            if (!icons) restoredImages.remove("app_visibility");
            verifyImages(restoredImages);
            Object previousWallpaper = DeviceSettings.prefs(context).getAll().get("custom_wallpaper_uri");
            if (values.containsKey("custom_wallpaper_uri") && !Objects.equals(previousWallpaper, values.get("custom_wallpaper_uri"))) {
                File blur = new File(context.getFilesDir(), "wallpaper_blur.jpg");
                if (blur.exists() && !blur.delete()) throw new IOException("The previous wallpaper preview could not be refreshed. Please try again.");
            }
            SavedSetupSettings.apply(DeviceSettings.prefs(context), values);
        }
    }

    private JSONObject read() throws Exception {
        if (!new File(directory, "setups.json").exists() && !new File(directory, "setups.json.bak").exists())
            return new JSONObject().put("version", 1).put("setups", new JSONArray());
        byte[] bytes;
        try (InputStream input = file.openRead()) { bytes = bounded(input, MAX_JSON); }
        JSONObject data = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        if (data.length() != 2 || !(data.get("version") instanceof Integer) || data.getInt("version") != 1)
            throw new IOException("This saved setup file is not supported. Your current settings were not changed.");
        JSONArray items = data.getJSONArray("setups");
        if (items.length() > MAX_SETUPS) throw new IOException("The saved setup list is too large.");
        Set<String> ids = new HashSet<>(), names = new HashSet<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String id = item.getString("id"), name = checkedName(item.getString("name"));
            if (!id.matches("[a-f0-9-]{36}") || !ids.add(id) || !names.add(name.toLowerCase(Locale.ROOT)))
                throw new IOException("The saved setup list contains duplicate or invalid entries.");
            if (!(item.get("savedAt") instanceof Number) || item.getLong("savedAt") < 0) throw new IOException("Invalid setup date.");
            SavedSetupSettings.validate(item.getJSONObject("settings"));
        }
        return data;
    }

    private void write(JSONObject data) throws Exception {
        byte[] bytes = data.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_JSON) throw new IOException("Saved setup storage is full. Delete an unused setup first.");
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(bytes);
            file.finishWrite(output);
        } catch (Exception failed) {
            if (output != null) file.failWrite(output);
            throw failed;
        }
    }

    private static String checkedName(String value) throws IOException {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || name.length() > 60 || name.matches("(?s).*\\p{Cntrl}.*"))
            throw new IOException("Enter a name with 1 to 60 characters.");
        return name;
    }

    private static void checkDuplicate(JSONArray items, String name, String exceptId) throws Exception {
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (name.equalsIgnoreCase(item.getString("name")) && !item.getString("id").equals(exceptId))
                throw new IOException("A setup with that name already exists. Choose another name or update that setup.");
        }
    }

    private static int index(JSONArray items, String id) throws Exception {
        for (int i = 0; i < items.length(); i++) if (id.equals(items.getJSONObject(i).getString("id"))) return i;
        return -1;
    }

    private void preserveImages(JSONObject snapshot, List<File> created) throws Exception {
        JSONObject values = snapshot.getJSONObject("values");
        Map<String, String> copies = new HashMap<>();
        if (values.has("custom_wallpaper_uri")) values.put("custom_wallpaper_uri", copyImage(values.getString("custom_wallpaper_uri"), copies, created));
        if (values.has("app_visibility")) {
            JSONObject apps = new JSONObject(values.getString("app_visibility"));
            for (Iterator<String> i = apps.keys(); i.hasNext();) {
                JSONObject app = apps.optJSONObject(i.next());
                if (app != null && app.has("customIconUri")) app.put("customIconUri", copyImage(app.getString("customIconUri"), copies, created));
            }
            values.put("app_visibility", apps.toString());
        }
    }

    private String copyImage(String value, Map<String, String> copies, List<File> created) throws Exception {
        if (value.isEmpty() || value.startsWith("asset:")) return value;
        Uri uri = Uri.parse(value);
        if (!"content".equals(uri.getScheme()) && !"file".equals(uri.getScheme()))
            throw new IOException("This image source cannot be saved. Choose an image stored on the TV first.");
        if (copies.containsKey(value)) return copies.get(value);
        if ("file".equals(uri.getScheme()) && new File(uri.getPath()).getCanonicalFile().getParentFile().equals(directory.getCanonicalFile())) {
            try (InputStream input = openImage(uri)) {
                if (input == null || input.read() < 0) throw new IOException("A saved image is missing. Choose it again before saving.");
            }
            return value;
        }
        File target = new File(directory, UUID.randomUUID().toString() + ".image");
        created.add(target);
        long used = 0;
        File[] files = directory.listFiles();
        if (files != null) for (File f : files) if (f.getName().endsWith(".image")) used += f.length();
        try (InputStream input = openImage(uri);
             FileOutputStream output = new FileOutputStream(target)) {
            if (input == null) throw new IOException("An image is unavailable. Choose it again before saving.");
            byte[] buffer = new byte[8192]; int size; long total = 0;
            while ((size = input.read(buffer)) != -1) {
                total += size;
                if (total > 20L * 1024 * 1024 || used + total > MAX_ASSETS) throw new IOException("Saved images are too large. Use smaller images or delete an unused setup.");
                output.write(buffer, 0, size);
            }
            if (total == 0) throw new IOException("An image is empty. Choose it again before saving.");
            output.getFD().sync();
        }
        String saved = Uri.fromFile(target).toString();
        copies.put(value, saved);
        return saved;
    }

    private InputStream openImage(Uri uri) throws IOException {
        try {
            InputStream input = context.getContentResolver().openInputStream(uri);
            if (input == null) throw new IOException("Image source returned no data.");
            return input;
        } catch (IOException | RuntimeException unavailable) {
            throw new IOException("An image is unavailable. Choose it again before saving or applying this setup.", unavailable);
        }
    }

    private void verifyImages(Map<String, ?> values) throws Exception {
        for (String value : imageUris(values)) {
            if (value.isEmpty() || value.startsWith("asset:")) continue;
            Uri uri = Uri.parse(value);
            if (!"file".equals(uri.getScheme()) && !"content".equals(uri.getScheme())) throw new IOException("An image source is not supported.");
            try (InputStream input = openImage(uri)) {
                if (input == null || input.read() < 0) throw new IOException("A saved image is unavailable. Your current setup was not changed.");
            }
        }
    }

    private static Set<String> imageUris(Map<String, ?> values) throws Exception {
        Set<String> result = new HashSet<>();
        Object wallpaper = values.get("custom_wallpaper_uri");
        if (wallpaper instanceof String) result.add((String) wallpaper);
        Object visibility = values.get("app_visibility");
        if (visibility instanceof String) {
            JSONObject apps = new JSONObject((String) visibility);
            for (Iterator<String> i = apps.keys(); i.hasNext();) {
                JSONObject app = apps.optJSONObject(i.next());
                if (app != null && app.has("customIconUri")) result.add(app.getString("customIconUri"));
            }
        }
        return result;
    }

    private void prune(JSONObject data) {
        try {
            Set<String> referenced = imageUris(DeviceSettings.prefs(context).getAll());
            JSONArray items = data.getJSONArray("setups");
            for (int i = 0; i < items.length(); i++) referenced.addAll(imageUris(SavedSetupSettings.validate(items.getJSONObject(i).getJSONObject("settings"))));
            File[] files = directory.listFiles();
            if (files != null) for (File asset : files)
                if (asset.getName().endsWith(".image") && !referenced.contains(Uri.fromFile(asset).toString())) asset.delete();
        } catch (Exception ignored) {
            // Keep images when their references cannot be established safely.
        }
    }

    private static byte[] bounded(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int size;
        while ((size = input.read(buffer)) != -1) {
            if (output.size() + size > maximum) throw new IOException("The saved setup file is too large.");
            output.write(buffer, 0, size);
        }
        return output.toByteArray();
    }
}
