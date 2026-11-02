package com.jeremykenedy.firetv.ui.nativeapp.icons;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Xml;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.xmlpull.v1.XmlPullParser;

/** Reads only explicitly advertised icon packs and their component mappings. */
public final class IconPackStore {
    public static final String ORIGINAL = "original", CLEAN = "clean-line", MUTED = "muted-solid";
    public static final String[] ACTIONS = {"org.adw.launcher.THEMES", "com.gau.go.launcherex.theme"};
    public static final class Pack {
        public final String id, label;
        Pack(String id, String label) { this.id = id; this.label = label; }
    }
    private static String cachedPackage;
    private static long cachedVersion;
    private static Map<String, String> cachedMappings;
    private IconPackStore() {}

    public static String selected(Context context) {
        return context.getSharedPreferences("native_icon_packs", 0).getString("package", ORIGINAL);
    }

    public static List<Pack> available(Context context) {
        Map<String, Pack> found = new LinkedHashMap<>();
        PackageManager pm = context.getPackageManager();
        for (String action : ACTIONS) {
            for (ResolveInfo info : pm.queryIntentActivities(new Intent(action), 0)) {
                if (info.activityInfo == null || !info.activityInfo.enabled || !info.activityInfo.exported
                        || !info.activityInfo.applicationInfo.enabled) continue;
                String name = info.activityInfo.packageName;
                found.put(name, new Pack(name, String.valueOf(info.loadLabel(pm))));
            }
        }
        List<Pack> result = new ArrayList<>(found.values());
        Collections.sort(result, new Comparator<Pack>() {
            @Override public int compare(Pack a, Pack b) { return a.label.compareToIgnoreCase(b.label); }
        });
        result.add(0, new Pack(MUTED, "Muted solid"));
        result.add(0, new Pack(CLEAN, "Clean line"));
        result.add(0, new Pack(ORIGINAL, "None (original artwork)"));
        return result;
    }

    public static boolean select(Context context, String id) {
        for (Pack pack : available(context)) if (pack.id.equals(id)) {
            return context.getSharedPreferences("native_icon_packs", 0).edit().putString("package", id).commit();
        }
        return false;
    }

    public static void showPicker(final Activity activity, final Runnable changed) {
        final List<Pack> packs = available(activity);
        String[] labels = new String[packs.size()];
        int checked = 0;
        for (int i = 0; i < packs.size(); i++) {
            labels[i] = packs.get(i).label;
            if (packs.get(i).id.equals(selected(activity))) checked = i;
        }
        new AlertDialog.Builder(activity).setTitle("Icon pack").setSingleChoiceItems(labels, checked,
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (select(activity, packs.get(which).id)) { dialog.dismiss(); if (changed != null) changed.run(); }
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    /** Call from the artwork worker. Missing packs and missing mappings retain original artwork. */
    public static Drawable resolve(Context context, String tileKey, Drawable factory) {
        String pack = selected(context);
        if (CLEAN.equals(pack) || MUTED.equals(pack)) return styled(context, factory, CLEAN.equals(pack));
        if (ORIGINAL.equals(pack)) return factory;
        if (tileKey == null || tileKey.startsWith("input:")) return factory;
        ComponentName component = ComponentName.unflattenFromString(tileKey);
        if (component == null) return factory;
        try {
            Context external = context.createPackageContext(pack, 0);
            Resources resources = external.getResources();
            long version = context.getPackageManager().getPackageInfo(pack, 0).lastUpdateTime;
            String drawable;
            synchronized (IconPackStore.class) {
                if (!pack.equals(cachedPackage) || version != cachedVersion || cachedMappings == null) {
                    cachedMappings = mappings(external);
                    cachedPackage = pack; cachedVersion = version;
                }
                drawable = cachedMappings.get(component.flattenToString());
            }
            int id = drawable == null ? 0 : resources.getIdentifier(drawable, "drawable", pack);
            if (id == 0) return factory;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeResource(resources, id, options);
            if (options.outWidth > 0 && options.outHeight > 0) {
                if ((long) options.outWidth * options.outHeight > 40000000) return factory;
                options.inSampleSize = 1;
                while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 512) options.inSampleSize *= 2;
                options.inJustDecodeBounds = false;
                Bitmap image = BitmapFactory.decodeResource(resources, id, options);
                return image == null ? factory : new BitmapDrawable(context.getResources(), image);
            }
            return raster(context, resources.getDrawable(id, external.getTheme()));
        } catch (Exception unavailable) { return factory; }
    }

    private static Map<String, String> mappings(Context external) throws Exception {
        int xml = external.getResources().getIdentifier("appfilter", "xml", external.getPackageName());
        if (xml != 0) {
            android.content.res.XmlResourceParser parser = external.getResources().getXml(xml);
            try { return mappings(parser); } finally { parser.close(); }
        }
        try (InputStream input = new FilterInputStream(external.getAssets().open("appfilter.xml")) {
            private int total;
            @Override public int read() throws IOException {
                int value = super.read(); if (value >= 0 && ++total > 1048576) throw new IOException("Icon map too large"); return value;
            }
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                int count = super.read(bytes, offset, length); if (count > 0 && (total += count) > 1048576) throw new IOException("Icon map too large"); return count;
            }
        }) {
            XmlPullParser parser = Xml.newPullParser(); parser.setInput(input, "UTF-8");
            return mappings(parser);
        }
    }

