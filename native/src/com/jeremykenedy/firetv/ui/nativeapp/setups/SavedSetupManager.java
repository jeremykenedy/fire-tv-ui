package com.jeremykenedy.firetv.ui.nativeapp.setups;

import android.content.Context;
import android.net.Uri;
import android.util.AtomicFile;
import android.util.Base64;

import com.jeremykenedy.firetv.ui.nativeapp.migration.LegacyPresentationMapper;
import com.jeremykenedy.firetv.ui.nativeapp.migration.PreferenceSnapshot;
import com.jeremykenedy.firetv.ui.nativeapp.migration.PresentationMappingContext;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Private, bounded UI presets. Account connections and TV controls are never captured. */
public final class SavedSetupManager {
    static final int MAX_SETUPS = 24;
    private static final int MAX_JSON = 4 * 1024 * 1024;
    private static final long MAX_ASSETS = 96L * 1024 * 1024;
    private static final Object LOCK = new Object();
    private final Context context;
    private final File directory;
    private final AtomicFile file;

    public SavedSetupManager(Context context) {
        this.context = context;
        directory = new File(context.getFilesDir(), "saved-setups");
        file = new AtomicFile(new File(directory, "setups.json"));
    }

    public List<JSONObject> list() throws Exception {
        synchronized (LOCK) {
            JSONArray values = read().getJSONArray("setups");
            List<JSONObject> result = new ArrayList<>();
            for (int i = 0; i < values.length(); i++) result.add(values.getJSONObject(i));
            return result;
        }
    }

    public String save(String name, String replaceId) throws Exception {
        synchronized (LOCK) {
            JSONObject data = read();
            JSONArray items = data.getJSONArray("setups");
            name = checkedName(name);
            checkDuplicate(items, name, replaceId);
            int index = replaceId == null ? -1 : index(items, replaceId);
            if (replaceId != null && index < 0)
                throw new IOException("This setup no longer exists.");
            if (index < 0 && items.length() >= MAX_SETUPS)
                throw new IOException(
                        "You can save up to 24 setups. Delete one before saving another.");
            if (!directory.isDirectory() && !directory.mkdirs())
                throw new IOException("Saved setup storage is unavailable.");
            Map<String, PreferenceSnapshot> nativeState = PresentationState.capture(context);
            JSONObject snapshot =
                    LegacySetupSettings.capture(
                            context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
                                    .getAll());
            List<File> created = new ArrayList<>();
            try {
                Map<String, String> copies = new HashMap<>();
                preserveImages(snapshot, copies, created);
                JSONObject nativeImages = new JSONObject();
                Map<String, Object> retainedLegacy =
                        new LinkedHashMap<>(LegacySetupSettings.validate(snapshot));
                retainedLegacy.values().removeAll(Collections.singleton(null));
                nativeState = new LinkedHashMap<>(nativeState);
                nativeState.put("launcher_prefs", new PreferenceSnapshot(retainedLegacy));
                JSONObject nativeSnapshot =
                        encodeNative(
                                preserveNativeImages(nativeState, nativeImages, copies, created));
                String id = replaceId == null ? UUID.randomUUID().toString() : replaceId;
                JSONObject item =
                        new JSONObject()
                                .put("id", id)
                                .put("name", name)
                                .put("savedAt", System.currentTimeMillis())
                                .put("settings", snapshot)
                                .put("native", nativeSnapshot)
                                .put("nativeImages", nativeImages);
                if (index < 0) items.put(item);
                else items.put(index, item);
                write(data);
                prune(data);
                return id;
            } catch (Exception failed) {
                for (File asset : created) asset.delete();
                throw failed;
            }
        }
    }

