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
        for(String style:AppearanceOptions.BASES) {
            if(!style.equals("off")&&!style.startsWith("still_")) {
                check(AppearanceOptions.known(AppearanceOptions.OVERLAYS,style).equals(style),"Animated style selectable as transparent overlay: "+style);
            }
        }
        check(AppearancePreferences.wallpaperMode(context).equals("system")&&!AppearancePreferences.videoFrozen(context),"Media defaults remain unchanged");
        AppearancePreferences.selectWallpaper(context,"video");
        AppearancePreferences.flag(context,"wallpaper_video_frozen",true);
        AppearancePreferences.selectWallpaper(context,"image");
        check(AppearancePreferences.wallpaperMode(context).equals("image")&&AppearancePreferences.videoFrozen(context),"Source switching preserves independent still-video choice");
        check(AppearancePreferences.load(context).baseFrozen&&AppearancePreferences.load(context).overlayOpacity==17,"Source switching leaves chosen effects unchanged");
        check(AppearancePreferences.load(context).videoUri.equals("content://owner/video"),"Saved video survives image selection");
        context.prefs.values.put("wallpaper_mode",17);
        check(AppearancePreferences.wallpaperMode(context).equals("system")&&context.prefs.values.get("wallpaper_mode").equals(17),"Malformed source falls back without rewriting raw choice");
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
        AppearancePreferences.validateSnapshot(java.util.Collections.<String,Object>singletonMap("wallpaper_video_frozen",true));
        check(!AppearancePreferences.reducedMotion(context),"Reduced motion is opt-in");
        AppearancePreferences.flag(context,"reduced_motion",true);
        check(AppearancePreferences.reducedMotion(context),"Reduced motion persists independently");
        check(AppearancePreferences.load(context).baseFrozen&&AppearancePreferences.load(context).overlayFrozen,"Reduced motion preserves individual motion choices");
        AppearancePreferences.validateSnapshot(java.util.Collections.<String,Object>singletonMap("reduced_motion",true));
        try { AppearancePreferences.validateSnapshot(java.util.Collections.<String,Object>singletonMap("reduced_motion","true")); throw new AssertionError("Malformed reduced motion rejected"); } catch(IllegalArgumentException expected) {}
        AppearancePreferences.string(context,"legacy_gradient","electric_indigo");
        AppearancePreferences.string(context,"base","still_legacy_gradient");
        check(!AppearancePreferences.load(context).baseAnimated(),"Saved gradient is static");
        AppearancePreferences.string(context,"base","off");
        check(AppearancePreferences.load(context).legacyGradient.equals("electric_indigo"),"Changing mode retains saved gradient");
        AppearancePreferences.string(context,"legacy_gradient","cloud_nine");
        AppearancePreferences.string(context,"base","still_legacy_gradient");
        check(AppearancePreferences.load(context).legacyGradient.equals("cloud_nine"),"A different gradient can be selected while dormant");
        check(LegacyGradients.top(0)==0xff1a0f3e && LegacyGradients.bottom(0)==0xff5633a1
                && LegacyGradients.top(1)==0xffeceff1 && LegacyGradients.bottom(1)==0xff90a4ae,"Observed endpoint colors retained");
        try { AppearancePreferences.validateSnapshot(java.util.Collections.<String,Object>singletonMap("legacy_gradient","unknown")); throw new AssertionError("Unknown saved gradient accepted"); } catch(IllegalArgumentException expected) {}
        context.prefs.values.put("legacy_gradient","unknown");
        check(LegacyGradients.index(AppearancePreferences.load(context).legacyGradient)<0 && context.prefs.values.get("legacy_gradient").equals("unknown"),"Unknown stored value is preserved without guessing");
        context.prefs.fail=true;
        try { AppearancePreferences.string(context,"overlay","leaves"); throw new AssertionError("Failed write must report error"); }
        catch(IllegalStateException expected) {}
        check(context.prefs.values.get("overlay").equals("snowflakes"),"Failed write rolls back changed key");
        check(context.prefs.values.get("wallpaper_image_uri").equals("content://owner/image"),"Failed write does not clear media");
        System.out.println("Appearance storage and independent layer checks passed");
    }
    private static void check(boolean result,String message) { if(!result) throw new AssertionError(message); }
}
