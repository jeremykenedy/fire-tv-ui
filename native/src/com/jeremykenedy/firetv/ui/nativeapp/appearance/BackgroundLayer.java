package com.jeremykenedy.firetv.ui.nativeapp.appearance;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.VideoView;
import com.jeremykenedy.firetv.ui.nativeapp.holidays.HolidayScenes;
import com.jeremykenedy.firetv.ui.nativeapp.holidays.HolidaySceneView;
import java.util.concurrent.Future;

/** Shared composition for Home, settings and previews, ordered below interactive content. */
public final class BackgroundLayer extends FrameLayout {
    private final ImageView wallpaper;
    private final HolidaySceneView scene;
    private final VideoView video;
    private final AppearanceView base, overlay;
    private Future<?> imageRequest;
    private int generation;
    private boolean resumed;
    private String mediaKey="";
    public BackgroundLayer(Context context) {
        super(context); setTag("appearance:layers"); setBackgroundColor(0x00101523);
        video=new VideoView(context); addView(video,new LayoutParams(-1,-1)); video.setVisibility(GONE);
        video.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener() { @Override public void onPrepared(android.media.MediaPlayer player) { player.setLooping(true); player.setVolume(0,0); if(resumed && hasWindowFocus()) video.start(); } });
        video.setOnErrorListener(new android.media.MediaPlayer.OnErrorListener() { @Override public boolean onError(android.media.MediaPlayer player, int what, int extra) { video.setVisibility(GONE); return true; } });
        wallpaper=new ImageView(context); wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP); addView(wallpaper,new LayoutParams(-1,-1));
        base=new AppearanceView(context,false); base.setTag("appearance:base"); addView(base,new LayoutParams(-1,-1));
        scene=new HolidaySceneView(context); addView(scene,new LayoutParams(-1,-1));
        overlay=new AppearanceView(context,true); overlay.setTag("appearance:overlay"); addView(overlay,new LayoutParams(-1,-1));
    }
    public static BackgroundLayer attach(Activity activity, View content) {
        ViewGroup parent=(ViewGroup)content.getParent(); if(parent!=null) parent.removeView(content);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        activity.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        BackgroundLayer layer=new BackgroundLayer(activity);
        content.setBackgroundColor(0x66101523);
        layer.addView(content,new LayoutParams(-1,-1)); activity.setContentView(layer); return layer;
    }
    public void refresh() {
        final AppearanceOptions options=AppearancePreferences.load(getContext());
        base.setOptions(options); overlay.setOptions(options);
        String mode=AppearancePreferences.file(getContext()).getAll().get("wallpaper_mode") instanceof String
                ? (String)AppearancePreferences.file(getContext()).getAll().get("wallpaper_mode") : "system";
        String nextMedia=mode+"|"+options.imageUri+"|"+options.videoUri;
        if(!mediaKey.equals(nextMedia)) {
            mediaKey=nextMedia; final int request=++generation;
            if(imageRequest!=null) { imageRequest.cancel(true); imageRequest=null; }
            video.stopPlayback(); video.setVisibility(GONE); wallpaper.setImageDrawable(null);
            if(mode.equals("image") && !options.imageUri.isEmpty()) imageRequest=WallpaperImages.load(getContext(),options.imageUri,
                    new WallpaperImages.Callback() { @Override public void loaded(Bitmap bitmap) { if(request==generation) wallpaper.setImageBitmap(bitmap); } });
            if(mode.equals("video") && !options.videoUri.isEmpty()) {
                Uri uri=Uri.parse(options.videoUri);
                if(WallpaperAccess.supported(getContext(),uri)) { video.setVisibility(VISIBLE); video.setVideoURI(uri); }
            }
        }
        scene.setScene(HolidayScenes.find(options.scene),!options.baseFrozen,options.baseSpeed);
    }
    public void setResumed(boolean value) {
        resumed=value;
        if(value) { refresh(); if(video.getVisibility()==VISIBLE && hasWindowFocus()) video.start(); }
        else { if(video.isPlaying()) video.pause(); }
        base.setResumed(value); scene.setResumed(value); overlay.setResumed(value);
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (video != null) { if (focused && resumed && video.getVisibility()==VISIBLE) video.start(); else if(video.isPlaying()) video.pause(); }
    }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if(video!=null) {
            if(visibility==VISIBLE && resumed && hasWindowFocus() && video.getVisibility()==VISIBLE) video.start();
            else if(video.isPlaying()) video.pause();
        }
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) { super.onSizeChanged(w,h,oldw,oldh); if(base!=null && resumed) refresh(); }
    @Override protected void onDetachedFromWindow() {
        setResumed(false); generation++; if(imageRequest!=null) imageRequest.cancel(true);
        mediaKey=""; video.stopPlayback(); super.onDetachedFromWindow();
    }
}