    public void rename(String id, String name) throws Exception {
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

    public void delete(String id) throws Exception {
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

    public void apply(String id, boolean icons) throws Exception {
        apply(id, icons, PresentationMappingContext.empty());
    }

    public void apply(String id, boolean icons, PresentationMappingContext identities) throws Exception {
        if (identities == null) throw new IllegalArgumentException("Missing migration identities.");
        synchronized (LOCK) {
            JSONObject data = read();
            JSONArray items = data.getJSONArray("setups");
            int index = index(items, id);
            if (index < 0) throw new IOException("This setup no longer exists.");
            JSONObject item = items.getJSONObject(index);
            Map<String, PreferenceSnapshot> target =
                    item.has("native")
                            ? decodeNative(item.getJSONObject("native"))
                            : prepareLegacy(
                                    item.getJSONObject("settings"),
                                    identities,
                                    icons);
            Map<String, Object> legacy = target.get("launcher_prefs").values();
            verifyVideo(legacy);
            Map<String, Object> imagesToVerify = new LinkedHashMap<>(legacy);
            if (!icons) imagesToVerify.remove("app_visibility");
            verifyImages(imagesToVerify);
            verifyNativeMedia(target, item.optJSONObject("nativeImages"), icons);
            PresentationState.apply(context, target, icons);
        }
    }

    /**
     * Explicit verified identity inventory can be supplied for legacy feeds, inputs and custom
     * icons.
     */
    public Map<String, PreferenceSnapshot> prepareLegacy(
            JSONObject settings, PresentationMappingContext identities, boolean icons)
            throws Exception {
        Map<String, Object> saved = LegacySetupSettings.validate(settings);
        Map<String, Object> present = new LinkedHashMap<>(saved);
        present.values().removeAll(Collections.singleton(null));
        if (!icons) {
            present.remove("icon_pack_package");
            present.remove("factory_icon_packages");
            if (present.containsKey("app_visibility")) {
                JSONObject visibility = new JSONObject((String) present.get("app_visibility"));
                for (Iterator<String> ids = visibility.keys(); ids.hasNext(); )
                    visibility.getJSONObject(ids.next()).remove("customIconUri");
                present.put("app_visibility", visibility.toString());
            }
        }
        Map<String, PreferenceSnapshot> empty = new LinkedHashMap<>();
        for (String file : LegacyPresentationMapper.FILES)
            if (!file.equals("launcher_prefs"))
                empty.put(file, new PreferenceSnapshot(Collections.emptyMap()));
        LegacyPresentationMapper.Plan plan =
                LegacyPresentationMapper.plan(new PreferenceSnapshot(present), identities, empty);
        Set<String> unresolved = new LinkedHashSet<>(plan.unmappedLegacyKeys);
        unresolved.removeAll(LegacyPresentationMapper.directlyConsumedPresentationKeys(present));
        if (!unresolved.isEmpty())
            throw new IOException(
                    "This legacy setup contains presentation choices that still need verified"
                            + " migration. The setup and all images have been kept.");
        Map<String, PreferenceSnapshot> target =
                new LinkedHashMap<>(PresentationState.capture(context));
        Map<String, Set<String>> owned = new LinkedHashMap<>(
                LegacyPresentationMapper.ownedNativeKeys(saved.keySet(), identities));
        if (saved.containsKey("mode") && !"custom".equals(present.get("mode"))) {
            Set<String> appearance = new LinkedHashSet<>(owned.get(LegacyPresentationMapper.APPEARANCE));
            owned.put(LegacyPresentationMapper.APPEARANCE, appearance);
            appearance.remove("scene");
            if (!saved.containsKey("menu_animation")) appearance.remove("base");
        }
        for (Map.Entry<String, Set<String>> file : owned.entrySet()) {
            if (!target.containsKey(file.getKey()))
                throw new IOException("Unsupported legacy target.");
            if (!icons && (file.getKey().equals("native_icons") || file.getKey().equals("native_icon_packs"))) continue;
            Map<String, Object> values = new LinkedHashMap<>(target.get(file.getKey()).values());
            for (String key : file.getValue()) values.remove(key);
            PreferenceSnapshot patch = plan.patches.get(file.getKey());
            if (patch != null) values.putAll(patch.values());
            target.put(file.getKey(), new PreferenceSnapshot(values));
        }
        Map<String, Object> raw = new LinkedHashMap<>(target.get("launcher_prefs").values());
        for (Map.Entry<String, Object> change :
                LegacySetupSettings.toApply(settings, raw, icons).entrySet()) {
            if (change.getValue() == null) raw.remove(change.getKey());
            else raw.put(change.getKey(), change.getValue());
        }
        target.put("launcher_prefs", new PreferenceSnapshot(raw));
        PresentationState.validate(target);
        return target;
    }

    private JSONObject read() throws Exception {
        if (!new File(directory, "setups.json").exists()
                && !new File(directory, "setups.json.bak").exists())
            return new JSONObject().put("version", 1).put("setups", new JSONArray());
        byte[] bytes;
        try (InputStream input = file.openRead()) {
            bytes = bounded(input, MAX_JSON);
        }
        JSONObject data = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        if (data.length() != 2
                || !(data.get("version") instanceof Integer)
                || data.getInt("version") != 1)
            throw new IOException(
                    "This saved setup file is not supported. Your current settings were not"
                            + " changed.");
        JSONArray items = data.getJSONArray("setups");
        if (items.length() > MAX_SETUPS)
            throw new IOException("The saved setup list is too large.");
        Set<String> ids = new HashSet<>(), names = new HashSet<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String id = item.getString("id"), name = checkedName(item.getString("name"));
            if (!id.matches("[a-f0-9-]{36}")
                    || !ids.add(id)
                    || !names.add(name.toLowerCase(Locale.ROOT)))
                throw new IOException(
                        "The saved setup list contains duplicate or invalid entries.");
            if (!(item.get("savedAt") instanceof Number) || item.getLong("savedAt") < 0)
                throw new IOException("Invalid setup date.");
            LegacySetupSettings.validate(item.getJSONObject("settings"));
            if (item.has("native")) decodeNative(item.getJSONObject("native"));
        }
        return data;
    }

    private void write(JSONObject data) throws Exception {
        byte[] bytes = data.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_JSON)
            throw new IOException("Saved setup storage is full. Delete an unused setup first.");
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

    private static void checkDuplicate(JSONArray items, String name, String exceptId)
            throws Exception {
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (name.equalsIgnoreCase(item.getString("name"))
                    && !item.getString("id").equals(exceptId))
                throw new IOException(
                        "A setup with that name already exists. Choose another name or update that"
                                + " setup.");
        }
    }