    static Map<String, String> mappings(XmlPullParser parser) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        int events = 0;
        for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
            if (++events > 20000 || result.size() >= 4096) break;
            if (event != XmlPullParser.START_TAG || !"item".equals(parser.getName())) continue;
            String key = parser.getAttributeValue(null, "component"), value = parser.getAttributeValue(null, "drawable");
            if (key == null || key.length() > 2048 || value == null || !value.matches("[a-zA-Z0-9_]{1,200}")) continue;
            if (key.startsWith("ComponentInfo{") && key.endsWith("}")) key = key.substring(14, key.length() - 1);
            ComponentName component = ComponentName.unflattenFromString(key);
            if (component != null) result.put(component.flattenToString(), value);
        }
        return result;
    }

    private static Drawable raster(Context context, Drawable source) {
        if (source == null) return null;
        Bitmap bitmap = Bitmap.createBitmap(512, 288, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Rect old = source.copyBounds();
        int width = Math.max(1, source.getIntrinsicWidth()), height = Math.max(1, source.getIntrinsicHeight());
        float scale = Math.min(512f / width, 288f / height);
        int w = Math.max(1, Math.round(width * scale)), h = Math.max(1, Math.round(height * scale));
        try { source.setBounds((512-w)/2, (288-h)/2, (512+w)/2, (288+h)/2); source.draw(canvas); }
        finally { source.setBounds(old); }
        return new BitmapDrawable(context.getResources(), bitmap);
    }

    private static Drawable styled(Context context, Drawable source, boolean outline) {
        Drawable rendered = raster(context, source);
        if (!(rendered instanceof BitmapDrawable)) return source;
        Bitmap image = ((BitmapDrawable) rendered).getBitmap();
        Bitmap result = Bitmap.createBitmap(512, 288, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(outline ? 0xffa8b4cc : 0xff303b4c);
        paint.setStyle(outline ? Paint.Style.STROKE : Paint.Style.FILL); paint.setStrokeWidth(3);
        canvas.drawRoundRect(5, 5, 507, 283, 16, 16, paint);
        paint.setStyle(Paint.Style.FILL);
        if (!outline) {
            ColorMatrix colors = new ColorMatrix();
            colors.setSaturation(0.45f);
            paint.setColorFilter(new ColorMatrixColorFilter(colors));
        }
        canvas.drawBitmap(image, null, new Rect(24, 14, 488, 274), paint);
        image.recycle();
        return new BitmapDrawable(context.getResources(), result);
    }
}
