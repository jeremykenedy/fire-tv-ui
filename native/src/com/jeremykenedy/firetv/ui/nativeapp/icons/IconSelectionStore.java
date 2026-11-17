package com.jeremykenedy.firetv.ui.nativeapp.icons;

import android.content.Context;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.RetainedHomeChoices;
import com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState;

/** Individual artwork choices; source images outside this app are never modified. */
public final class IconSelectionStore {
    public static final String FACTORY = "factory";
    public static final String THEME = "theme";
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_MANAGED_BYTES = 20 * 1024 * 1024;

    private IconSelectionStore() {}

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("native_icons", Context.MODE_PRIVATE);
    }

    public static String choice(Context context, String tileKey) {
        return preferences(context).getString(tileKey, THEME);
    }

    public static boolean isValidTileKey(String key) {
        return key != null && key.length() <= 2048 && key.indexOf('\0') < 0
                && (key.startsWith("input:") ? key.length() > 6 : ComponentName.unflattenFromString(key) != null);
    }

    public static void save(Context context, String tileKey, String value) throws IOException {
        if (!isValidTileKey(tileKey)) {
            throw new IOException("Choose an app first.");
        }
        boolean valid = FACTORY.equals(value) || THEME.equals(value)
                || value != null && value.startsWith("catalog:") && IconCatalog.find(value.substring(8)) != null
                || imageFile(context, value) != null && imageFile(context, value).isFile();
        if (!valid) throw new IOException("This icon is unavailable. Choose another image.");
        synchronized (PresentationState.class) {
            String previous = choice(context, tileKey);
            SharedPreferences.Editor edit = preferences(context).edit();
            if (THEME.equals(value)) edit.remove(tileKey); else edit.putString(tileKey, value);
            edit.putBoolean("edited:" + tileKey, true);
            if (!edit.commit()) throw new IOException("The icon could not be saved. Try again.");
            if (!value.equals(previous)) discardPreparedImage(context, previous);
        }
    }

    public static Drawable resolve(Context context, String tileKey, Drawable factoryIcon, Drawable themeIcon) {
        String selected = choice(context, tileKey);
        if (FACTORY.equals(selected)) return factoryIcon;
        if (THEME.equals(selected)) return themeIcon == null ? IconPackStore.resolve(context, tileKey, factoryIcon) : themeIcon;
        if (selected.startsWith("catalog:")) {
            IconCatalog.Entry entry = IconCatalog.find(selected.substring(8));
            if (entry != null) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 4;
                Bitmap image = BitmapFactory.decodeResource(context.getResources(), entry.drawable, options);
                if (image != null) return new BitmapDrawable(context.getResources(), image);
            }
        }
        File file = imageFile(context, selected);
        if (file != null && file.isFile()) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (options.outWidth < 1 || options.outHeight < 1 || file.length() > MAX_MANAGED_BYTES
                    || (long) options.outWidth * options.outHeight > 40000000) return factoryIcon;
            options.inSampleSize = 1;
            while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 512) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            Bitmap image = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (image != null) return new BitmapDrawable(context.getResources(), image);
        }
        return factoryIcon;
    }

    public static String label(Context context, String tileKey) {
        String selected = choice(context, tileKey);
        if (FACTORY.equals(selected)) return "Factory icon";
        if (THEME.equals(selected)) return "Theme icon";
        IconCatalog.Entry entry = selected.startsWith("catalog:") ? IconCatalog.find(selected.substring(8)) : null;
        return entry != null ? entry.label : "My image";
    }

    /** Run on a worker, then save the returned choice only if the picker is still open. */
    public static String prepareImage(Context context, InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Image import cancelled.");
            if (bytes.size() + count > MAX_BYTES) throw new IOException("Choose an image smaller than 8 MB.");
            bytes.write(buffer, 0, count);
        }
        byte[] data = bytes.toByteArray();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (options.outWidth < 1 || options.outHeight < 1
                || (long) options.outWidth * options.outHeight > 40000000) {
            throw new IOException("Choose a readable image no larger than 40 megapixels.");
        }
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 512) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        Bitmap image = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (image == null) throw new IOException("This image could not be opened.");
        String selected = "image:" + UUID.randomUUID() + ".png";
        File target = imageFile(context, selected);
        try {
            if (!target.getParentFile().isDirectory() && !target.getParentFile().mkdirs()) {
                throw new IOException("Image storage is unavailable.");
            }
            try (FileOutputStream output = new FileOutputStream(target)) {
                if (!image.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("Image could not be saved.");
            }
            return selected;
        } catch (IOException error) {
            target.delete();
            throw error;
        } finally {
            image.recycle();
        }
    }

    public static void discardPreparedImage(Context context, String value) {
        File file = imageFile(context, value);
        if (file == null) return;
        synchronized (PresentationState.class) {
            java.util.Map<String, ?> saved = preferences(context).getAll();
            if (saved.containsValue(value)) return;
            try {
                if (RetainedHomeChoices.read("native_icons", saved.get(RetainedHomeChoices.KEY)).containsValue(value)) return;
            } catch (IllegalArgumentException invalid) {
                return;
            }
            file.delete();
        }
    }

    private static File imageFile(Context context, String value) {
        if (value == null || !value.matches("image:[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}\\.png")) return null;
        return new File(new File(context.getFilesDir(), "icons"), value.substring(6));
    }
}