    private static int index(JSONArray items, String id) throws Exception {
        for (int i = 0; i < items.length(); i++)
            if (id.equals(items.getJSONObject(i).getString("id"))) return i;
        return -1;
    }

    private void preserveImages(JSONObject snapshot, Map<String, String> copies, List<File> created)
            throws Exception {
        JSONObject values = snapshot.getJSONObject("values");
        if (values.has("custom_wallpaper_uri"))
            values.put(
                    "custom_wallpaper_uri",
                    copyImage(values.getString("custom_wallpaper_uri"), copies, created));
        if (values.has("app_visibility")) {
            JSONObject apps = new JSONObject(values.getString("app_visibility"));
            for (Iterator<String> i = apps.keys(); i.hasNext(); ) {
                JSONObject app = apps.optJSONObject(i.next());
                if (app != null && app.has("customIconUri"))
                    app.put(
                            "customIconUri",
                            copyImage(app.getString("customIconUri"), copies, created));
            }
            values.put("app_visibility", apps.toString());
        }
    }

    private String copyImage(String value, Map<String, String> copies, List<File> created)
            throws Exception {
        if (value.isEmpty() || value.startsWith("asset:")) return value;
        Uri uri = Uri.parse(value);
        if (!"content".equals(uri.getScheme()) && !"file".equals(uri.getScheme()))
            throw new IOException(
                    "This image source cannot be saved. Choose an image stored on the TV first.");
        if (copies.containsKey(value)) return copies.get(value);
        if ("file".equals(uri.getScheme())
                && new File(uri.getPath())
                        .getCanonicalFile()
                        .getParentFile()
                        .equals(directory.getCanonicalFile())) {
            try (InputStream input = openImage(uri)) {
                if (input == null || input.read() < 0)
                    throw new IOException(
                            "A saved image is missing. Choose it again before saving.");
            }
            return value;
        }
        File target = new File(directory, UUID.randomUUID().toString() + ".image");
        created.add(target);
        long used = 0;
        File[] files = directory.listFiles();
        if (files != null)
            for (File f : files) if (f.getName().endsWith(".image")) used += f.length();
        try (InputStream input = openImage(uri);
                FileOutputStream output = new FileOutputStream(target)) {
            if (input == null)
                throw new IOException("An image is unavailable. Choose it again before saving.");
            byte[] buffer = new byte[8192];
            int size;
            long total = 0;
            while ((size = input.read(buffer)) != -1) {
                total += size;
                if (total > 20L * 1024 * 1024 || used + total > MAX_ASSETS)
                    throw new IOException(
                            "Saved images are too large. Use smaller images or delete an unused"
                                    + " setup.");
                output.write(buffer, 0, size);
            }
            if (total == 0)
                throw new IOException("An image is empty. Choose it again before saving.");
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
            throw new IOException(
                    "An image is unavailable. Choose it again before saving or applying this"
                            + " setup.",
                    unavailable);
        }
    }

