package com.jeremykenedy.firetv.ui.nativeapp.appearance;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import com.jeremykenedy.firetv.ui.nativeapp.holidays.HolidayScenes;
import com.jeremykenedy.firetv.ui.nativeapp.holidays.HolidaySelection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BackgroundSettingsActivity extends Activity {
    private boolean startupBlocked;
    private static final int IMAGE=7101, VIDEO=7102;
    private BackgroundLayer layer;
    private AppearanceOptions options;
    private TextView mediaStatus;
    private final Map<String,Button> rows=new LinkedHashMap<>();
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; } options=AppearancePreferences.load(this);
        LinearLayout root=Pages.page(this); root.setPadding(0,0,0,0);
        LinearLayout columns=new LinearLayout(this); root.addView(columns,new LinearLayout.LayoutParams(-1,-1));
        ScrollView scroll=new ScrollView(this); columns.addView(scroll,new LinearLayout.LayoutParams(0,-1,3));
        LinearLayout controls=new LinearLayout(this); controls.setOrientation(1); controls.setPadding(dp(32),dp(24),dp(24),dp(24)); controls.setBackgroundColor(0xdc101523); scroll.addView(controls);
        controls.addView(Pages.text(this,"Backgrounds and overlays",28));
        controls.addView(Pages.text(this,"Choose a background and a separate transparent effect. Your wallpaper stays saved.",17));
        section(controls,"Wallpaper");
        controls.addView(Pages.text(this,"Set Background to Off to show your wallpaper. Changing its source keeps your background and overlay choices.",16));
        row(controls,"wallpaper_mode","Wallpaper source",new View.OnClickListener() {
            @Override public void onClick(View view) { chooseWallpaper(); }
        });
        mediaStatus=Pages.text(this,"",16); mediaStatus.setTag("appearance:media-status"); controls.addView(mediaStatus);
        row(controls,"retry_media","Retry saved wallpaper",new View.OnClickListener() {
            @Override public void onClick(View view) { layer.retryMedia(); }
        });
        row(controls,"image","Choose image",new View.OnClickListener() { @Override public void onClick(View view) { pick(IMAGE,"image/*"); } });
        row(controls,"video","Choose silent video",new View.OnClickListener() { @Override public void onClick(View view) { pick(VIDEO,"video/*"); } });
        row(controls,"wallpaper_video_frozen","",new View.OnClickListener() {
            @Override public void onClick(View view) {
                flag("wallpaper_video_frozen",!AppearancePreferences.videoFrozen(BackgroundSettingsActivity.this));
            }
        });
        section(controls,"Motion");
        row(controls,"reduced_motion","",new View.OnClickListener() { @Override public void onClick(View view) {
            flag("reduced_motion",!AppearancePreferences.reducedMotion(BackgroundSettingsActivity.this));
        } });
        controls.addView(Pages.text(this,"Reduced motion holds backgrounds, holiday scenes, overlays and personal video still. Your individual motion choices stay saved.",16));
        section(controls,"Holiday scenery");
        row(controls,"scene","",new View.OnClickListener() { @Override public void onClick(View view) { chooseScene(); } });
        row(controls,"scene_overlay","Use this scene's suggested overlay",new View.OnClickListener() { @Override public void onClick(View view) {
            try { HolidaySelection.apply(AppearancePreferences.file(BackgroundSettingsActivity.this),options.scene,HolidaySelection.Mode.OVERLAY); refresh(); }
            catch(IllegalStateException failure) { error(failure); }
        } });
        controls.addView(Pages.text(this,"Scene selection changes only the scenery. Choose a transparent overlay separately, or use the scene's suggestion. Off returns to your saved background and wallpaper.",16));
        section(controls,"Background");
        row(controls,"base","",new View.OnClickListener() { @Override public void onClick(View view) { choose("Background","base",AppearanceOptions.BASES,AppearanceOptions.BASE_LABELS,options.base); } });
        row(controls,"legacy_gradient","",new View.OnClickListener() { @Override public void onClick(View view) {
            choose("Saved gradient", "legacy_gradient", LegacyGradients.IDS, LegacyGradients.LABELS, options.legacyGradient);
        } });
        row(controls,"base_frozen","",new View.OnClickListener() { @Override public void onClick(View view) { flag("base_frozen",!options.baseFrozen); } });
        number(controls,"base_speed","Background speed"); number(controls,"base_intensity","Background intensity"); number(controls,"base_brightness","Background brightness");
        palette(controls,"base_palette","Background palette recipe");
        section(controls,"Transparent overlay");
        row(controls,"overlay","",new View.OnClickListener() { @Override public void onClick(View view) { choose("Transparent overlay","overlay",AppearanceOptions.OVERLAYS,AppearanceOptions.OVERLAY_LABELS,options.overlay); } });
        row(controls,"overlay_frozen","",new View.OnClickListener() { @Override public void onClick(View view) { flag("overlay_frozen",!options.overlayFrozen); } });
        number(controls,"overlay_speed","Overlay speed"); number(controls,"overlay_intensity","Overlay intensity"); number(controls,"overlay_opacity","Overlay opacity");
        palette(controls,"overlay_palette","Overlay palette recipe");
        row(controls,"recommended","Use recommended overlay values",new View.OnClickListener() { @Override public void onClick(View view) {
            try { AppearancePreferences.update(BackgroundSettingsActivity.this,new String[]{"overlay_speed","overlay_intensity","overlay_opacity"},new Object[]{50,70,80}); refresh(); } catch(IllegalStateException failure) { error(failure); }
        } });
        LinearLayout sample=new LinearLayout(this); sample.setOrientation(1); sample.setPadding(dp(24),dp(30),dp(32),dp(24));
        columns.addView(sample,new LinearLayout.LayoutParams(0,-1,2));
        TextView preview=Pages.text(this,"Live preview",24); preview.setBackgroundColor(0x99101523); sample.addView(preview);
        TextView note=Pages.text(this,"The selected wallpaper, background, scene and overlay appear here in the same order as Home.",17); note.setBackgroundColor(0x99101523); sample.addView(note);
        Button full=Pages.button(this,"Full screen preview",new View.OnClickListener() { @Override public void onClick(View view) { startActivity(new Intent(BackgroundSettingsActivity.this,BackgroundPreviewActivity.class)); } }); full.setTag("appearance:preview"); sample.addView(full);
        layer=BackgroundLayer.attach(this,root); root.setBackgroundColor(0);
        layer.setMediaListener(new BackgroundLayer.MediaListener() {
            @Override public void changed(BackgroundLayer.MediaState state,String message) {
                mediaStatus.setText(message);
                rows.get("retry_media").setEnabled(state==BackgroundLayer.MediaState.UNAVAILABLE);
            }
        });
        refresh(); rows.get("wallpaper_mode").requestFocus();
    }
    private void chooseScene() {
        String[] values=new String[HolidayScenes.ALL.length+1], labels=new String[values.length];
        values[0]=""; labels[0]="Off: use saved background and wallpaper";
        for(int i=0;i<HolidayScenes.ALL.length;i++) { values[i+1]=HolidayScenes.ALL[i].id; labels[i+1]=HolidayScenes.ALL[i].name; }
        choose("Holiday scenery","scene",values,labels,options.scene);
    }

    private void chooseWallpaper() {
        final String[] values={"system","image","video"};
        final String[] labels={"System wallpaper","Saved image","Saved silent video"};
        String current=AppearancePreferences.wallpaperMode(this);
        int selected=0;
        for(int i=0;i<values.length;i++) if(values[i].equals(current)) selected=i;
        new AlertDialog.Builder(this).setTitle("Wallpaper source")
                .setSingleChoiceItems(labels,selected,new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog,int index) {
                        if((index==1&&options.imageUri.isEmpty()) || (index==2&&options.videoUri.isEmpty())) {
                            new AlertDialog.Builder(BackgroundSettingsActivity.this)
                                    .setMessage(index==1?"Choose an image first. Your current wallpaper is kept.":"Choose a video first. Your current wallpaper is kept.")
                                    .setPositiveButton("OK",null).show();
                            return;
                        }
                        try {
                            AppearancePreferences.selectWallpaper(BackgroundSettingsActivity.this,values[index]);
                            refresh(); dialog.dismiss();
                        } catch(IllegalStateException failure) { error(failure); }
                    }
                }).setNegativeButton("Cancel",null).show();
    }

    private void palette(LinearLayout parent, final String key, final String title) {
        row(parent,key,title,new View.OnClickListener() { @Override public void onClick(View view) {
            String[] values=new String[PaletteRecipes.ALL.length+1], labels=new String[values.length];
            values[0]=""; labels[0]="Default colors";
            for(int i=0;i<PaletteRecipes.ALL.length;i++) { values[i+1]=PaletteRecipes.ALL[i].id; labels[i+1]=PaletteRecipes.ALL[i].name+" ("+PaletteRecipes.ALL[i].family+")"; }
            choose(title,key,values,labels,key.equals("base_palette")?options.basePalette:options.overlayPalette);
        } });
    }
    private void section(LinearLayout parent,String title) { TextView text=Pages.text(this,title,21); text.setPadding(0,dp(20),0,dp(6)); parent.addView(text); }
    private void row(LinearLayout parent,String key,String label,View.OnClickListener listener) { Button row=Pages.button(this,label,listener); row.setTag("appearance:"+key); row.setMinHeight(dp(48)); rows.put(key,row); parent.addView(row,new LinearLayout.LayoutParams(-1,-2)); }
    private void number(LinearLayout parent,String key,String label) { row(parent,key,label,new View.OnClickListener() { @Override public void onClick(View view) {
        final android.widget.SeekBar bar=new android.widget.SeekBar(BackgroundSettingsActivity.this); bar.setMax(100); bar.setKeyProgressIncrement(1); bar.setProgress(value(key)); bar.setPadding(dp(24),dp(16),dp(24),dp(16)); bar.setContentDescription(label);
        final TextView caption=Pages.text(BackgroundSettingsActivity.this,label+": "+value(key)+"%",20);
        LinearLayout body=new LinearLayout(BackgroundSettingsActivity.this); body.setOrientation(1); body.setPadding(dp(16),dp(12),dp(16),dp(12)); body.addView(caption); body.addView(bar,new LinearLayout.LayoutParams(-1,dp(64)));
        bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(android.widget.SeekBar seek) {} public void onStopTrackingTouch(android.widget.SeekBar seek) {}
            public void onProgressChanged(android.widget.SeekBar seek,int value,boolean user) { caption.setText(label+": "+value+"%"); }
        });
        AlertDialog dialog=new AlertDialog.Builder(BackgroundSettingsActivity.this).setTitle(label).setView(body).setNegativeButton("Cancel",null).setPositiveButton("Save",new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface owner,int which) {
            try { AppearancePreferences.number(BackgroundSettingsActivity.this,key,bar.getProgress()); refresh(); } catch(IllegalStateException failure) { error(failure); }
        } }).create(); dialog.setOnShowListener(new DialogInterface.OnShowListener() { @Override public void onShow(DialogInterface owner) { bar.requestFocus(); } }); dialog.show();
    } }); }
    private int value(String key) { if(key.equals("base_speed")) return options.baseSpeed; if(key.equals("base_intensity")) return options.baseIntensity; if(key.equals("base_brightness")) return options.brightness; if(key.equals("overlay_speed")) return options.overlaySpeed; if(key.equals("overlay_intensity")) return options.overlayIntensity; return options.overlayOpacity; }
    private void choose(String title,String key,String[] values,String[] labels,String selected) {
        int checked=-1; for(int i=0;i<values.length;i++) if(values[i].equals(selected)) checked=i;
        new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels,checked,new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog,int index) {
            try {
                if(key.equals("base")) { AppearancePreferences.update(BackgroundSettingsActivity.this,new String[]{"base","scene"},new Object[]{values[index],""}); refresh(); }
                else edit(key,values[index]);
                dialog.dismiss();
            } catch(IllegalStateException failure) { error(failure); }
        } }).setNegativeButton("Cancel",null).show();
    }
    private void edit(String key,String value) { try { AppearancePreferences.string(this,key,value); refresh(); } catch(IllegalStateException failure) { error(failure); } }
    private void flag(String key,boolean value) { try { AppearancePreferences.flag(this,key,value); refresh(); } catch(IllegalStateException failure) { error(failure); } }
    private void refresh() {
        options=AppearancePreferences.load(this);
        boolean reduced=AppearancePreferences.reducedMotion(this);
        rows.get("reduced_motion").setText("Reduced motion: "+(reduced?"On":"Off"));
        HolidayScenes.Scene selectedScene=HolidayScenes.find(options.scene);
        rows.get("scene").setText("Holiday scenery: "+(selectedScene==null?"Off":selectedScene.name));
        rows.get("scene_overlay").setEnabled(selectedScene!=null);
        String wallpaperMode=AppearancePreferences.wallpaperMode(this);
        rows.get("wallpaper_mode").setText("Wallpaper source: "+(wallpaperMode.equals("image")?"Saved image":wallpaperMode.equals("video")?"Saved silent video":"System wallpaper"));
        rows.get("wallpaper_video_frozen").setText("Video motion: "+(AppearancePreferences.videoFrozen(this)?"Still":"Playing"));
        rows.get("wallpaper_video_frozen").setEnabled(wallpaperMode.equals("video"));
        rows.get("base").setText("Background: "+(selectedScene!=null?selectedScene.name:label(AppearanceOptions.BASES,AppearanceOptions.BASE_LABELS,options.base)));
        int gradient = LegacyGradients.index(options.legacyGradient);
        rows.get("legacy_gradient").setText("Saved gradient: "+(gradient < 0 ? "Not selected" : LegacyGradients.LABELS[gradient]));
        rows.get("base_frozen").setText("Background motion: "+(options.baseFrozen?"Still":"Animated"));
        rows.get("overlay").setText("Overlay: "+label(AppearanceOptions.OVERLAYS,AppearanceOptions.OVERLAY_LABELS,options.overlay));
        rows.get("overlay_frozen").setText("Overlay motion: "+(options.overlayFrozen?"Still":"Animated"));
        String[] numbers={"base_speed","base_intensity","base_brightness","overlay_speed","overlay_intensity","overlay_opacity"};
        String[] labels={"Background speed","Background intensity","Background brightness","Overlay speed","Overlay intensity","Overlay opacity"};
        for(int i=0;i<numbers.length;i++) rows.get(numbers[i]).setText(labels[i]+": "+value(numbers[i])+"%");
        rows.get("base_frozen").setEnabled(!options.scene.isEmpty() || (!options.base.equals("off")&&!options.base.startsWith("still_")));
        rows.get("base_speed").setEnabled(!reduced && (options.baseAnimated() || (!options.scene.isEmpty()&&!options.baseFrozen))); rows.get("base_intensity").setEnabled(!options.base.equals("off")&&!options.base.startsWith("still_")&&options.scene.isEmpty());
        rows.get("base_brightness").setEnabled(!options.base.equals("off")&&!options.base.equals("still_legacy_gradient")&&options.scene.isEmpty());
        rows.get("overlay_frozen").setEnabled(!options.overlay.equals("off")); rows.get("overlay_speed").setEnabled(!reduced && options.overlayAnimated());
        rows.get("overlay_intensity").setEnabled(!options.overlay.equals("off")); rows.get("overlay_opacity").setEnabled(!options.overlay.equals("off"));
        PaletteRecipes.Theme baseRecipe=PaletteRecipes.find(options.basePalette), overlayRecipe=PaletteRecipes.find(options.overlayPalette);
        rows.get("base_palette").setEnabled(!options.base.equals("off")&&!options.base.equals("still_legacy_gradient")&&options.scene.isEmpty());
        rows.get("overlay_palette").setEnabled(!options.overlay.equals("off"));
        rows.get("base_palette").setText("Background palette recipe: "+(baseRecipe==null?"Default colors":baseRecipe.name));
        rows.get("overlay_palette").setText("Overlay palette recipe: "+(overlayRecipe==null?"Default colors":overlayRecipe.name));
        if(layer!=null) layer.refresh();
    }
    private String label(String[] values,String[] labels,String value) { for(int i=0;i<values.length;i++) if(values[i].equals(value)) return labels[i]; return labels[0]; }
    private void pick(int request,String type) { Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(type).addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION); try { startActivityForResult(intent,request); } catch(android.content.ActivityNotFoundException unavailable) { new AlertDialog.Builder(this).setMessage("This device has no document picker. Your current wallpaper is kept.").setPositiveButton("OK",null).show(); } }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        if (startupBlocked) { super.onActivityResult(request, result, data); return; } super.onActivityResult(request,result,data); if(result!=RESULT_OK||data==null||data.getData()==null||(request!=IMAGE&&request!=VIDEO)) return;
        Uri uri=data.getData(); if(!"content".equals(uri.getScheme())) return;
        try {
            getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
            String key=request==IMAGE?"wallpaper_image_uri":"wallpaper_video_uri";
            AppearancePreferences.update(this,new String[]{key,"wallpaper_mode"},new Object[]{uri.toString(),request==IMAGE?"image":"video"}); refresh();
        } catch(SecurityException | IllegalStateException failure) { error(failure); }
    }
    private void error(Exception failure) { new AlertDialog.Builder(this).setTitle("Settings not saved").setMessage(failure.getMessage()).setPositiveButton("OK",null).show(); }
    private int dp(int value) { return Pages.dp(this,value); }
    @Override protected void onResume() {
        if (startupBlocked) { super.onResume(); return; } super.onResume(); if(layer!=null) { refresh(); layer.setResumed(true); } }
    @Override protected void onPause() {
        if (startupBlocked) { super.onPause(); return; } if(layer!=null) layer.setResumed(false); super.onPause(); }
}
