package com.jeremykenedy.firetv.ui.nativeapp.icons;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import com.jeremykenedy.firetv.ui.nativeapp.focus.FocusTileView;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class IconInstrumentation extends Instrumentation {
    private Activity home;
    private Activity picker;
    private final List<FocusTileView> tiles = new ArrayList<>();
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        Context context = getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences("native_icons", Context.MODE_PRIVATE);
        Map<String, ?> original = prefs.getAll();
        String imported = null;
        SharedPreferences packPreferences = context.getSharedPreferences("native_icon_packs", 0);
        Map<String, ?> originalPacks = packPreferences.getAll();
        try {
            check(IconSelectionStore.isValidTileKey("input:vendor/.Input/HDMI1"), "Input artwork keys must remain valid independently of component syntax.");
            check(!IconSelectionStore.isValidTileKey("input:") && !IconSelectionStore.isValidTileKey("invalid"), "Empty input and invalid app keys must fail.");
            org.xmlpull.v1.XmlPullParser parser = android.util.Xml.newPullParser();
            parser.setInput(new java.io.StringReader("<resources><item component='ComponentInfo{proof.one/.Main}' drawable='custom_icon'/><item component='proof.two/.Main' drawable='../escape'/></resources>"));
            Map<String, String> mappings = IconPackStore.mappings(parser);
            check("custom_icon".equals(mappings.get("proof.one/proof.one.Main")) && mappings.size() == 1, "Icon pack mappings must match exact components and reject invalid resource names.");
            String first = "proof.one/.Main", second = "proof.two/.Main";
            IconSelectionStore.save(context, first, "catalog:jbl");
            IconSelectionStore.save(context, second, "catalog:pc");
            IconSelectionStore.save(context, first, "catalog:insignia");
            Bitmap insignia = ((BitmapDrawable) IconSelectionStore.resolve(context, first,
                    new ColorDrawable(Color.RED), null)).getBitmap();
            check(insignia.getWidth() <= 512 && insignia.getHeight() <= 512,
                    "Insignia's reviewed replacement must be selectable as bounded artwork.");
            IconSelectionStore.save(context, first, IconSelectionStore.FACTORY);
            check("catalog:pc".equals(IconSelectionStore.choice(context, second)), "Factory reset must not change another tile.");
            ColorDrawable factory = new ColorDrawable(Color.RED), theme = new ColorDrawable(Color.BLUE);
            check(IconPackStore.select(context, "proof.icons.pack"), "Advertised installed fixture icon pack must be discoverable.");
            android.graphics.drawable.Drawable packed = IconPackStore.resolve(context, first, factory);
            check(packed instanceof BitmapDrawable && ((BitmapDrawable) packed).getBitmap().getWidth() <= 512,
                    "Installed pack component artwork must render at a bounded size.");
            check(IconPackStore.resolve(context, second, factory) == factory, "Unmapped components must retain factory artwork.");
            checkMutedArtwork(context);
            result.putBoolean("muted_artwork_pixels_verified", true);
            check(IconPackStore.select(context, IconPackStore.CLEAN), "Built in clean style must be selectable.");
            check(!IconPackStore.select(context, "uninstalled.pack"), "Unavailable icon packs must be rejected.");
            check(IconPackStore.CLEAN.equals(IconPackStore.selected(context)), "Rejected pack must preserve previous selection.");
            check(IconSelectionStore.resolve(context, first, factory, theme) == factory, "Factory choice must bypass theme.");
            IconSelectionStore.save(context, first, IconSelectionStore.THEME);
            check(IconSelectionStore.resolve(context, first, factory, null) instanceof BitmapDrawable, "Theme reset must apply selected built in icon style.");
            check(IconSelectionStore.resolve(context, first, factory, theme) == theme, "Theme reset must follow the global icon choice.");
            try {
                IconSelectionStore.save(context, first, "catalog:unlisted-image");
                throw new AssertionError("Unknown catalog images must be rejected.");
            } catch (IOException expected) {}
            check(IconSelectionStore.THEME.equals(IconSelectionStore.choice(context, first)), "Rejected choice must preserve selection.");
            try {
                IconSelectionStore.prepareImage(context, new ByteArrayInputStream(new byte[] {1,2,3}));
                throw new AssertionError("Invalid image must fail.");
            } catch (IOException expected) {}
            Bitmap source = Bitmap.createBitmap(1600, 800, Bitmap.Config.ARGB_8888);
            source.eraseColor(Color.GREEN);
            ByteArrayOutputStream image = new ByteArrayOutputStream();
            source.compress(Bitmap.CompressFormat.PNG, 100, image); source.recycle();
            imported = IconSelectionStore.prepareImage(context, new ByteArrayInputStream(image.toByteArray()));
            IconSelectionStore.save(context, first, imported);
            Bitmap bitmap = ((BitmapDrawable)IconSelectionStore.resolve(context, first, factory, theme)).getBitmap();
            check(bitmap.getWidth() <= 512 && bitmap.getWidth() == bitmap.getHeight() * 2, "Imported image must be bounded and keep its aspect ratio.");
            java.io.File selectedImage = new java.io.File(new java.io.File(context.getFilesDir(), "icons"), imported.substring(6));
            check(selectedImage.delete(), "Fixture selected image must be removable.");
            check(IconSelectionStore.resolve(context, first, factory, theme) == factory
                    && imported.equals(IconSelectionStore.choice(context, first)),
                    "A missing selected image must show factory artwork and retain its saved choice.");
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(selectedImage)) {
                output.write(new byte[] {1, 2, 3});
            }
            check(IconSelectionStore.resolve(context, first, factory, theme) == factory
                    && imported.equals(IconSelectionStore.choice(context, first)),
                    "A corrupt selected image must show factory artwork and retain its saved choice.");
            IconSelectionStore.save(context, first, IconSelectionStore.THEME);

            IconPackStore.select(context, IconPackStore.ORIGINAL);
            home = startActivitySync(new Intent().setClassName(context, "com.jeremykenedy.firetv.ui.nativeapp.MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            for (int attempt=0; attempt<50; attempt++) {
                SystemClock.sleep(100);
                onMain(new Runnable() { @Override public void run() { tiles.clear(); collect(home.getWindow().getDecorView()); }});
                if (!tiles.isEmpty()) break;
            }
            check(!tiles.isEmpty(), "An installed TV fixture app is required.");
            final FocusTileView appTile = tiles.get(0);
            final String key = String.valueOf(appTile.getTag());
            check(!IconSelectionStore.choice(context, key).startsWith("image:"), "Use a fresh fixture tile for non-destructive proof.");
            Instrumentation.ActivityMonitor monitor = addMonitor(IconPickerActivity.class.getName(), null, false);
            onMain(new Runnable() { @Override public void run() { appTile.requestFocus(); appTile.performLongClick(); }});
            waitForIdleSync();
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            picker = monitor.waitForActivityWithTimeout(3000);
            check(picker != null, "Main long-press Change icon must open the owned picker.");
            waitForIdleSync();
            onMain(new Runnable() { @Override public void run() { tiles.clear(); collect(picker.getWindow().getDecorView()); }});
            check(tiles.size() == IconCatalog.ALL.size(), "Picker must contain only reviewed catalog tiles.");
            for (FocusTileView tile : tiles) check(IconCatalog.find(String.valueOf(tile.getTag())) != null, "No device image may enter the curated catalog.");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);
            onMain(new Runnable() { @Override public void run() {
                View focused = picker.getCurrentFocus();
                check(focused instanceof Button && "Use factory icon".contentEquals(((Button) focused).getText()),
                        "Factory reset must be reachable above the first curated icon.");
            }});
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            onMain(new Runnable() { @Override public void run() { check(tiles.get(0).isFocused(), "D-pad down must return to the catalog."); }});
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
            onMain(new Runnable() { @Override public void run() { check(tiles.get(1).isFocused(), "D-pad must move to the next curated icon."); }});
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            onMain(new Runnable() { @Override public void run() { check(tiles.get(5).isFocused(), "D-pad down must move to next curated row."); }});
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            onMain(new Runnable() { @Override public void run() { check(tiles.get(8).isFocused(), "D-pad down must reach the last audio row."); }});
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            onMain(new Runnable() { @Override public void run() { check(tiles.get(9).isFocused(), "D-pad down must cross category headings."); }});
            onMain(new Runnable() { @Override public void run() { tiles.get(1).requestFocus(); }});
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            waitForIdleSync();
            check(("catalog:" + IconCatalog.ALL.get(1).id).equals(IconSelectionStore.choice(context, key)), "Selecting an icon must persist for the actual app component.");
            final Bitmap expected = ((BitmapDrawable)IconSelectionStore.resolve(context,key,factory,theme)).getBitmap();
            final boolean[] rendered = {false};
            for (int attempt=0; attempt<40 && !rendered[0]; attempt++) {
                SystemClock.sleep(100);
                onMain(new Runnable() { @Override public void run() {
                    tiles.clear(); collect(home.getWindow().getDecorView());
                    for(FocusTileView tile:tiles) if(key.equals(tile.getTag())) {
                        ImageView icon = imageView(tile);
                        rendered[0] = icon.getDrawable() instanceof BitmapDrawable
                                && expected.sameAs(((BitmapDrawable)icon.getDrawable()).getBitmap());
                    }
                }});
            }
            check(rendered[0], "Returning Home must visibly use the selected artwork.");
            IconSelectionStore.save(context,key,IconSelectionStore.FACTORY);
            check(IconSelectionStore.resolve(context,key,factory,theme)==factory,"Actual app factory restore must work.");
            IconSelectionStore.save(context,key,IconSelectionStore.THEME);
            check(IconSelectionStore.resolve(context,key,factory,theme)==theme,"Actual app theme restore must work.");
            result.putString("status","PASS");
            result.putInt("curated_icons",IconCatalog.ALL.size());
            result.putBoolean("main_longpress_picker_selection_rendered",true);
            result.putBoolean("factory_theme_reset_isolated",true);
            result.putBoolean("import_bounded_proportional",true);
        } catch(Throwable error) {
            result.putString("status","FAIL"); result.putString("reason",error.toString());
        } finally {
            SharedPreferences.Editor restorePacks = packPreferences.edit().clear();
            for (Map.Entry<String, ?> entry : originalPacks.entrySet()) restorePacks.putString(entry.getKey(), (String) entry.getValue());
            result.putBoolean("icon_pack_preferences_restored", restorePacks.commit());
            SharedPreferences.Editor restore=prefs.edit().clear();
            for(Map.Entry<String,?> entry:original.entrySet()) restore.putString(entry.getKey(),(String)entry.getValue());
            result.putBoolean("preferences_restored",restore.commit());
            if(imported!=null) IconSelectionStore.discardPreparedImage(context,imported);
        }
        finish(0,result);
    }

    private static void checkMutedArtwork(Context context) {
        ColorDrawable red = new ColorDrawable(Color.RED);
        red.setBounds(3, 5, 83, 45);
        check(IconPackStore.select(context, IconPackStore.CLEAN), "Select clean style for rendered color comparison.");
        Bitmap clean = ((BitmapDrawable) IconPackStore.resolve(context, "proof.color/.Main", red)).getBitmap();
        check(clean.getPixel(256, 144) == Color.RED, "Clean line must preserve original artwork color.");
        check(IconPackStore.select(context, IconPackStore.MUTED), "Select muted style for rendered color comparison.");
        Bitmap muted = ((BitmapDrawable) IconPackStore.resolve(context, "proof.color/.Main", red)).getBitmap();
        int pixel = muted.getPixel(256, 144);
        check(Math.abs(Color.red(pixel) - 145) <= 2 && Math.abs(Color.green(pixel) - 30) <= 2
                && Math.abs(Color.blue(pixel) - 30) <= 2 && Color.alpha(pixel) == 255,
                "Muted solid must render subdued artwork, not only add a background plate.");
        Bitmap gray = ((BitmapDrawable) IconPackStore.resolve(context, "proof.gray/.Main", new ColorDrawable(0xff808080))).getBitmap();
        check(gray.getPixel(256, 144) == 0xff808080, "Muted artwork must preserve neutral gray brightness.");
        check(red.getColor() == Color.RED && red.getBounds().equals(new android.graphics.Rect(3, 5, 83, 45)),
                "Style rendering must preserve the source drawable and its bounds.");
        clean.recycle(); muted.recycle(); gray.recycle();
    }

    private void onMain(final Runnable action) {
        final Throwable[] failure={null};
        runOnMainSync(new Runnable(){@Override public void run(){try{action.run();}catch(Throwable error){failure[0]=error;}}});
        if(failure[0]!=null) throw new AssertionError(failure[0]);
    }
    private void collect(View view) {
        if(view instanceof FocusTileView && view.isFocusable()) tiles.add((FocusTileView)view);
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) collect(((ViewGroup)view).getChildAt(i));
    }
    private ImageView imageView(View view) {
        if(view instanceof ImageView)return(ImageView)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            ImageView found=imageView(((ViewGroup)view).getChildAt(i));if(found!=null)return found;
        }
        return null;
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