    private void verifyVideo(Map<String, ?> values) throws IOException {
        if (!"customv".equals(values.get("mode"))) return;
        Object source = values.get("custom_video_uri");
        String message =
                "The saved video is unavailable. Keep its original file accessible or choose it"
                        + " again before applying this setup.";
        if (!(source instanceof String) || ((String) source).isEmpty())
            throw new IOException(message);
        try (InputStream input =
                context.getContentResolver().openInputStream(Uri.parse((String) source))) {
            if (input == null || input.read() < 0) throw new IOException(message);
        } catch (IOException | RuntimeException unavailable) {
            throw new IOException(message, unavailable);
        }
    }

    private void verifyImages(Map<String, ?> values) throws Exception {
        for (String value : imageUris(values)) {
            if (value.isEmpty() || value.startsWith("asset:")) continue;
            Uri uri = Uri.parse(value);
            if (!"file".equals(uri.getScheme()) && !"content".equals(uri.getScheme()))
                throw new IOException("An image source is not supported.");
            try (InputStream input = openImage(uri)) {
                if (input == null || input.read() < 0)
                    throw new IOException(
                            "A saved image is unavailable. Your current setup was not changed.");
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
            for (Iterator<String> i = apps.keys(); i.hasNext(); ) {
                JSONObject app = apps.optJSONObject(i.next());
                if (app != null && app.has("customIconUri"))
                    result.add(app.getString("customIconUri"));
            }
        }
        return result;
    }

    private void prune(JSONObject data) {
        try {
            Set<String> referenced =
                    imageUris(
                            context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
                                    .getAll());
            addAppearanceImages(
                    referenced,
                    context.getSharedPreferences("source_appearance", Context.MODE_PRIVATE)
                            .getAll());
            JSONArray items = data.getJSONArray("setups");
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                referenced.addAll(
                        imageUris(LegacySetupSettings.validate(item.getJSONObject("settings"))));
                if (item.has("native")) {
                    Map<String, PreferenceSnapshot> nativeState =
                            decodeNative(item.getJSONObject("native"));
                    referenced.addAll(imageUris(nativeState.get("launcher_prefs").values()));
                    addAppearanceImages(referenced, nativeState.get("source_appearance").values());
                }
                JSONObject icons = item.optJSONObject("nativeImages");
                if (icons != null)
                    for (Iterator<String> keys = icons.keys(); keys.hasNext(); )
                        referenced.add(icons.getString(keys.next()));
            }
            Set<String> paths = new HashSet<>();
            for (String reference : referenced) {
                Uri uri = Uri.parse(reference);
                if ("file".equals(uri.getScheme()))
                    paths.add(new File(uri.getPath()).getCanonicalPath());
            }
            File[] files = directory.listFiles();
            if (files != null)
                for (File asset : files)
                    if (asset.getName().endsWith(".image")
                            && !paths.contains(asset.getCanonicalPath())) asset.delete();
        } catch (Exception uncertain) {
            // Keep every image if a complete reference inventory cannot be verified.
        }
    }

    private static void addAppearanceImages(Set<String> referenced, Map<String, ?> appearance) {
        for (String key : Arrays.asList("wallpaper_image_uri", "wallpaper_video_uri")) {
            Object value = appearance.get(key);
            if (value instanceof String) referenced.add((String) value);
        }
    }

    public static JSONObject encodeNative(Map<String, PreferenceSnapshot> state) throws Exception {
        PresentationState.validate(state);
        JSONObject object = new JSONObject();
        for (Map.Entry<String, PreferenceSnapshot> entry : state.entrySet())
            object.put(
                    entry.getKey(),
                    Base64.encodeToString(entry.getValue().encode(), Base64.NO_WRAP));
        return object;
    }

    public static Map<String, PreferenceSnapshot> decodeNative(JSONObject object) throws Exception {
        Map<String, PreferenceSnapshot> result = new LinkedHashMap<>();
        for (Iterator<String> keys = object.keys(); keys.hasNext(); ) {
            String key = keys.next();
            Object value = object.get(key);
            if (!(value instanceof String)) throw new IOException("Invalid saved presentation.");
            result.put(
                    key, PreferenceSnapshot.decode(Base64.decode((String) value, Base64.NO_WRAP)));
        }
        if (!result.containsKey("source_accessibility"))
            result.put("source_accessibility", new PreferenceSnapshot(Collections.emptyMap()));
        PresentationState.validate(result);
        return result;
    }

