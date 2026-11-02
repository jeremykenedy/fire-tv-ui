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
        getWindow().getDecorView().setSystemUiVisibility(5894);
        android.widget.FrameLayout root=new android.widget.FrameLayout(this);
        root.setBackgroundColor(0xff1c1037);
        setContentView(root);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        android.graphics.drawable.GradientDrawable panel=new android.graphics.drawable.GradientDrawable();
        panel.setColor(0xff383838); panel.setCornerRadius(dp(18));
        scroll.setBackground(panel); scroll.setClipToOutline(true);
        android.widget.FrameLayout.LayoutParams bounds=new android.widget.FrameLayout.LayoutParams(-1,-1);
        bounds.setMargins(dp(32),dp(24),dp(32),dp(24)); root.addView(scroll,bounds);
        LinearLayout controls=new LinearLayout(this); controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(24),dp(14),dp(24),dp(24)); scroll.addView(controls);
        TextView heading=Pages.text(this,"Backgrounds and overlays",28);
        heading.setTypeface(null,android.graphics.Typeface.BOLD);
        heading.setPadding(0,dp(6),0,dp(2)); controls.addView(heading);
        TextView description=Pages.text(this,"Keep your wallpaper and add a transparent overlay, or choose a full background. Each layer has its own motion and colors. The preview uses a neutral base.",18);
        description.setPadding(0,dp(6),0,dp(10)); controls.addView(description);
        layer=new BackgroundLayer(this);
        android.graphics.drawable.GradientDrawable previewBackground=new android.graphics.drawable.GradientDrawable();
        previewBackground.setColor(0xff202733); previewBackground.setCornerRadius(dp(8));
        layer.setBackground(previewBackground); layer.setClipToOutline(true);
        LinearLayout.LayoutParams previewBounds=new LinearLayout.LayoutParams(dp(320),dp(180));
        previewBounds.bottomMargin=dp(10); controls.addView(layer,previewBounds);
        Button full=editorButton("Full screen preview",new View.OnClickListener() { @Override public void onClick(View view) { startActivity(new Intent(BackgroundSettingsActivity.this,BackgroundPreviewActivity.class)); } });
        full.setTag("appearance:preview");
        row(controls,"holiday_theme","Holiday themes",new View.OnClickListener() { @Override public void onClick(View view) { chooseHolidayTheme(); } });
        row(controls,"base","",new View.OnClickListener() { @Override public void onClick(View view) { choose("Background","base",AppearanceOptions.BASES,AppearanceOptions.BASE_LABELS,options.base); } });
        section(controls,"Transparent overlay");
        controls.addView(Pages.text(this,"Off removes only the overlay. Your background, wallpaper and app layout remain as they are.",16));
        row(controls,"overlay","",new View.OnClickListener() { @Override public void onClick(View view) { choose("Transparent overlay","overlay",AppearanceOptions.OVERLAYS,AppearanceOptions.OVERLAY_LABELS,options.overlay); } });
        row(controls,"overlay_frozen","",new View.OnClickListener() { @Override public void onClick(View view) { flag("overlay_frozen",!options.overlayFrozen); } });
        number(controls,"overlay_speed","Overlay speed"); number(controls,"overlay_intensity","Overlay intensity"); number(controls,"overlay_opacity","Overlay opacity");
        palette(controls,"overlay_palette","Overlay palette recipe");
        row(controls,"recommended","Use recommended overlay values",new View.OnClickListener() { @Override public void onClick(View view) {
            try { AppearancePreferences.update(BackgroundSettingsActivity.this,new String[]{"overlay_speed","overlay_intensity","overlay_opacity"},new Object[]{50,70,80}); refresh(); } catch(IllegalStateException failure) { error(failure); }
        } });
        section(controls,"Background options");
        row(controls,"legacy_gradient","",new View.OnClickListener() { @Override public void onClick(View view) {
            choose("Saved gradient", "legacy_gradient", LegacyGradients.IDS, LegacyGradients.LABELS, options.legacyGradient);
        } });
        row(controls,"base_frozen","",new View.OnClickListener() { @Override public void onClick(View view) { flag("base_frozen",!options.baseFrozen); } });
        number(controls,"base_speed","Background speed"); number(controls,"base_intensity","Background intensity"); number(controls,"base_brightness","Background brightness");
        palette(controls,"base_palette","Background palette recipe");
        section(controls,"Holiday scenery");
        row(controls,"scene","",new View.OnClickListener() { @Override public void onClick(View view) { chooseScene(); } });
        row(controls,"scene_overlay","Use this scene's suggested overlay",new View.OnClickListener() { @Override public void onClick(View view) {
            try { synchronized(com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState.class) { com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction.assertWritable(BackgroundSettingsActivity.this); HolidaySelection.apply(AppearancePreferences.file(BackgroundSettingsActivity.this),options.scene,HolidaySelection.Mode.OVERLAY); } refresh(); }
            catch(IllegalStateException failure) { error(failure); }
        } });
        controls.addView(Pages.text(this,"Scene selection changes only the scenery. Choose a transparent overlay separately, or use the scene's suggestion. Off returns to your saved background and wallpaper.",16));
        section(controls,"Motion");
        row(controls,"reduced_motion","",new View.OnClickListener() { @Override public void onClick(View view) {
            flag("reduced_motion",!AppearancePreferences.reducedMotion(BackgroundSettingsActivity.this));
        } });
        controls.addView(Pages.text(this,"Reduced motion holds backgrounds, holiday scenes, overlays and personal video still. Your individual motion choices stay saved.",16));
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
        controls.addView(full);
        layer.setMediaListener(new BackgroundLayer.MediaListener() {
            @Override public void changed(BackgroundLayer.MediaState state,String message) {
                mediaStatus.setText(message);
                rows.get("retry_media").setEnabled(state==BackgroundLayer.MediaState.UNAVAILABLE);
            }
        });
        refresh(); rows.get("base").requestFocus();
    }

    private void chooseScene() {
        String[] values=new String[HolidayScenes.ALL.length+1], labels=new String[values.length];
        values[0]=""; labels[0]="Off: use saved background and wallpaper";
        for(int i=0;i<HolidayScenes.ALL.length;i++) { values[i+1]=HolidayScenes.ALL[i].id; labels[i+1]=HolidayScenes.ALL[i].name; }
        choose("Holiday scenery","scene",values,labels,options.scene);
    }

    private void chooseHolidayTheme() {
        String[] names=PaletteRecipes.names();
        String[] labels=java.util.Arrays.copyOf(names,names.length+1);
        labels[names.length]="My own celebration";
        new AlertDialog.Builder(this).setTitle("Holiday themes").setItems(labels, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog,int index) {
                if(index==PaletteRecipes.ALL.length) {
                    new AlertDialog.Builder(BackgroundSettingsActivity.this).setTitle("My own celebration")
                            .setMessage("Choose your background, overlay, colors and motion here. You can save them in Saved setups.")
                            .setPositiveButton("Choose appearance",null).show();
                    return;
                }
                final PaletteRecipes.Theme theme=PaletteRecipes.ALL[index];
                final boolean[] choices={true,true,false};
                new AlertDialog.Builder(BackgroundSettingsActivity.this).setTitle(theme.name)
                        .setMultiChoiceItems(new String[]{"Transparent overlay over my current background", "Animated (off for still)", "Use coordinated icon colors"},choices,
                                new DialogInterface.OnMultiChoiceClickListener() {
                                    @Override public void onClick(DialogInterface owner,int which,boolean checked) { choices[which]=checked; }
                                })
                        .setNegativeButton("Cancel",null).setPositiveButton("Apply",new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface owner,int which) {
                                try { HolidayThemeSettings.apply(BackgroundSettingsActivity.this,theme.id,choices[0],choices[1],choices[2]); refresh(); }
                                catch (java.io.IOException | RuntimeException failure) { error(failure); }
                            }
                        }).show();
            }
        }).setNegativeButton("Cancel",null).show();
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
    private Button editorButton(String label, View.OnClickListener listener) {
        Button button=Pages.menuButton(this,label,listener);
        button.setOnFocusChangeListener(null);
        button.setTextSize(18); button.setTypeface(null,android.graphics.Typeface.NORMAL);
        button.setPadding(dp(14),0,dp(14),0);
        LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(-1,dp(42)); bounds.bottomMargin=dp(6);
        button.setLayoutParams(bounds);
        com.jeremykenedy.firetv.ui.nativeapp.accessibility.AccessibilityStyle.apply(this,button);
        return button;
    }
    private void row(LinearLayout parent,String key,String label,View.OnClickListener listener) { Button row=editorButton(label,listener); row.setTag("appearance:"+key); rows.put(key,row); parent.addView(row); }
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
        if (!com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.ready(this)) { startupBlocked=com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this); return; }
        Uri uri=data.getData(); if(!"content".equals(uri.getScheme())) return;
        try {
            getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
            String key=request==IMAGE?"wallpaper_image_uri":"wallpaper_video_uri";
            AppearancePreferences.update(this,new String[]{key,"wallpaper_mode"},new Object[]{uri.toString(),request==IMAGE?"image":"video"}); refresh();
        } catch(SecurityException | IllegalStateException failure) { error(failure); }
    }
    private void error(Exception failure) {
        if (!com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.ready(this)) {
            if (layer != null) layer.setResumed(false);
            startupBlocked = com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this);
            return;
        }
        new AlertDialog.Builder(this).setTitle("Settings not saved").setMessage(failure.getMessage()).setPositiveButton("OK",null).show();
    }
    private int dp(int value) { return Pages.dp(this,value); }
    @Override protected void onResume() {
        if (startupBlocked) { super.onResume(); return; } super.onResume();
        if (!com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.ready(this)) {
            if(layer!=null) layer.setResumed(false);
            startupBlocked=com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this); return;
        }
        if(layer!=null) { refresh(); layer.setResumed(true); } }
    @Override protected void onPause() {
        if (startupBlocked) { super.onPause(); return; } if(layer!=null) layer.setResumed(false); super.onPause(); }
}
