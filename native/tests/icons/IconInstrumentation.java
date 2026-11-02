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
        try {
            String first = "proof.one/.Main", second = "proof.two/.Main";
            IconSelectionStore.save(context, first, "catalog:jbl");
            IconSelectionStore.save(context, second, "catalog:pc");
            IconSelectionStore.save(context, first, IconSelectionStore.FACTORY);
            check("catalog:pc".equals(IconSelectionStore.choice(context, second)), "Factory reset must not change another tile.");
            ColorDrawable factory = new ColorDrawable(Color.RED), theme = new ColorDrawable(Color.BLUE);
            check(IconSelectionStore.resolve(context, first, factory, theme) == factory, "Factory choice must bypass theme.");
            IconSelectionStore.save(context, first, IconSelectionStore.THEME);
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
            IconSelectionStore.save(context, first, IconSelectionStore.THEME);

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
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
            onMain(new Runnable() { @Override public void run() { check(tiles.get(1).isFocused(), "D-pad must move to the next curated icon."); }});
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            waitForIdleSync();
            check("catalog:sonos".equals(IconSelectionStore.choice(context, key)), "Selecting an icon must persist for the actual app component.");
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
            SharedPreferences.Editor restore=prefs.edit().clear();
            for(Map.Entry<String,?> entry:original.entrySet()) restore.putString(entry.getKey(),(String)entry.getValue());
            result.putBoolean("preferences_restored",restore.commit());
            if(imported!=null) IconSelectionStore.discardPreparedImage(context,imported);
        }
        finish(0,result);
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