    private Map<String, PreferenceSnapshot> preserveNativeImages(
            Map<String, PreferenceSnapshot> state,
            JSONObject images,
            Map<String, String> copies,
            List<File> created)
            throws Exception {
        Map<String, PreferenceSnapshot> next = new LinkedHashMap<>(state);
        for (Map.Entry<String, Object> icon : state.get("native_icons").values().entrySet()) {
            String value = (String) icon.getValue();
            if (value.startsWith("image:"))
                images.put(
                        value,
                        copyImage(
                                Uri.fromFile(
                                                new File(
                                                        new File(context.getFilesDir(), "icons"),
                                                        value.substring(6)))
                                        .toString(),
                                copies,
                                created));
        }
        Map<String, Object> appearance =
                new LinkedHashMap<>(state.get("source_appearance").values());
        Object wallpaper = appearance.get("wallpaper_image_uri");
        if (wallpaper instanceof String && !((String) wallpaper).isEmpty())
            appearance.put("wallpaper_image_uri", copyImage((String) wallpaper, copies, created));
        next.put("source_appearance", new PreferenceSnapshot(appearance));
        return next;
    }

    private void verifyNativeMedia(
            Map<String, PreferenceSnapshot> state, JSONObject images, boolean icons)
            throws Exception {
        Map<String, Object> appearance = state.get("source_appearance").values();
        Object image = appearance.get("wallpaper_image_uri");
        if (image instanceof String && !((String) image).isEmpty())
            try (InputStream input = openImage(Uri.parse((String) image))) {
                if (input.read() < 0) throw new IOException("Saved wallpaper is empty.");
            }
        if ("video".equals(appearance.get("wallpaper_mode"))) {
            Object video = appearance.get("wallpaper_video_uri");
            if (!(video instanceof String) || ((String) video).isEmpty())
                throw new IOException("Saved video is unavailable. The setup has been kept.");
            try (InputStream input = openImage(Uri.parse((String) video))) {
                if (input.read() < 0) throw new IOException("Saved video is empty.");
            }
        }
        if (!icons) return;
        // Validate all retained sources before preparing any target image.
        Map<File, File> pending = new LinkedHashMap<>();
        for (Object selected : state.get("native_icons").values().values()) {
            String value = (String) selected;
            if (!value.startsWith("image:")) continue;
            if (images == null || !images.has(value))
                throw new IOException("A saved icon is unavailable. The setup has been kept.");
            Uri source = Uri.parse(images.getString(value));
            File saved = new File(source.getPath());
            if (!"file".equals(source.getScheme())
                    || !saved.getCanonicalFile()
                            .getParentFile()
                            .equals(directory.getCanonicalFile())
                    || !saved.getName().endsWith(".image"))
                throw new IOException("Invalid retained icon.");
            if (!saved.isFile() || saved.length() == 0 || saved.length() > 20L * 1024 * 1024)
                throw new IOException("A saved icon is missing or too large.");
            File target = new File(new File(context.getFilesDir(), "icons"), value.substring(6));
            if (target.exists()) {
                try (InputStream input = new FileInputStream(target)) {
                    if (!Arrays.equals(digest(saved), digest(target)))
                        throw new IOException("A retained icon conflicts with its original image.");
                }
            } else pending.put(target, saved);
        }
        for (Map.Entry<File, File> entry : pending.entrySet()) {
            File target = entry.getKey();
            if (!target.getParentFile().isDirectory() && !target.getParentFile().mkdirs())
                throw new IOException("Icon storage unavailable.");
            AtomicFile imageFile = new AtomicFile(target);
            FileOutputStream output = null;
            try {
                output = imageFile.startWrite();
                try (InputStream input = new FileInputStream(entry.getValue())) {
                    byte[] buffer = new byte[8192];
                    int size;
                    long total = 0;
                    while ((size = input.read(buffer)) != -1) {
                        total += size;
                        if (total > 20L * 1024 * 1024)
                            throw new IOException("Saved icon is too large.");
                        output.write(buffer, 0, size);
                    }
                }
                imageFile.finishWrite(output);
            } catch (Exception failure) {
                if (output != null) imageFile.failWrite(output);
                throw failure;
            }
        }
    }

    private static byte[] digest(File file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int size;
            long total = 0;
            while ((size = input.read(buffer)) != -1) {
                total += size;
                if (total > 20L * 1024 * 1024) throw new IOException("Saved icon is too large.");
                digest.update(buffer, 0, size);
            }
        }
        return digest.digest();
    }

    private static byte[] bounded(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int size;
        while ((size = input.read(buffer)) != -1) {
            if (output.size() + size > maximum)
                throw new IOException("The saved setup file is too large.");
            output.write(buffer, 0, size);
        }
        return output.toByteArray();
    }
}
