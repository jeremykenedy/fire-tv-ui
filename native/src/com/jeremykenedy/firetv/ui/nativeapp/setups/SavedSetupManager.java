package com.jeremykenedy.firetv.ui.nativeapp.setups;

import android.content.Context;
import android.net.Uri;
import android.util.AtomicFile;
import android.util.Base64;

import com.jeremykenedy.firetv.ui.nativeapp.appgrid.RetainedHomeChoices;
import com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroupPresentationPlan;

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
                                .put("nativeVersion", 2)
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
            synchronized (PresentationState.class) {
                boolean legacyRecord = !item.has("native");
                boolean preparedIcons = false;
                try {
                    PresentationMappingContext selected = identities;
                    if (legacyRecord && icons) {
                        Set<String> references = retainedIconReferences(item.getJSONObject("settings"));
                        if (!references.isEmpty()) {
                            Map<String,String> placeholders = new LinkedHashMap<>();
                            for (String reference : references) placeholders.put(reference,"image:00000000-0000-0000-0000-000000000000.png");
                            prepareLegacy(item.getJSONObject("settings"),withIcons(identities,placeholders),true);
                            preparedIcons = true;
                            selected = withIcons(identities,com.jeremykenedy.firetv.ui.nativeapp.migration.AndroidLegacyIconMigration.prepare(context,references));
                        }
                    }
                    Map<String, PreferenceSnapshot> target = item.has("native")
                            ? decodeNative(item.getJSONObject("native"))
                            : prepareLegacy(item.getJSONObject("settings"),selected,icons);
                    if (item.has("native") && !item.has("nativeVersion")) {
                        Map<String, Object> layout = new LinkedHashMap<>(target.get("native_layout").values());
                        Object groups = context.getSharedPreferences("native_layout", Context.MODE_PRIVATE)
                                .getAll().get("widget_groups");
                        if (groups != null) layout.put("widget_groups", groups);
                        target = new LinkedHashMap<>(target);
                        target.put("native_layout", new PreferenceSnapshot(layout));
                    }
                    Map<String, Object> legacy = target.get("launcher_prefs").values();
                    verifyVideo(legacy);
                    Map<String, Object> imagesToVerify = new LinkedHashMap<>(legacy);
                    if (!icons) imagesToVerify.remove("app_visibility");
                    verifyImages(imagesToVerify);
                    verifyNativeMedia(target, item.optJSONObject("nativeImages"), legacyRecord ? false : icons);
                    if (legacyRecord && icons) verifyManagedIcons(target);
                    PresentationState.apply(context, target, icons);
                } catch (Exception failure) {
                    if (preparedIcons) throw new IOException(failure.getMessage()
                            + " Original saved images were kept. Any prepared icon copies remain available for retry.",failure);
                    throw failure;
                }
            }
        }
    }

    private PresentationMappingContext withIcons(PresentationMappingContext base,Map<String,String> changes) {
        Map<String,String> icons = new LinkedHashMap<>(base.icons);icons.putAll(changes);
        return new PresentationMappingContext(base.components,base.feeds,base.items,icons,base.inputs,
                base.advertisedIconPacks,base.privateMedia.values(),base.ambiguousInputs);
    }

    private Set<String> retainedIconReferences(JSONObject settings) throws Exception {
        Map<String,Object> values = LegacySetupSettings.validate(settings);
        Set<String> references = new LinkedHashSet<>();
        Object visibility = values.get("app_visibility");
        if (!(visibility instanceof String)) return references;
        for (Object value : com.jeremykenedy.firetv.ui.nativeapp.migration.LegacyJson.object((String)visibility).values()) {
            if (!(value instanceof Map)) throw new IOException("Invalid saved icon choices.");
            Object uri = ((Map<?,?>)value).get("customIconUri");
            if (uri == null) continue;
            if (!(uri instanceof String)) throw new IOException("Invalid saved icon reference.");
            Uri parsed = Uri.parse((String)uri);
            if (!"file".equals(parsed.getScheme()) || parsed.getPath() == null)
                throw new IOException("This setup needs a retained image stored on this TV.");
            File image = new File(parsed.getPath());
            File root = directory.getCanonicalFile();
            File owned = new File(root,image.getName());
            if (!root.equals(image.getCanonicalFile().getParentFile()) || !image.getName().endsWith(".image")
                    || !owned.getCanonicalFile().equals(owned.getAbsoluteFile()))
                throw new IOException("This setup contains an unsupported retained image reference.");
            if (!owned.isFile() || owned.length() == 0 || owned.length() > 20L * 1024 * 1024)
                throw new IOException("A saved image is unavailable. The setup and your current choices were kept.");
            references.add((String)uri);
        }
        return references;
    }

    private void verifyManagedIcons(Map<String,PreferenceSnapshot> target) throws IOException {
        for (String value : nativeIconValues(target)) {
            if (!value.startsWith("image:")) continue;
            File icon = new File(new File(context.getFilesDir(),"icons"),value.substring(6));
            if (!icon.isFile() || icon.length() == 0 || icon.length() > 20L * 1024 * 1024)
                throw new IOException("A personal icon is unavailable on this TV. No settings were changed.");
        }
    }

    public Map<String, PreferenceSnapshot> prepareOriginalBackup(PreferenceSnapshot incoming) throws Exception {
        Map<String,Object> values = incoming.values();
        JSONObject settings = LegacySetupSettings.capture(values);
        Map<String,Object> checked = LegacySetupSettings.validate(settings);
        checked.values().removeAll(Collections.singleton(null));
        if (!checked.equals(values)) throw new IOException("This backup includes unsupported settings. Nothing was changed.");
        settings.put("keys",new JSONArray(values.keySet()));
        Map<String,?> local = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).getAll();
        for (String key : Arrays.asList("custom_wallpaper_uri", "custom_video_uri")) {
            Object value = values.get(key);
            if (value instanceof String && !((String)value).isEmpty() && !value.equals(local.get(key)))
                throw new IOException("Personal artwork must already be selected on this TV. No files or access permissions were imported.");
        }
        Set<String> iconReferences = new LinkedHashSet<>();
        if (values.containsKey("app_visibility")) {
            JSONObject next = new JSONObject((String)values.get("app_visibility"));
            Object stored = local.get("app_visibility");
            JSONObject current = new JSONObject(stored instanceof String ? (String)stored : "{}");
            for (Iterator<String> ids = next.keys(); ids.hasNext();) {
                String id = ids.next(); JSONObject choice = next.getJSONObject(id);
                if (!choice.has("customIconUri")) continue;
                Object uri = choice.get("customIconUri");
                if (uri instanceof String && ((String)uri).isEmpty()) continue;
                JSONObject existing = current.optJSONObject(id);
                if (existing == null || !Objects.equals(uri, existing.opt("customIconUri")))
                    throw new IOException("A personal icon must already be selected on this TV. Choose it locally before importing this backup.");
                iconReferences.add((String)uri);
            }
        }
        PresentationMappingContext identities = com.jeremykenedy.firetv.ui.nativeapp.migration.AndroidPresentationInventory.read(context,incoming);
        if (!iconReferences.isEmpty()) {
            Map<String,String> verified = new LinkedHashMap<>(identities.icons);
            verified.putAll(com.jeremykenedy.firetv.ui.nativeapp.migration.AndroidLegacyIconMigration.verifyExisting(context,iconReferences));
            identities = new PresentationMappingContext(identities.components,identities.feeds,identities.items,verified,
                    identities.inputs,identities.advertisedIconPacks,identities.privateMedia.values(),identities.ambiguousInputs);
        }
        Map<String,PreferenceSnapshot> target = prepareLegacy(settings,identities,true);
        Map<String,Object> raw = new LinkedHashMap<>(target.get("launcher_prefs").values());
        for (Map.Entry<String,Object> entry : PresentationState.capture(context).get("launcher_prefs").values().entrySet())
            if (!values.containsKey(entry.getKey()) && entry.getKey().startsWith("color_")) raw.put(entry.getKey(),entry.getValue());
        target = new LinkedHashMap<>(target);
        target.put("launcher_prefs",new PreferenceSnapshot(raw));
        verifyVideo(values);
        verifyImages(values);
        verifyManagedIcons(target);
        return target;
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
        present.remove("source_status_bar_off");
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
        Map<String, PreferenceSnapshot> current = PresentationState.capture(context);
        boolean groupsRequested = present.containsKey("widgets_config") || present.containsKey("enable_widgets") || present.containsKey("hide_watch_next");
        if (groupsRequested) {
            Map<String,Object> local = current.get("launcher_prefs").values();
            Map<String,Object> layout = current.get("native_layout").values();
            Object mode = layout.get("mode");
            boolean enabled = mode == null || "BOTH".equals(mode) || "WIDGETS_ONLY".equals(mode);
            Object hidden = layout.get("hidden");
            if (hidden instanceof String && Arrays.asList(((String)hidden).split("\n")).contains("widget:grouped-widgets")) enabled = false;
            if (!present.containsKey("enable_widgets")) present.put("enable_widgets",enabled);
            if (!present.containsKey("hide_watch_next")) present.put("hide_watch_next","NONE".equals(mode));
            if (!present.containsKey("widgets_config")) {
                Object existing = current.get("native_layout").values().get("widget_groups");
                if (existing instanceof String) present.put("widgets_config",existing);
                else if (local.get("widgets_config") instanceof String) present.put("widgets_config",local.get("widgets_config"));
            }
        }
        Map<String, PreferenceSnapshot> empty = new LinkedHashMap<>();
        for (String file : LegacyPresentationMapper.FILES)
            if (!file.equals("launcher_prefs"))
                empty.put(file, new PreferenceSnapshot(Collections.emptyMap()));
        LegacyPresentationMapper.Plan plan =
                LegacyPresentationMapper.plan(new PreferenceSnapshot(present), identities, empty);
        WidgetGroupPresentationPlan grouped = WidgetGroupPresentationPlan.prepare(plan);
        Set<String> unresolved = new LinkedHashSet<>(grouped.unmappedLegacyKeys);
        unresolved.removeAll(LegacyPresentationMapper.directlyConsumedPresentationKeys(present));
        if (!unresolved.isEmpty())
            throw new IOException(
                    "Unsupported presentation choices: " + String.join(", ", unresolved)
                            + ". No settings or images were changed.");
        Map<String, PreferenceSnapshot> target = new LinkedHashMap<>(current);
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
            if (file.getKey().equals("native_icons")) {
                PreferenceSnapshot patch = plan.patches.get(file.getKey());
                target.put(file.getKey(),new PreferenceSnapshot(mergeLegacyIcons(current.get(file.getKey()).values(),
                        patch == null ? Collections.emptyMap() : patch.values(),present,identities)));
                continue;
            }
            Map<String, Object> values = new LinkedHashMap<>(target.get(file.getKey()).values());
            for (String key : file.getValue()) values.remove(key);
            PreferenceSnapshot patch = plan.patches.get(file.getKey());
            if (patch != null) values.putAll(patch.values());
            target.put(file.getKey(), new PreferenceSnapshot(values));
        }
        Map<String,Object> preparedGroups = grouped.target.stores.get("native_layout").values();
        if (groupsRequested && preparedGroups.containsKey("widget_groups")) {
            Map<String,Object> layout = new LinkedHashMap<>(target.get("native_layout").values());
            Map<String,Object> prepared = preparedGroups;
            layout.put("widget_groups",prepared.get("widget_groups"));
            if (saved.containsKey("enable_widgets") || saved.containsKey("hide_watch_next")) layout.put("mode",prepared.get("mode"));
            if (Boolean.TRUE.equals(present.get("enable_widgets"))) {
                Set<String> hidden = new LinkedHashSet<>();
                Object previous = layout.get("hidden");
                if (previous instanceof String && !((String)previous).isEmpty()) hidden.addAll(Arrays.asList(((String)previous).split("\n")));
                hidden.add("widget:clock");
                if (Boolean.TRUE.equals(saved.get("enable_widgets")) && !Boolean.TRUE.equals(present.get("hide_watch_next"))) hidden.remove("widget:grouped-widgets");
                layout.put("hidden",String.join("\n",hidden));
            }
            target.put("native_layout",new PreferenceSnapshot(layout));
        }
        Map<String, Object> raw = new LinkedHashMap<>(target.get("launcher_prefs").values());
        if (saved.containsKey("minimal_status_bar") && !saved.containsKey("source_status_bar_off"))
            raw.remove("source_status_bar_off");
        for (Map.Entry<String, Object> change :
                LegacySetupSettings.toApply(settings, raw, icons).entrySet()) {
            if (change.getValue() == null) raw.remove(change.getKey());
            else raw.put(change.getKey(), change.getValue());
        }
        target.put("launcher_prefs", new PreferenceSnapshot(raw));
        PresentationState.validate(target);
        return target;
    }

    private static Map<String,Object> mergeLegacyIcons(Map<String,Object> current,Map<String,Object> patch,
            Map<String,Object> legacy,PresentationMappingContext identities) {
        Set<String> references = new LinkedHashSet<>();
        if (legacy.get("app_visibility") instanceof String)
            references.addAll(com.jeremykenedy.firetv.ui.nativeapp.migration.LegacyJson.object((String)legacy.get("app_visibility")).keySet());
        if (legacy.get("factory_icon_packages") instanceof Set)
            for (Object value : (Set<?>)legacy.get("factory_icon_packages")) references.add((String)value);
        Map<String,Object> result = new LinkedHashMap<>(current);
        Map<String,Object> pending = RetainedHomeChoices.read("native_icons",current.get(RetainedHomeChoices.KEY));
        for (String reference : references) {
            String resolved = iconKey(reference,identities);
            for (String key : new ArrayList<>(result.keySet())) {
                String tile = key.startsWith("edited:") ? key.substring(7) : key;
                if (tile.equals(resolved) || RetainedHomeChoices.packageName(reference) && tile.startsWith(reference+"/")) result.remove(key);
            }
            for (String key : new ArrayList<>(pending.keySet())) {
                String previous = key.substring(5);
                if (previous.equals(reference) || resolved != null && resolved.equals(iconKey(previous,identities))
                        || RetainedHomeChoices.packageName(reference) && previous.startsWith(reference+"/")) pending.remove(key);
            }
        }
        for (Map.Entry<String,Object> entry : patch.entrySet())
            if (!entry.getKey().equals(RetainedHomeChoices.KEY)) result.put(entry.getKey(),entry.getValue());
        pending.putAll(RetainedHomeChoices.read("native_icons",patch.get(RetainedHomeChoices.KEY)));
        if (pending.isEmpty()) result.remove(RetainedHomeChoices.KEY);
        else result.put(RetainedHomeChoices.KEY,RetainedHomeChoices.write("native_icons",pending));
        return result;
    }

    private static String iconKey(String reference,PresentationMappingContext identities) {
        if (reference.startsWith("tvinput_name:")) {
            String input = identities.inputs.get(reference);
            return input == null ? null : "input:"+input;
        }
        String component = identities.components.get(reference);
        if (component != null) return component;
        if (!RetainedHomeChoices.component(reference)) return null;
        int slash = reference.indexOf('/');
        String type = reference.substring(slash+1);
        return reference.substring(0,slash)+"/"+(type.startsWith(".") ? reference.substring(0,slash)+type : type);
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
            if (item.has("nativeVersion") && (!item.has("native")
                    || !(item.get("nativeVersion") instanceof Integer)
                    || item.getInt("nativeVersion") != 2))
                throw new IOException("This native setup version is not supported.");
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

    private static Collection<String> nativeIconValues(Map<String,PreferenceSnapshot> state) throws IOException {
        Set<String> values = new LinkedHashSet<>();
        Map<String,Object> icons = state.get("native_icons").values();
        for (Map.Entry<String,Object> entry : icons.entrySet()) {
            if (entry.getKey().equals(RetainedHomeChoices.KEY) || entry.getKey().startsWith("edited:")) continue;
            if (!(entry.getValue() instanceof String)) throw new IOException("Invalid saved icon choice.");
            values.add((String)entry.getValue());
        }
        for (Object value : RetainedHomeChoices.read("native_icons",icons.get(RetainedHomeChoices.KEY)).values())
            values.add((String)value);
        return values;
    }

    private Map<String, PreferenceSnapshot> preserveNativeImages(
            Map<String, PreferenceSnapshot> state,
            JSONObject images,
            Map<String, String> copies,
            List<File> created)
            throws Exception {
        Map<String, PreferenceSnapshot> next = new LinkedHashMap<>(state);
        for (String value : nativeIconValues(state)) {
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
        for (String value : nativeIconValues(state)) {
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
