package com.jeremykenedy.firetv.ui.nativeapp.appearance;

import android.content.Context;

public final class AppearanceTest {
    public static void main(String[] args) {
        Context context=new Context();
        check(AppearancePreferences.load(context).base.equals("off"),"Default preserves wallpaper");
        AppearancePreferences.string(context,"wallpaper_image_uri","content://owner/image");
        AppearancePreferences.string(context,"wallpaper_video_uri","content://owner/video");
        AppearancePreferences.number(context,"overlay_opacity",17);
        AppearancePreferences.flag(context,"base_frozen",true);
        AppearancePreferences.string(context,"overlay","snowflakes");
        AppearanceOptions selected=AppearancePreferences.load(context);
        check(selected.base.equals("off") && selected.overlayAnimated(),"Overlay runs with base off");
        check(selected.overlayOpacity==17 && selected.baseFrozen,"Explicit choices survive changing layer");
        check(selected.imageUri.equals("content://owner/image") && selected.videoUri.equals("content://owner/video"),"Both saved media references survive");
        AppearancePreferences.flag(context,"overlay_frozen",true);
        check(!AppearancePreferences.load(context).overlayAnimated(),"Still overlay stops callbacks");
        context.prefs.values.put("base_speed","invalid");
        check(AppearancePreferences.load(context).baseSpeed==50 && context.prefs.values.get("base_speed").equals("invalid"),"Malformed storage uses fallback without destroying raw value");
        context.prefs.values.put("overlay_opacity",101);
        check(AppearancePreferences.load(context).overlayOpacity==50,"Out of range storage uses default");
        AppearancePreferences.number(context,"overlay_intensity",0);
        check(!AppearancePreferences.load(context).overlayAnimated(),"Invisible effect needs no callbacks");
        AppearancePreferences.string(context,"base_palette","diwali");
        AppearancePreferences.string(context,"overlay_palette","hanukkah");
        check(AppearancePreferences.load(context).basePalette.equals("diwali")&&AppearancePreferences.load(context).overlayPalette.equals("hanukkah"),"Palette choices stay independent");
        check(PaletteRecipes.ALL.length==41&&PaletteRecipes.find("diwali")!=null&&PaletteRecipes.find("")==null,"All retained recipes have explicit default handling");
        AppearancePreferences.validateSnapshot(java.util.Collections.<String,Object>singletonMap("base_palette","diwali"));
        try { AppearancePreferences.validateSnapshot(java.util.Collections.<String,Object>singletonMap("overlay_opacity",101)); throw new AssertionError("Malformed setup rejected"); } catch(IllegalArgumentException expected) {}
        context.prefs.fail=true;
        try { AppearancePreferences.string(context,"overlay","leaves"); throw new AssertionError("Failed write must report error"); }
        catch(IllegalStateException expected) {}
        check(context.prefs.values.get("overlay").equals("snowflakes"),"Failed write rolls back changed key");
        check(context.prefs.values.get("wallpaper_image_uri").equals("content://owner/image"),"Failed write does not clear media");
        System.out.println("Appearance storage and independent layer checks passed");
    }
    private static void check(boolean result,String message) { if(!result) throw new AssertionError(message); }
}
