package com.jeremykenedy.firetv.ui.nativeapp.setups;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;

import com.jeremykenedy.firetv.ui.nativeapp.appearance.AppearancePreferences;
import com.jeremykenedy.firetv.ui.nativeapp.accessibility.AccessibilityPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppGridOptions;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.HomeTileOrder;
import com.jeremykenedy.firetv.ui.nativeapp.cards.CardOptions;
import com.jeremykenedy.firetv.ui.nativeapp.focus.FocusOptions;
import com.jeremykenedy.firetv.ui.nativeapp.folders.FolderPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.icons.IconCatalog;
import com.jeremykenedy.firetv.ui.nativeapp.inputs.InputEntry;
import com.jeremykenedy.firetv.ui.nativeapp.inputs.InputOptions;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutOptions;
import com.jeremykenedy.firetv.ui.nativeapp.migration.PreferenceSnapshot;
import com.jeremykenedy.firetv.ui.nativeapp.widget.breathing.BreathingSession;
import com.jeremykenedy.firetv.ui.nativeapp.widget.worldclock.WorldClocks;

import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Explicit presentation boundary shared by local setups and portable backup. */
public final class PresentationState {
    private static final Map<String, Set<String>> KEYS = new LinkedHashMap<>();

    static {
        add(
                "native_app_grid",
                "columns rows horizontal order home_order hidden favorites show_all_apps_button");
        add(
                "native_focus",
                "duration_millis growth_percent border_enabled border_rgb border_opacity"
                        + " fill_enabled fill_rgb fill_opacity legacy_app_default");
        add("native_layout", "card_width_dp card_height_dp order hidden mode");
        add(
                "native_cards",
                "schema_version global_seconds feed_seconds hidden_items hidden_series item_order");
        add("native_folders", "schema_version folders");
        add("native_world_clocks", "clocks");
        add(AccessibilityPreferences.FILE, "text_percent high_contrast");
        add("native_breathing_break", "inhale hold exhale rest duration reduced_motion");
        add("native_icons", "");
        add("native_inputs", "");
        add("native_icon_packs", "package");
        KEYS.put(
                AppearancePreferences.FILE,
                Collections.unmodifiableSet(new LinkedHashSet<>(AppearancePreferences.KEYS)));
        add("launcher_prefs", "");
    }

    private PresentationState() {}

    private static void add(String file, String keys) {
        KEYS.put(
                file,
                Collections.unmodifiableSet(
                        new LinkedHashSet<>(
                                keys.isEmpty()
                                        ? Collections.<String>emptyList()
                                        : Arrays.asList(keys.split(" ")))));
    }

    public static Set<String> files() {
        return Collections.unmodifiableSet(KEYS.keySet());
    }

    private static boolean owns(String file, String key) {
        if (file.equals("native_inputs"))
            return key.equals("order") || key.startsWith("name:") || key.startsWith("hidden:");
        return file.equals("native_icons")
                ? key.startsWith("input:")
                        || key.length() <= 512 && key.matches("[A-Za-z0-9_.$:/-]+")
                : KEYS.get(file).contains(key);
    }

