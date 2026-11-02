package com.jeremykenedy.firetv.ui.nativeapp.setups;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import com.jeremykenedy.firetv.ui.nativeapp.appearance.PaletteRecipes;
import com.jeremykenedy.firetv.ui.nativeapp.migration.PreferenceSnapshot;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** A private journal containing only the keys changed by a holiday preset. */
public final class HolidayPresetTransaction {
    private static final String APPEARANCE = "source_appearance", FOCUS = "native_focus",
                                PACKS = "native_icon_packs";
    private HolidayPresetTransaction() {}

    public static void apply(Context context, String id, boolean overlay, boolean animated, boolean icons)
        throws IOException {
        PaletteRecipes.Theme theme = PaletteRecipes.find(id);
        if (theme == null)
            throw new IllegalArgumentException("Choose a holiday theme.");
        synchronized (PresentationState.class) {
            assertWritable(context);
            Map<String, Map<String, Object>> target = new LinkedHashMap<>();
            Map<String, Object> appearance = new LinkedHashMap<>();
            String layer = overlay ? "overlay" : "base";
            appearance.put(layer, theme.motif);
            appearance.put(layer + "_palette", id);
            appearance.put(layer + "_frozen", !animated);
            if (!overlay)
                appearance.put("scene", "");
            target.put(APPEARANCE, appearance);
            if (icons) {
                Map<String, Object> focus = new LinkedHashMap<>();
                focus.put("border_enabled", true);
                focus.put("border_rgb", theme.accent & 0xffffff);
                focus.put("border_opacity", 100);
                focus.put("fill_enabled", false);
                focus.put("legacy_app_default", null);
                target.put(FOCUS, focus);
                target.put(PACKS,
                    Collections.<String, Object>singletonMap("package", PaletteRecipes.holidayPack(id)));
            }
            Map<String, Object> journal = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Object>> file : target.entrySet()) {
                Map<String, ?> current = prefs(context, file.getKey()).getAll();
                for (String key : file.getValue().keySet()) {
                    String compound = file.getKey() + "/" + key;
                    journal.put("present/" + compound, current.containsKey(key));
                    if (current.containsKey(key))
                        journal.put("value/" + compound, current.get(key));
                }
            }
            save(context, new PreferenceSnapshot(journal));
            try {
                for (Map.Entry<String, Map<String, Object>> file : target.entrySet()) {
                    if (!write(context, file.getKey(), file.getValue()))
                        throw new IOException("Holiday theme could not be saved.");
                }
                for (Map.Entry<String, Map<String, Object>> file : target.entrySet())
                    for (Map.Entry<String, Object> entry : file.getValue().entrySet())
                        if (!Objects.equals(
                                prefs(context, file.getKey()).getAll().get(entry.getKey()), entry.getValue()))
                            throw new IOException("Holiday theme could not be verified.");
                clear(context);
            } catch (Exception failure) {
                try {
                    restore(context, new PreferenceSnapshot(journal));
                    clear(context);
                } catch (Exception recovery) {
                    throw new IOException(
                        "Holiday theme could not be saved; restore the interrupted change before continuing.",
                        recovery);
                }
                throw new IOException(
                    "Holiday theme could not be saved. Previous choices were restored.", failure);
            }
        }
    }
    public static void assertReadable(Context context) {
        if (hasRecovery(context))
            throw new IllegalStateException(
                "An interrupted holiday change must be restored. Return to Home and choose Retry.");
    }
    public static void assertWritable(Context context) {
        for (String name : new String[] {"holiday-preset-v1.snapshot", "saved-setup-apply-v1.json",
                 "portable-household-import-v1.snapshot", "presentation-migration-v1.snapshot"}) {
            File file = new File(context.getNoBackupFilesDir(), name);
            if (file.exists() || new File(file + ".bak").exists() || new File(file + ".new").exists())
                throw new IllegalStateException(
                    "Restore the interrupted presentation change before editing settings.");
        }
    }
    public static boolean hasRecovery(Context context) {
        File path = path(context);
        return path.exists() || new File(path + ".bak").exists() || new File(path + ".new").exists();
    }
    public static void recover(Context context) throws IOException {
        synchronized (PresentationState.class) {
            if (hasRecovery(context)) {
                restore(context, read(context));
                clear(context);
            }
        }
    }
    private static void restore(Context context, PreferenceSnapshot snapshot) throws IOException {
        Map<String, Object> values = snapshot.values();
        Map<String, Map<String, Object>> changes = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String name = entry.getKey();
            boolean presence = name.startsWith("present/");
            boolean valueEntry = name.startsWith("value/");
            if (!presence && !valueEntry)
                throw new IOException("Invalid holiday recovery.");
            String compound = name.substring(presence ? 8 : 6);
            int split = compound.indexOf('/');
            if (split < 0)
                throw new IOException("Invalid holiday recovery.");
            String file = compound.substring(0, split), key = compound.substring(split + 1);
            if (!allowed(file, key))
                throw new IOException("Invalid holiday recovery.");
            Object present = values.get("present/" + compound);
            if (!(present instanceof Boolean)
                || (Boolean.TRUE.equals(present) != values.containsKey("value/" + compound)))
                throw new IOException("Invalid holiday recovery.");
            if (valueEntry)
                continue;
            Object value = Boolean.TRUE.equals(present) ? values.get("value/" + compound) : null;
            if (!changes.containsKey(file))
                changes.put(file, new LinkedHashMap<String, Object>());
            changes.get(file).put(key, value);
        }
        if (changes.isEmpty())
            throw new IOException("Invalid holiday recovery.");
        for (Map.Entry<String, Map<String, Object>> file : changes.entrySet())
            if (!write(context, file.getKey(), file.getValue()))
                throw new IOException("Holiday recovery could not be restored.");
        for (Map.Entry<String, Map<String, Object>> file : changes.entrySet())
            for (Map.Entry<String, Object> entry : file.getValue().entrySet()) {
                Map<String, ?> actual = prefs(context, file.getKey()).getAll();
                if (entry.getValue() == null ? actual.containsKey(entry.getKey())
                                             : !Objects.equals(actual.get(entry.getKey()), entry.getValue()))
                    throw new IOException("Holiday recovery could not be verified.");
            }
    }
    private static boolean allowed(String file, String key) {
        if (APPEARANCE.equals(file))
            return Arrays
                .asList("base", "base_palette", "base_frozen", "scene", "overlay", "overlay_palette",
                    "overlay_frozen")
                .contains(key);
        if (FOCUS.equals(file))
            return Arrays
                .asList(
                    "border_enabled", "border_rgb", "border_opacity", "fill_enabled", "legacy_app_default")
                .contains(key);
        return PACKS.equals(file) && "package".equals(key);
    }
    private static SharedPreferences prefs(Context context, String name) {
        return context.getSharedPreferences(name, Context.MODE_PRIVATE);
    }
    private static boolean write(Context context, String file, Map<String, Object> values) {
        SharedPreferences.Editor edit = prefs(context, file).edit();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null)
                edit.remove(key);
            else if (value instanceof String)
                edit.putString(key, (String) value);
            else if (value instanceof Integer)
                edit.putInt(key, (Integer) value);
            else if (value instanceof Boolean)
                edit.putBoolean(key, (Boolean) value);
            else if (value instanceof Long)
                edit.putLong(key, (Long) value);
            else if (value instanceof Float)
                edit.putFloat(key, (Float) value);
            else {
                @SuppressWarnings("unchecked") Set<String> strings = (Set<String>) value;
                edit.putStringSet(key, strings);
            }
        }
        return edit.commit();
    }
    private static File path(Context context) {
        return new File(context.getNoBackupFilesDir(), "holiday-preset-v1.snapshot");
    }
    private static byte[] digest(byte[] bytes) throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException failure) {
            throw new IOException(failure);
        }
    }
    private static void save(Context context, PreferenceSnapshot snapshot) throws IOException {
        byte[] data = snapshot.encode();
        AtomicFile file = new AtomicFile(path(context));
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(data);
            output.write(digest(data));
            file.finishWrite(output);
        } catch (IOException failure) {
            if (output != null)
                file.failWrite(output);
            throw failure;
        }
        if (!snapshot.values().equals(read(context).values()))
            throw new IOException("Holiday recovery could not be verified.");
    }
    private static PreferenceSnapshot read(Context context) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream input = new AtomicFile(path(context)).openRead()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = input.read(buffer)) != -1) {
                if (bytes.size() + n > PreferenceSnapshot.MAX_BYTES + 32)
                    throw new IOException("Holiday recovery is too large.");
                bytes.write(buffer, 0, n);
            }
        }
        byte[] all = bytes.toByteArray();
        if (all.length < 32)
            throw new IOException("Invalid holiday recovery.");
        byte[] data = Arrays.copyOf(all, all.length - 32);
        if (!MessageDigest.isEqual(digest(data), Arrays.copyOfRange(all, all.length - 32, all.length)))
            throw new IOException("Invalid holiday recovery.");
        return PreferenceSnapshot.decode(data);
    }
    private static void clear(Context context) throws IOException {
        new AtomicFile(path(context)).delete();
        if (hasRecovery(context))
            throw new IOException("Holiday recovery could not be resolved.");
    }
}