    public static synchronized Map<String, PreferenceSnapshot> capture(Context context) throws Exception {
        if (HolidayPresetTransaction.hasRecovery(context)) throw new IOException("Restore the interrupted holiday change first.");
        Map<String, PreferenceSnapshot> result = new LinkedHashMap<>();
        for (String file : files()) {
            Map<String, ?> all = context.getSharedPreferences(file, Context.MODE_PRIVATE).getAll();
            Map<String, Object> values = new LinkedHashMap<>();
            if (file.equals("launcher_prefs")) {
                for (Map.Entry<String, Object> entry :
                        LegacySetupSettings.validate(LegacySetupSettings.capture(all)).entrySet())
                    if (entry.getValue() != null) values.put(entry.getKey(), entry.getValue());
            } else
                for (Map.Entry<String, ?> entry : all.entrySet())
                    if (owns(file, entry.getKey())) values.put(entry.getKey(), entry.getValue());
            result.put(file, new PreferenceSnapshot(values));
        }
        validate(result);
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, PreferenceSnapshot> optionalStores(Map<String, PreferenceSnapshot> state) {
        if (state == null || state.containsKey(AccessibilityPreferences.FILE)) return state;
        Map<String, PreferenceSnapshot> result = new LinkedHashMap<>(state);
        result.put(AccessibilityPreferences.FILE, new PreferenceSnapshot(Collections.emptyMap()));
        return result;
    }

    public static void validate(Map<String, PreferenceSnapshot> state) throws Exception {
        state = optionalStores(state);
        if (state == null || !state.keySet().equals(files()))
            throw new IOException("The saved presentation is incomplete or unsupported.");
        for (String file : files()) {
            Map<String, Object> values = state.get(file).values();
            if (file.equals("launcher_prefs")) {
                Map<String, Object> checked =
                        LegacySetupSettings.validate(LegacySetupSettings.capture(values));
                checked.values().removeAll(Collections.singleton(null));
                if (!checked.equals(values))
                    throw new IOException("The setup contains settings outside presentation.");
            } else
                for (Map.Entry<String, Object> entry : values.entrySet()) {
                    if (!owns(file, entry.getKey()))
                        throw new IOException("An unsupported setting was found.");
                    if (file.equals("native_icons")
                            && entry.getKey().startsWith("input:")
                            && !InputEntry.validId(entry.getKey().substring(6)))
                        throw new IOException("Invalid input artwork identity.");
                    if (file.equals("native_icons")
                            && (!(entry.getValue() instanceof String)
                                    || !((String) entry.getValue())
                                            .matches(
                                                    "factory|theme|catalog:[A-Za-z0-9_-]+|image:[a-f0-9-]{36}\\.png")))
                        throw new IOException("An unsupported icon was found.");
                    if (file.equals("native_icons")
                            && ((String) entry.getValue()).startsWith("catalog:")
                            && IconCatalog.find(((String) entry.getValue()).substring(8)) == null)
                        throw new IOException("A saved icon is not in the reviewed catalog.");
                }
        }
        Map<String, Object> gridDefaults = new LinkedHashMap<>();
        gridDefaults.put("columns", 5);
        gridDefaults.put("rows", 0);
        gridDefaults.put("horizontal", false);
        gridDefaults.put("order", "");
        gridDefaults.put("hidden", "");
        gridDefaults.put("favorites", "");
        Map<String, Object> grid = defaults("native_app_grid", state, gridDefaults);
        new AppGridOptions(
                (Integer) grid.get("columns"),
                (Integer) grid.get("rows"),
                (Boolean) grid.get("horizontal"),
                lines(grid.get("order")),
                new LinkedHashSet<>(lines(grid.get("hidden"))),
                new LinkedHashSet<>(lines(grid.get("favorites"))));
        Object homeOrder = state.get("native_app_grid").values().get("home_order");
        if (homeOrder != null) new HomeTileOrder(lines(homeOrder));
        LayoutOptions.fromBackup(
                defaults("native_layout", state, LayoutOptions.defaults(320, 180).backupValues()));
        CardOptions.fromBackup(
                defaults("native_cards", state, CardOptions.defaults().backupValues()));
        Map<String, Object> f = state.get("native_focus").values();
        if (f.containsKey(FocusOptions.LEGACY_APP_DEFAULT)
                && !(f.get(FocusOptions.LEGACY_APP_DEFAULT) instanceof Boolean))
            throw new IOException("Invalid legacy app focus presentation.");
        FocusOptions d = FocusOptions.defaults();
        new FocusOptions(
                integer(f, "duration_millis", d.durationMillis),
                integer(f, "growth_percent", d.growthPercent),
                flag(f, "border_enabled", d.borderEnabled),
                integer(f, "border_rgb", d.borderRgb),
                integer(f, "border_opacity", d.borderOpacity),
                flag(f, "fill_enabled", d.fillEnabled),
                integer(f, "fill_rgb", d.fillRgb),
                integer(f, "fill_opacity", d.fillOpacity));
        Map<String, Object> folders = state.get("native_folders").values();
        if (folders.containsKey("schema_version")
                && !Integer.valueOf(1).equals(folders.get("schema_version")))
            throw new IOException("Unsupported folders version.");
        FolderPreferences.decode((String) folders.getOrDefault("folders", "[]"));
        Map<String, Object> inputs = state.get("native_inputs").values();
        List<String> inputOrder = Collections.emptyList();
        Set<String> hiddenInputs = new LinkedHashSet<>(), explicitInputs = new LinkedHashSet<>();
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key.equals("order")) {
                if (!(value instanceof String)) throw new IOException("Invalid input order type.");
                inputOrder = InputOptions.parseOrder((String) value);
            } else {
                String id = key.substring(key.startsWith("name:") ? 5 : 7);
                if (!InputEntry.validId(id)) throw new IOException("Invalid input identity.");
                if (key.startsWith("name:")) {
                    if (!(value instanceof String)
                            || !InputEntry.name((String) value).equals(value))
                        throw new IOException("Invalid input name.");
                } else {
                    if (!(value instanceof Boolean))
                        throw new IOException("Invalid input visibility type.");
                    explicitInputs.add(id);
                    if ((Boolean) value) hiddenInputs.add(id);
                }
            }
        }
        new InputOptions(inputOrder, hiddenInputs);
        explicitInputs.addAll(inputOrder);
        if (explicitInputs.size() > InputOptions.MAX_INPUTS)
            throw new IOException("Too many saved input visibility choices.");
        Object allApps = state.get("native_app_grid").values().get("show_all_apps_button");
        if (allApps != null && !(allApps instanceof Boolean))
            throw new IOException("Invalid All apps visibility type.");
        Map<String, Object> clocks = state.get("native_world_clocks").values();
        if (clocks.containsKey("clocks")) WorldClocks.decode((String) clocks.get("clocks"));
        Map<String, Object> breathing = state.get("native_breathing_break").values();
        new BreathingSession(
                integer(breathing, "inhale", 4),
                integer(breathing, "hold", 0),
                integer(breathing, "exhale", 6),
                integer(breathing, "rest", 0),
                integer(breathing, "duration", 120),
                flag(breathing, "reduced_motion", false),
                0,
                0,
                false);
        AppearancePreferences.validateSnapshot(state.get(AppearancePreferences.FILE).values());
        AccessibilityPreferences.validateSnapshot(state.get(AccessibilityPreferences.FILE).values());
        for (Object value : state.get("native_icon_packs").values().values())
            if (!(value instanceof String)
                    || (com.jeremykenedy.firetv.ui.nativeapp.appearance.PaletteRecipes.fromHolidayPack((String) value) == null && !((String) value)
                            .matches(
                                    "original|clean-line|muted-solid|[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")))
                throw new IOException("Invalid icon pack.");
    }

    public static void validatePortable(Map<String, PreferenceSnapshot> state) throws Exception {
        validate(state);
        Map<String, Object> legacy = state.get("launcher_prefs").values();
        for (String key :
                Arrays.asList("app_order", "folders", "widgets_config", "channel_visibility")) {
            Object value = legacy.get(key);
            if (value instanceof String && !((String) value).isEmpty())
                throw new IOException(
                        "This legacy presentation contains an unverified nested format. Its local"
                                + " settings have been kept; portable export requires migration"
                                + " first.");
        }
        if (legacy.containsKey("app_visibility")) {
            JSONObject apps = new JSONObject((String) legacy.get("app_visibility"));
            for (Iterator<String> keys = apps.keys(); keys.hasNext(); ) {
                JSONObject choices = apps.getJSONObject(keys.next());
                for (Iterator<String> properties = choices.keys(); properties.hasNext(); ) {
                    String key = properties.next();
                    if (!Arrays.asList("hidden", "hideWatchNext").contains(key)
                            || !(choices.get(key) instanceof Boolean))
                        throw new IOException(
                                "Legacy icon and unknown visibility data must be migrated before"
                                        + " portable export.");
                }
            }
        }
        for (Map.Entry<String, PreferenceSnapshot> file : state.entrySet())
            for (Map.Entry<String, Object> entry : file.getValue().values().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String) {
                    String text = (String) value;
                    if (text.startsWith("image:")
                            || text.startsWith("file:")
                            || text.startsWith("content:")
                            || text.contains("customIconUri")
                            || entry.getKey().matches("(custom_.*_uri|wallpaper_.*_uri)")
                                    && !text.isEmpty())
                        throw new IOException(
                                "Personal images and videos stay on this TV. Choose built-in"
                                    + " artwork before exporting or importing a portable backup.");
                }
            }
    }

    private static int integer(Map<String, Object> v, String k, int d) {
        return v.containsKey(k) ? (Integer) v.get(k) : d;
    }

    private static boolean flag(Map<String, Object> v, String k, boolean d) {
        return v.containsKey(k) ? (Boolean) v.get(k) : d;
    }

    private static List<String> lines(Object value) {
        String text = (String) value;
        return text.isEmpty()
                ? Collections.<String>emptyList()
                : Arrays.asList(text.split("\n", -1));
    }

    private static Map<String, Object> defaults(
            String file, Map<String, PreferenceSnapshot> state, Map<String, Object> initial) {
        Map<String, Object> values = new LinkedHashMap<>(initial);
        values.putAll(state.get(file).values());
        return values;
    }

    public static synchronized void apply(
            Context context, Map<String, PreferenceSnapshot> state, boolean icons)
            throws Exception {
        if (HolidayPresetTransaction.hasRecovery(context)) throw new IOException("Restore the interrupted holiday change first.");
        state = optionalStores(state);
        validate(state);
        if (hasRecovery(context))
            throw new IOException(
                    "An interrupted setup change must be restored before applying another setup.");
        Map<String, PreferenceSnapshot> before = capture(context);
        if (!before.get("native_breathing_break")
                .values()
                .equals(state.get("native_breathing_break").values())) {
            Map<String, ?> runtime =
                    context.getSharedPreferences("native_breathing_break", Context.MODE_PRIVATE)
                            .getAll();
            Object elapsed = runtime.get("elapsed");
            long duration =
                    integer(state.get("native_breathing_break").values(), "duration", 120) * 1000L;
            if (Boolean.TRUE.equals(runtime.get("running"))
                    || elapsed instanceof Long && (Long) elapsed > duration)
                throw new IOException(
                        "Pause or reset the current breathing session before applying different"
                                + " exercise settings.");
        }
        saveRecovery(context, before);
        Map<String, PreferenceSnapshot> latest = capture(context);
        for (String file : files())
            if (!before.get(file).values().equals(latest.get(file).values())) {
                resolveRecovery(context);
                throw new IOException(
                        "Presentation changed while preparing this setup. Apply it again.");
            }
        List<String> changed = new ArrayList<>();
        try {
            for (String file : files()) {
                if (!icons && (file.equals("native_icons") || file.equals("native_icon_packs")))
                    continue;
                Map<String, Object> target = new LinkedHashMap<>(state.get(file).values());
                Set<String> keys = new LinkedHashSet<>(KEYS.get(file));
                if (file.equals("launcher_prefs")) {
                    target =
                            LegacySetupSettings.toApply(
                                    LegacySetupSettings.capture(target),
                                    before.get(file).values(),
                                    icons);
                    keys.addAll(target.keySet());
                }
                if (file.equals("native_icons") || file.equals("native_inputs")) {
                    keys.addAll(before.get(file).values().keySet());
                    keys.addAll(target.keySet());
                }
                changed.add(file);
                if (!write(context, file, keys, target))
                    throw new IOException("Presentation could not be saved.");
                Map<String, ?> actual =
                        context.getSharedPreferences(file, Context.MODE_PRIVATE).getAll();
                for (String key : keys)
                    if (!Objects.equals(actual.get(key), target.get(key)))
                        throw new IOException("Presentation readback failed.");
            }
        } catch (Exception failure) {
            boolean restored = true;
            Collections.reverse(changed);
            for (String file : changed) {
                Set<String> keys = new LinkedHashSet<>(KEYS.get(file));
                keys.addAll(before.get(file).values().keySet());
                keys.addAll(state.get(file).values().keySet());
                try {
                    restored &= write(context, file, keys, before.get(file).values());
                } catch (RuntimeException failureDuringRestore) {
                    restored = false;
                }
            }
            try {
                Map<String, PreferenceSnapshot> restoredState = capture(context);
                for (String file : files())
                    restored &= restoredState.get(file).values().equals(before.get(file).values());
            } catch (Exception invalidReadback) {
                restored = false;
            }
            if (restored) resolveRecovery(context);
            throw new IOException(
                    restored
                            ? "Setup could not be applied. Previous presentation restored."
                            : "Setup could not be applied and restoration could not be verified.",
                    failure);
        }
        resolveRecovery(context);
    }

    private static File journalPath(Context context) {
        return new File(context.getNoBackupFilesDir(), "saved-setup-apply-v1.json");
    }

    private static AtomicFile journal(Context context) {
        return new AtomicFile(journalPath(context));
    }

    public static boolean hasRecovery(Context context) {
        File path = journalPath(context);
        return path.exists()
                || new File(path.getPath() + ".bak").exists()
                || new File(path.getPath() + ".new").exists();
    }

    private static void resolveRecovery(Context context) throws IOException {
        journal(context).delete();
        if (hasRecovery(context))
            throw new IOException(
                    "The presentation was written, but its private recovery record could not be"
                            + " removed. Restore the interrupted change before applying another"
                            + " setup.");
    }

    private static void saveRecovery(Context context, Map<String, PreferenceSnapshot> state)
            throws Exception {
        byte[] bytes =
                SavedSetupManager.encodeNative(state).toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 16 * 1024 * 1024)
            throw new IOException("Presentation recovery is too large.");
        AtomicFile file = journal(context);
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(bytes);
            file.finishWrite(output);
        } catch (Exception failure) {
            if (output != null) file.failWrite(output);
            throw failure;
        }
        Map<String, PreferenceSnapshot> saved = readRecovery(context);
        for (String name : files())
            if (!state.get(name).values().equals(saved.get(name).values()))
                throw new IOException("Private recovery could not be verified.");
    }

    private static Map<String, PreferenceSnapshot> readRecovery(Context context) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream input = journal(context).openRead()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > 16 * 1024 * 1024)
                    throw new IOException("Recovery is too large.");
                bytes.write(buffer, 0, count);
            }
        }
        return SavedSetupManager.decodeNative(
                new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8)));
    }

    public static synchronized void recover(Context context) throws Exception {
        if (HolidayPresetTransaction.hasRecovery(context)) throw new IOException("Restore the interrupted holiday change first.");
        Map<String, PreferenceSnapshot> saved = readRecovery(context);
        boolean success = true;
        for (String file : files()) {
            Set<String> keys = new LinkedHashSet<>(KEYS.get(file));
            keys.addAll(saved.get(file).values().keySet());
            Map<String, ?> current =
                    context.getSharedPreferences(file, Context.MODE_PRIVATE).getAll();
            if (file.equals("launcher_prefs")) keys.addAll(LegacySetupSettings.ownedKeys(current));
            else for (String key : current.keySet()) if (owns(file, key)) keys.add(key);
            success &= write(context, file, keys, saved.get(file).values());
        }
        Map<String, PreferenceSnapshot> restored = capture(context);
        for (String file : files())
            success &= saved.get(file).values().equals(restored.get(file).values());
        if (!success)
            throw new IOException(
                    "Previous presentation could not be restored. Private recovery has been kept.");
        resolveRecovery(context);
    }

    private static boolean write(
            Context context, String file, Set<String> keys, Map<String, Object> values) {
        SharedPreferences.Editor edit =
                context.getSharedPreferences(file, Context.MODE_PRIVATE).edit();
        for (String key : keys) {
            Object value = values.get(key);
            if (value == null) edit.remove(key);
            else if (value instanceof String) edit.putString(key, (String) value);
            else if (value instanceof Integer) edit.putInt(key, (Integer) value);
            else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
            else if (value instanceof Long) edit.putLong(key, (Long) value);
            else if (value instanceof Float) edit.putFloat(key, (Float) value);
            else if (value instanceof Set) {
                Set<String> strings = new LinkedHashSet<>();
                for (Object item : (Set<?>) value) strings.add((String) item);
                edit.putStringSet(key, strings);
            }
        }
        return edit.commit();
    }
}
